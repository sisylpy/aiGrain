package com.nongxinle.ai.semantic;

/**
 * 语义解析不可用或需澄清时的澄清策略：不向 Java 分层猜测意图/时间/范围/口径。
 * <p>
 * <b>澄清话术优先级</b>（由 {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} 组装，本类仅提供最末级固定模板）：
 * <ol>
 *   <li>Intake 澄清</li>
 *   <li>V2 {@code needClarification=true} 且非空 {@code clarificationQuestion}（业务澄清主权）</li>
 *   <li>合同 strict 校验澄清（无更具体业务澄清时）</li>
 *   <li>时间合同 / frame validation 的具体 {@code clarificationQuestion}</li>
 *   <li>{@link #FIXED_CLARIFICATION_QUESTION}（本类 {@link #clarificationQuestion()}）</li>
 * </ol>
 */
public final class SemanticParseClarificationPolicy {

    /** 第 3 级固定澄清模板（禁止再散落旧话术）；前几级均由 Resolver 优先选用。 */
    public static final String FIXED_CLARIFICATION_QUESTION =
            "我没有完全理解你的问题。你是想查询经营、营业额、采购、出库，还是菜品毛利？请补充时间和门店范围。";

    private SemanticParseClarificationPolicy() {
    }

    /** 返回第 3 级固定澄清模板；Resolver 仅在无更具体澄清原因时使用。 */
    public static String clarificationQuestion() {
        return FIXED_CLARIFICATION_QUESTION;
    }

    /**
     * 解析缺失、置信度不达标、模型显式索要澄清时为 true；
     * 业务 path 是否在合法集合由调用方另行判断。
     */
    public static boolean needSemanticParseClarification(AiQuerySemanticParseResult sem, double minConfidence) {
        if (sem == null) {
            return true;
        }
        if (sem.isParseMissing()) {
            return true;
        }
        if (!sem.isStructuralConfidenceOk(minConfidence)) {
            return true;
        }
        return Boolean.TRUE.equals(sem.getNeedClarification());
    }
}
