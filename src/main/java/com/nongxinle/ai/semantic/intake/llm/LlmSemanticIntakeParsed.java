package com.nongxinle.ai.semantic.intake.llm;

import lombok.Builder;
import lombok.Value;

import com.nongxinle.ai.semantic.intake.SemanticIntakeSubQuestion;
import java.util.List;

/**
 * LLM Semantic Intake 单行 JSON 解析结果（schema v1）。
 * <p>{@link #reason} 当前兼作 Harness 观测与过渡 structured marker（如 {@code _to_cost_ranking}）；
 * schema v2 将引入 {@code followUpIntent} 等字段，reason 仅保留 debug 文本。
 * 见 {@code docs/ai/semantic-intake-schema-evolution.md}。
 */
@Value
@Builder
public class LlmSemanticIntakeParsed {

    boolean parseFailed;
    String parseError;
    String rawDigest;

    String questionMode;
    String normalizationType;
    String canonicalUserQuery;
    boolean isFollowUp;
    boolean usedPreviousContext;
    String primaryDomain;
    List<String> candidateDomains;
    String routeType;
    Double confidence;
    boolean needClarification;
    String clarificationQuestion;
    String reason;
    String warehouseInventorySemantics;
    List<SemanticIntakeSubQuestion> subQuestions;
}
