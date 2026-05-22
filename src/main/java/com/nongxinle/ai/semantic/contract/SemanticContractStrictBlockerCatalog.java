package com.nongxinle.ai.semantic.contract;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict 前置 blocker 只读登记：仅列出<strong>当前仍阻塞</strong>开启 strict enforce 的逻辑。
 * <p>不修改运行时行为；供 Harness debug 与 strict 决策附注。
 */
public final class SemanticContractStrictBlockerCatalog {

    private SemanticContractStrictBlockerCatalog() {
    }

    public static final String STATUS_ACTIVE = "ACTIVE";

    public static final String BLOCKER_MATRIX_CONTRACT_FRAME_CANONICAL =
            "matrix.contract_frame_canonicalize";
    public static final String BLOCKER_SLOT_MERGE_WIRE_RECONCILE = "slot_merge.wire_reconcile";
    public static final String BLOCKER_PROMPT_METRIC_RANKING_TYPE = "prompt.metric_ranking_type_field";
    public static final String BLOCKER_DEBUG_LEGACY_WIRE_FIELDS = "debug.replay_legacy_wire_fields";

    private static final List<StrictBlockerEntry> ENTRIES =
            List.of(
                    entry(
                            BLOCKER_MATRIX_CONTRACT_FRAME_CANONICAL,
                            STATUS_ACTIVE,
                            "PurchaseSemanticCapabilityMatrix.canonicalizePurchaseFollowUp / WarehouseSemanticCapabilityMatrix.canonicalizeWarehouseContractFrame",
                            "合同帧补全 registered wire 下槽位；strict observe 仍允许"),
                    entry(
                            BLOCKER_SLOT_MERGE_WIRE_RECONCILE,
                            STATUS_ACTIVE,
                            "AiQuerySemanticSlotMerge.reconcile*SemanticSlots",
                            "Merge 层按 Matrix 形状覆盖 LLM wire / sourceFacet"),
                    entry(
                            BLOCKER_PROMPT_METRIC_RANKING_TYPE,
                            STATUS_ACTIVE,
                            "query_semantic_parser.v2.md / semantic-output-schema.md",
                            "schema 仍含 metric.rankingType debug 字段；主链 wire 仅 semanticSlots"),
                    entry(
                            BLOCKER_DEBUG_LEGACY_WIRE_FIELDS,
                            STATUS_ACTIVE,
                            "Harness replay / semanticMetricNormalized* debug",
                            "Replay debug 仍输出 metric.rankingType 等观测字段"));

    public static List<StrictBlockerEntry> entries() {
        return ENTRIES;
    }

    public static List<String> activeBlockerIds() {
        return ENTRIES.stream().map(StrictBlockerEntry::id).toList();
    }

    public static Map<String, Object> dump() {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("blockerCount", ENTRIES.size());
        out.put("activeBlockerCount", activeBlockerIds().size());
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
