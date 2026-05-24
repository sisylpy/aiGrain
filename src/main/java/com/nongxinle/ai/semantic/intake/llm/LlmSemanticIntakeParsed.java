package com.nongxinle.ai.semantic.intake.llm;

import lombok.Builder;
import lombok.Value;

import com.nongxinle.ai.semantic.intake.SemanticIntakeSubQuestion;
import java.util.List;

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
    List<SemanticIntakeSubQuestion> subQuestions;
}
