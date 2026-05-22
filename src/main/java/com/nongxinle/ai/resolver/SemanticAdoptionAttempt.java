package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.SemanticTimeContractCheck;
import com.nongxinle.ai.semantic.contract.SemanticContractViolationCode;

/**
 * {@link SemanticAdoptionPipeline#tryAdopt} 结果：成功收养、或时间/路径失败待澄清。
 */
record SemanticAdoptionAttempt(
        AiQuerySemanticParseResult semantic,
        AiResolvedQueryIntent mergedIntent,
        AiResolvedTimeWindow tentativeTime,
        SemanticTimeContractCheck.Result timeContract,
        String rejectionReason,
        String semanticClarificationQuestion,
        SemanticContractViolationCode contractViolationCode) {

    SemanticAdoptionAttempt(
            AiQuerySemanticParseResult semantic,
            AiResolvedQueryIntent mergedIntent,
            AiResolvedTimeWindow tentativeTime,
            SemanticTimeContractCheck.Result timeContract,
            String rejectionReason,
            String semanticClarificationQuestion) {
        this(
                semantic,
                mergedIntent,
                tentativeTime,
                timeContract,
                rejectionReason,
                semanticClarificationQuestion,
                null);
    }

    SemanticAdoptionAttempt(
            AiQuerySemanticParseResult semantic,
            AiResolvedQueryIntent mergedIntent,
            AiResolvedTimeWindow tentativeTime,
            SemanticTimeContractCheck.Result timeContract,
            String rejectionReason) {
        this(semantic, mergedIntent, tentativeTime, timeContract, rejectionReason, null, null);
    }

    boolean adopted() {
        return semantic != null && mergedIntent != null && timeContract != null && timeContract.valid();
    }

    /** {@link com.nongxinle.ai.semantic.frame.CurrentSemanticFrameValidator} 等业务 frame 校验失败时的具体澄清句。 */
    boolean frameClarificationRequired() {
        return semanticClarificationQuestion != null && !semanticClarificationQuestion.isBlank();
    }

    boolean contractSelectionClarificationRequired() {
        return contractViolationCode != null;
    }
}
