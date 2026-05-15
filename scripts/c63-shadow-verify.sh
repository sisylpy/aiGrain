#!/usr/bin/env bash
# C-63.1 — 本地三轮 SHADOW 验收：轮换 shadow.* → POST /api/ai/runs → GET .../events
# 要求：JDK/Maven/Spring 与本地库已配置；后端默认 http://localhost:8090 ，context-path=/api
# 禁改 Java/SQL/Test；不接 PRIMARY。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROP_FILE="$REPO_ROOT/src/main/resources/application.properties"
DESKTOP="${HOME}/Desktop"

# BASE 含 servlet context-path，即 server.servlet.context-path=/api → http://localhost:8090/api
BASE_URL="${BASE_URL:-http://localhost:8090/api}"

# SSE 单行拉取超时（秒）；普通 Run 可能较慢，可调大。
C63_EVENTS_MAX_TIME="${C63_EVENTS_MAX_TIME:-240}"

BODY='{"userId":1,"departmentId":1,"distributerId":2,"scopeMode":"GROUP","message":"这个月经营得怎么样？"}'

STAMP="$(date +%Y%m%d_%H%M%S)"
PROP_BACKUP="$REPO_ROOT/scripts/.c63-shadow-verify.application.properties.backup.${STAMP}"

die() {
  printf '%s\n' "$*" >&2
  exit 1
}

[[ -f "$PROP_FILE" ]] || die "找不到 $PROP_FILE（请在仓库根下执行或由 scripts/ 启动）"

mkdir -p "$DESKTOP"

cp "$PROP_FILE" "$PROP_BACKUP"
cleanup() {
  if [[ ! -f "$PROP_BACKUP" ]]; then
    return 0
  fi
  cp "$PROP_BACKUP" "$PROP_FILE" \
    || printf '警告：未能恢复 application.properties，请手动从 %s 还原\n' "$PROP_BACKUP" >&2
}
trap cleanup EXIT INT TERM

printf '已备份: %s\n' "$PROP_BACKUP"

needs_restart_hint() {
  printf '\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  printf '[C-63.1] 已写入 shadow 与会话前置配置。**请重启 Spring Boot**，使 classpath 读到新 properties。\n'
  printf '就绪后在本终端按 Enter 继续拉取 SSE / 存档…\n'
  printf '（若使用 profile 覆盖了 shadow.* ，请以实际生效的配置为准）\n'
  printf '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'
  read -r _
}

perl_set_shadow() {
  export C63_SE="$1" C63_SU="$2" C63_SS="$3"
  perl -0777 -i -pe '
    s/^ai\.composite\.businessDiagnosis\.shadow\.enabled=.*$/ai.composite.businessDiagnosis.shadow.enabled=$ENV{C63_SE}/gm;
    s/^ai\.composite\.businessDiagnosis\.shadow\.userWhitelist=.*$/ai.composite.businessDiagnosis.shadow.userWhitelist=$ENV{C63_SU}/gm;
    s/^ai\.composite\.businessDiagnosis\.shadow\.scopeWhitelist=.*$/ai.composite.businessDiagnosis.shadow.scopeWhitelist=$ENV{C63_SS}/gm;
  ' "$PROP_FILE"
  unset C63_SE C63_SU C63_SS
}

shadow_keys_present() {
  grep -q '^ai\.composite\.businessDiagnosis\.shadow\.enabled=' "$PROP_FILE" \
    && grep -q '^ai\.composite\.businessDiagnosis\.shadow\.userWhitelist=' "$PROP_FILE" \
    && grep -q '^ai\.composite\.businessDiagnosis\.shadow\.scopeWhitelist=' "$PROP_FILE"
}

ensure_shadow_block() {
  if shadow_keys_present; then
    return 0
  fi
  {
    printf '\n# --- C-63.1 shadow-verify: shadow.* block (script will restore on exit) ---\n'
    printf 'ai.composite.businessDiagnosis.shadow.enabled=false\n'
    printf 'ai.composite.businessDiagnosis.shadow.userWhitelist=\n'
    printf 'ai.composite.businessDiagnosis.shadow.distributerWhitelist=\n'
    printf 'ai.composite.businessDiagnosis.shadow.departmentWhitelist=\n'
    printf 'ai.composite.businessDiagnosis.shadow.scopeWhitelist=\n'
    printf 'ai.composite.businessDiagnosis.shadow.maxRunsPerMinute=0\n'
    printf 'ai.composite.businessDiagnosis.shadow.maxRunsPerHour=0\n'
    printf 'ai.composite.businessDiagnosis.shadow.cooldownSeconds=0\n'
  } >> "$PROP_FILE"
}

ensure_composite_shadow_path() {
  # 使普通 Run 能进入 maybeExecuteShadowCompositePlanner（仍须 Gate allowed=true；见设计文档）
  if ! grep -q '^ai\.composite\.businessDiagnosis\.productionEnabled=' "$PROP_FILE"; then
    printf '\n# --- C-63.1: enable Composite feature flag for SHADOW path ---\n' >> "$PROP_FILE"
    printf 'ai.composite.businessDiagnosis.productionEnabled=true\n' >> "$PROP_FILE"
  else
    perl -i -pe 's/^ai\.composite\.businessDiagnosis\.productionEnabled=.*/ai.composite.businessDiagnosis.productionEnabled=true/' "$PROP_FILE"
  fi
  if ! grep -q '^ai\.composite\.businessDiagnosis\.executionMode=' "$PROP_FILE"; then
    printf 'ai.composite.businessDiagnosis.executionMode=SHADOW\n' >> "$PROP_FILE"
  else
    perl -i -pe 's/^ai\.composite\.businessDiagnosis\.executionMode=.*/ai.composite.businessDiagnosis.executionMode=SHADOW/' "$PROP_FILE"
  fi
}

apply_round() {
  cp "$PROP_BACKUP" "$PROP_FILE"
  ensure_shadow_block
  ensure_composite_shadow_path
  perl_set_shadow "$1" "$2" "$3"
  printf '[C-63.1] 已套用配置: shadow.enabled=%s userWhitelist=[%s] scopeWhitelist=[%s]\n' "$1" "$2" "$3"
}

extract_run_id() {
  local json="$1"
  if command -v jq >/dev/null 2>&1; then
    jq -r '.runId // empty' <<<"$json" | head -n1
    return
  fi
  python3 -c 'import sys, json; 
d=json.load(sys.stdin); 
rid=d.get("runId"); 
print(int(rid) if rid is not None else "")' <<<"$json"
}

curl_create() {
  local out="$1"
  local http
  http=$(
    curl -sS -o "$out" -w "%{http_code}" \
      -X POST "$BASE_URL/ai/runs" \
      -H 'Content-Type: application/json' \
      -d "$BODY"
  )
  [[ "$http" == "200" ]] || die "POST $BASE_URL/ai/runs 失败 (HTTP $http)，见 $out"

  RUN_ID="$(extract_run_id "$(cat "$out")")"
  [[ -n "$RUN_ID" ]] || die "无法从响应解析 runId，见 $out"
  printf '%s\n' "$RUN_ID"
}

curl_events() {
  local run="$1"
  local out="$2"
  curl -sS --no-buffer \
    --max-time "$C63_EVENTS_MAX_TIME" \
    -H 'Accept: text/event-stream' \
    "$BASE_URL/ai/runs/${run}/events" \
    -o "$out" || {
    printf '警告：SSE 在未达到 run_finished 时提前结束或非 200（已保存部分到 %s）\n' "$out" >&2
    return 0
  }
}

run_round() {
  local tag="$1" enabled="$2" uwl="$3" swl="$4"
  local create="$DESKTOP/${tag}-create.json"
  local ev="$DESKTOP/${tag}-events.txt"

  apply_round "$enabled" "$uwl" "$swl"
  needs_restart_hint

  RID="$(curl_create "$create")"
  printf '[%s] runId=%s create -> %s\n' "$tag" "$RID" "$create"

  curl_events "$RID" "$ev"
  printf '[%s] events -> %s\n\n' "$tag" "$ev"
}

printf '=== C-63.1 SHADOW verify (BASE_URL=%s) ===\n' "$BASE_URL"

run_round 'c63-shadow-disabled' 'false' '' ''
run_round 'c63-shadow-whitelist-hit' 'true' '1' 'GROUP'
run_round 'c63-shadow-whitelist-miss' 'true' '999999' 'GROUP'

trap - EXIT INT TERM
cleanup
printf '\n已从备份还原 %s\n临时备份（可删）: %s\n' "$PROP_FILE" "$PROP_BACKUP"
