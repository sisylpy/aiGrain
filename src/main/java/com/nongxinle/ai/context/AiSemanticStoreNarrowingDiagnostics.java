package com.nongxinle.ai.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义点名门店收窄（集团 → 单店 / 子集）的 Harness 观测与失败原因；
 * 不含业务 SQL ID 之外的敏感字段，仅用于 ResolvedQueryContext 调试摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSemanticStoreNarrowingDiagnostics {

    public static final String REASON_SKIPPED_STRUCTUREAL_GATE = "SKIPPED_STRUCTUREAL_GATE";
    public static final String REASON_SKIPPED_NOT_GROUP_SCOPE = "SKIPPED_NOT_GROUP_SCOPE";
    public static final String REASON_SKIPPED_SEMANTIC_UNUSABLE = "SKIPPED_SEMANTIC_UNUSABLE";
    public static final String REASON_SKIPPED_EXPLICIT_GROUP_REQUEST = "SKIPPED_EXPLICIT_GROUP_REQUEST";
    public static final String REASON_EMPTY_VISIBLE_STORE_ROWS = "EMPTY_VISIBLE_STORE_ROWS";
    public static final String REASON_MISSING_DISTRIBUTER_FOR_CANDIDATES = "MISSING_DISTRIBUTER_FOR_CANDIDATES";
    public static final String REASON_NO_CANDIDATES_AFTER_VISIBILITY = "NO_CANDIDATES_AFTER_VISIBILITY";
    public static final String REASON_NO_SINGLE_STORE_MENTION = "NO_SINGLE_STORE_MENTION";
    public static final String REASON_NO_LEXICAL_MATCH = "NO_LEXICAL_MATCH";
    public static final String REASON_AMBIGUOUS_LEXICAL_MATCH = "AMBIGUOUS_LEXICAL_MATCH";
    public static final String REASON_SEMANTIC_MENTION_BUT_EMPTY_CANDIDATES = "SEMANTIC_MENTION_BUT_EMPTY_CANDIDATES";
    public static final String REASON_MULTI_STORE_SUBSET_PARTIAL = "MULTI_STORE_SUBSET_PARTIAL";

    @Builder.Default
    private List<String> semanticMentionedStoreNames = new ArrayList<>();
    /** 推导出的经销门店根候选，如 {@code "42:AAA门店"} */
    @Builder.Default
    private List<String> storeRootCandidates = new ArrayList<>();
    /** 本轮可见门店行（常为仅店名预览），如 {@code "AAA"} 或 {@code "AAA#123"} */
    @Builder.Default
    private List<String> visibleStoreCandidates = new ArrayList<>();
    /** 唯一收窄命中时同上格式 */
    private String matchedStoreCandidate;
    /** 与 {@link #matchedStoreCandidate} 对应的语义口述店名（如 AAA） */
    private String matchedSemanticStoreMention;
    /** 未完成收窄或未命中时使用；成功收窄可为 null */
    private String narrowingFailureReason;
    private boolean narrowedSuccessfully;
    private boolean narrowingAttemptedSemanticExplicitStore;
    private boolean ambiguousLexicalMatch;
    @Builder.Default
    private List<String> lexicalAmbiguityStoreSummaries = new ArrayList<>();
    /** 最近一次单店口述店名（澄清话术用），非 DB 名 */
    private String lastSingleSemanticStoreMention;

    public static AiSemanticStoreNarrowingDiagnostics empty() {
        return AiSemanticStoreNarrowingDiagnostics.builder().build();
    }
}
