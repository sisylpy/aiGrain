package com.nongxinle.ai.semantic;

/**
 * LLM QuerySemanticParser 不可用或不可靠时的统一兜底：不向 Java 分层猜测意图/时间/范围/口径。
 */
public final class SemanticParseFallbackPolicy {

    /**
     * 固定澄清模板（禁止使用分散 legacy 话术）；不沿用模型自由生成的 clarificationQuestion。
     */
    public static final String FIXED_CLARIFICATION_QUESTION =
            "我没有完全理解你的问题。你是想查询经营、营业额、采购、出库，还是菜品毛利？请补充时间和门店范围。";

    private SemanticParseFallbackPolicy() {
    }

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
