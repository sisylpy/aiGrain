package com.nongxinle.ai.identity;

/**
 * 实体 identity 解析来源（provenance）。历史 anchor 不得标记为当前轮 structured ID。
 */
public enum EntityIdentityResolutionSource {
    /** 当前轮 V2 / LockedFrame 输出的结构化实体 ID（非 rewrite / previous）。 */
    CURRENT_V2_STRUCTURED_ID,
    /** 当前轮显式实体名称经 DB lookup 落地。 */
    CURRENT_EXPLICIT_NAME_DB,
    /** @deprecated 使用 {@link #CURRENT_V2_STRUCTURED_ID} 或 {@link #CURRENT_EXPLICIT_NAME_DB} */
    @Deprecated
    CURRENT_MENTION_DB,
    /** @deprecated 使用 {@link #CURRENT_V2_STRUCTURED_ID} */
    @Deprecated
    CURRENT_STRUCTURED_ID,
    /** 上一轮 {@code resultAnchor} 继承（仅 {@code USE_PREVIOUS_ANCHOR} 且无当前显式实体）。 */
    PREVIOUS_RESULT_ANCHOR_ID,
    /** @deprecated 使用 {@link #PREVIOUS_RESULT_ANCHOR_ID} */
    @Deprecated
    INHERITED_PREVIOUS_ANCHOR,
    /** Intake rewrite 携带的历史 resultAnchor ID。 */
    REWRITE_RESULT_ANCHOR_ID,
    /** Intake rewrite 携带的历史 resultAnchor 名称（无 ID 或名称路径）。 */
    REWRITE_INHERITED_ANCHOR,
    /** 当前显式名称与候选 ID 的 canonical 不一致（fail-closed）。 */
    CURRENT_NAME_ID_CONFLICT,
    UNRESOLVED,
    SKIPPED;

    public boolean isCurrentTurnExplicitSource() {
        return this == CURRENT_V2_STRUCTURED_ID
                || this == CURRENT_EXPLICIT_NAME_DB
                || this == CURRENT_MENTION_DB
                || this == CURRENT_STRUCTURED_ID;
    }

    public boolean isHistoricalAnchorSource() {
        return this == PREVIOUS_RESULT_ANCHOR_ID
                || this == INHERITED_PREVIOUS_ANCHOR
                || this == REWRITE_RESULT_ANCHOR_ID
                || this == REWRITE_INHERITED_ANCHOR;
    }
}
