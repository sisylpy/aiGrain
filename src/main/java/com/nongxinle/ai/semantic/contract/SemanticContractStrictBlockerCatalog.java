package com.nongxinle.ai.semantic.contract;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict 前置 blocker 只读登记：区分仍阻塞 strict enforce 的 {@link #STATUS_ACTIVE} 项，
 * 与已降级为 debug / known-debt 的观测项（P2A：contract entry 通过后不再作为主链阻断理由）。
 * <p>不修改 Matrix / SlotMerge 运行时行为；供 Harness debug 与 strict 决策附注。
 */
public final class SemanticContractStrictBlockerCatalog {

    private SemanticContractStrictBlockerCatalog() {
    }

    /** 仍阻塞全域 strict enforce（当前应为空集，待 P4 清理完成后方可为空 catalog）。 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 仅 debug / replay 观测；不参与 enforce。 */
    public static final String STATUS_DEBUG_ONLY = "DEBUG_ONLY";
    /** 已知技术债；保留观测，contract entry 通过后不阻断主链。 */
    public static final String STATUS_KNOWN_DEBT = "KNOWN_DEBT";

    public static final String BLOCKER_MATRIX_CONTRACT_FRAME_CANONICAL =
            "matrix.contract_frame_canonicalize";
    public static final String BLOCKER_SLOT_MERGE_WIRE_RECONCILE = "slot_merge.wire_reconcile";
    public static final String BLOCKER_PROMPT_METRIC_RANKING_TYPE = "prompt.metric_ranking_type_field";
    public static final String BLOCKER_DEBUG_REPLAY_WIRE_FIELDS = "debug.replay_legacy_wire_fields";

    private static final List<StrictBlockerEntry> ENTRIES =
            List.of(
                    entry(
                            BLOCKER_MATRIX_CONTRACT_FRAME_CANONICAL,
                            STATUS_KNOWN_DEBT,
                            "PurchaseSemanticCapabilityMatrix.canonicalizePurchaseFollowUp / WarehouseSemanticCapabilityMatrix.canonicalizeWarehouseContractFrame",
                            "合同帧补全 registered wire 下槽位；P2A 降级为 known-debt 观测，contract entry 通过后不 enforce"),
                    entry(
                            BLOCKER_SLOT_MERGE_WIRE_RECONCILE,
                            STATUS_KNOWN_DEBT,
                            "AiQuerySemanticSlotMerge.reconcile*SemanticSlots",
                            "Merge 层按 Matrix 形状覆盖 LLM wire / sourceFacet；P2A 降级为 known-debt 观测"),
                    entry(
                            BLOCKER_PROMPT_METRIC_RANKING_TYPE,
                            STATUS_DEBUG_ONLY,
                            "query_semantic_parser.v2.md / semantic-output-schema.md",
                            "schema 仍含 metric.rankingType debug 字段；主链 wire 仅 semanticSlots"),
                    entry(
                            BLOCKER_DEBUG_REPLAY_WIRE_FIELDS,
                            STATUS_DEBUG_ONLY,
                            "Harness replay / semanticMetricNormalized* debug",
                            "Replay debug 仍输出 metric.rankingType 等观测字段"));

    public static List<StrictBlockerEntry> entries() {
        return ENTRIES;
    }

    /** 仍阻塞 strict enforce 的 blocker id（{@link #STATUS_ACTIVE} only）。 */
    public static List<String> activeBlockerIds() {
        return ENTRIES.stream()
                .filter(e -> STATUS_ACTIVE.equals(e.status()))
                .map(StrictBlockerEntry::id)
                .toList();
    }

    /** 已降级、仅 debug / known-debt 观测的 blocker id；不触发 clarification。 */
    public static List<String> deprecatedBlockerIds() {
        return ENTRIES.stream()
                .filter(e -> !STATUS_ACTIVE.equals(e.status()))
                .map(StrictBlockerEntry::id)
                .toList();
    }

    public static Map<String, Object> dump() {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("blockerCount", ENTRIES.size());
        out.put("activeBlockerCount", activeBlockerIds().size());
        out.put("deprecatedBlockerCount", deprecatedBlockerIds().size());
        out.put(
                "entries",
                ENTRIES.stream()
                        .map(
                                e -> {
                                    LinkedHashMap<String, Object> m = new LinkedHashMap<>();
                                    m.put("id", e.id());
                                    m.put("status", e.status());
                                    m.put("location", e.location());
                                    m.put("description", e.description());
                                    m.put("enforceBlocker", STATUS_ACTIVE.equals(e.status()));
                                    return m;
                                })
                        .toList());
        return out;
    }

    private static StrictBlockerEntry entry(String id, String status, String location, String description) {
        return new StrictBlockerEntry(id, status, location, description);
    }

    public record StrictBlockerEntry(String id, String status, String location, String description) {}
}
