package com.nongxinle.ai.harness;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeComposeResult;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeExecutionMode;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeExecutionResult;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeGateResult;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeReadonlyComposer;
import com.alibaba.fastjson2.JSON;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composite Gate / Execution / Shadow 观测字段摊平（C-55/C-58/C-61）。
 */
final class AiHarnessCompositeSummaryAppender {

    private AiHarnessCompositeSummaryAppender() {
    }

    static void putCompositeHarnessExecutionFieldDefaults(LinkedHashMap<String, Object> out) {
        out.put("compositeExecutionMode", null);
        out.put("compositeExecuted", false);
        out.put("compositeExecutionSuccess", false);
        out.put("compositeFallbackRequired", false);
        out.put("compositeFallbackReason", null);
        out.put("compositePlannerOverallStatus", null);
        out.put("compositePlannerDegradedSteps", null);
        out.put("compositeFinalAnswerText", null);
        out.put("compositeComposerVersion", null);
        out.put("compositeAnswerPlanType", null);
        out.put("compositeExecutionErrorCode", null);
        out.put("compositeExecutionErrorMessage", null);
        out.put("compositeShadowLatencyMs", null);
        out.put("compositeShadowComparedWithLegacy", null);
        out.put("compositeShadowLegacyAnswerPresent", null);
        out.put("compositeShadowCompositeAnswerPresent", null);
        out.put("compositeShadowFinalAnswerReplaced", null);
        out.put("compositeShadowSkipped", null);
        out.put("compositeShadowSkipReason", null);
        out.put("compositeShadowThrottleHit", null);
        out.put("compositeShadowWhitelistMatched", null);
    }

    static void mergeCompositeProductionGateHarnessFields(LinkedHashMap<String, Object> out, AiRunState state) {
        BusinessDiagnosisCompositeGateResult gr =
                state != null ? state.getBusinessDiagnosisCompositeGateResult() : null;
        if (gr == null) {
            out.put("compositeGateAllowed", null);
            out.put("compositeGateReasonCode", null);
            out.put("compositeGateReason", null);
            out.put("compositeGateScopeType", null);
            out.put("compositeGateRecommendedCaseKind", null);
            out.put("compositeGateFinalAnswerPlanType", null);
            out.put("compositeGateDebug", null);
            out.put("compositeGateProductionEnabledSource", null);
            out.put("compositeGateProductionEnabledEffective", null);
            return;
        }
        out.put("compositeGateAllowed", gr.isAllowed());
        out.put("compositeGateReasonCode", gr.getReasonCode() != null ? gr.getReasonCode().name() : null);
        out.put("compositeGateReason", AiHarnessSummaryUtils.blankToNull(gr.getReason()));
        out.put("compositeGateScopeType", AiHarnessSummaryUtils.blankToNull(gr.getScopeType()));
        BusinessDiagnosisCompositeGateResult.RecommendedCaseKind rk = gr.getRecommendedCaseKind();
        out.put("compositeGateRecommendedCaseKind", rk != null ? rk.name() : null);
        out.put("compositeGateFinalAnswerPlanType", AiHarnessSummaryUtils.blankToNull(gr.getFinalAnswerPlanType()));
        Map<String, Object> dbg = gr.getDebug();
        if (dbg == null || dbg.isEmpty()) {
            out.put("compositeGateDebug", null);
            out.put("compositeGateProductionEnabledSource", null);
            out.put("compositeGateProductionEnabledEffective", null);
        } else {
            out.put("compositeGateDebug", JSON.parseObject(JSON.toJSONString(dbg), Map.class));
            Object psrc = dbg.get("productionEnabledSource");
            out.put("compositeGateProductionEnabledSource", psrc != null ? psrc.toString() : null);
            Object peff = dbg.get("productionEnabledEffective");
            out.put(
                    "compositeGateProductionEnabledEffective",
                    peff instanceof Boolean ? peff : null);
        }
    }

    static void mergeCompositeHarnessExecutionFields(LinkedHashMap<String, Object> out, AiRunState state) {
        BusinessDiagnosisCompositeExecutionResult ex =
                state != null ? state.getBusinessDiagnosisCompositeExecutionResult() : null;
        if (ex == null) {
            putCompositeHarnessExecutionFieldDefaults(out);
            return;
        }
        BusinessDiagnosisCompositeExecutionMode m = ex.getMode();
        out.put("compositeExecutionMode", m != null ? m.name() : null);
        out.put("compositeExecuted", ex.isExecuted());
        out.put("compositeExecutionSuccess", ex.isSuccess());
        out.put("compositeFallbackRequired", ex.isFallbackRequired());
        out.put("compositeFallbackReason", AiHarnessSummaryUtils.blankToNull(ex.getFallbackReason()));
        var overall = ex.getPlannerOverallStatus();
        out.put("compositePlannerOverallStatus", overall != null ? overall.name() : null);
        List<String> ds = ex.getDegradedSteps();
        out.put(
                "compositePlannerDegradedSteps",
                ds == null || ds.isEmpty() ? null : new ArrayList<>(ds));
        BusinessDiagnosisCompositeComposeResult cr = ex.getComposeResult();
        String fat = cr != null ? cr.getFinalAnswerText() : null;
        out.put("compositeFinalAnswerText", AiHarnessSummaryUtils.blankToNull(fat));

        String cv = BusinessDiagnosisCompositeReadonlyComposer.COMPOSER_VERSION;
        if (cr != null && cr.getDebug() != null) {
            Object v = cr.getDebug().get("composerVersion");
            if (v != null && org.springframework.util.StringUtils.hasText(v.toString())) {
                cv = v.toString().trim();
            }
        }
        out.put("compositeComposerVersion", cv);

        String apt = null;
        if (cr != null) {
            apt = AiHarnessSummaryUtils.blankToNull(cr.getAnswerPlanType());
        }
        if (apt == null && ex.getBusinessDiagnosisCompositeAnswerPlan() != null) {
            apt = AiHarnessSummaryUtils.blankToNull(ex.getBusinessDiagnosisCompositeAnswerPlan().getType());
        }
        out.put("compositeAnswerPlanType", apt);
        out.put("compositeExecutionErrorCode", AiHarnessSummaryUtils.blankToNull(ex.getErrorCode()));
        out.put("compositeExecutionErrorMessage", AiHarnessSummaryUtils.blankToNull(ex.getErrorMessage()));
        mergeCompositeShadowObservationFields(out, m, ex);
    }

    static void mergeCompositeShadowObservationFields(
            LinkedHashMap<String, Object> out,
            BusinessDiagnosisCompositeExecutionMode mode,
            BusinessDiagnosisCompositeExecutionResult ex) {
        if (mode != BusinessDiagnosisCompositeExecutionMode.SHADOW || ex == null) {
            shadowObservationNullDefaults(out);
            return;
        }
        if (Boolean.TRUE.equals(ex.getCompositeShadowSkipped())) {
            shadowObservationNullLatencyDefaults(out);
            out.put("compositeShadowSkipped", Boolean.TRUE);
            out.put("compositeShadowSkipReason", AiHarnessSummaryUtils.blankToNull(ex.getCompositeShadowSkipReason()));
            out.put(
                    "compositeShadowThrottleHit",
                    ex.getCompositeShadowThrottleHit() != null
                            ? ex.getCompositeShadowThrottleHit()
                            : Boolean.FALSE);
            out.put(
                    "compositeShadowWhitelistMatched", ex.getCompositeShadowWhitelistMatched());
            return;
        }

        shadowObservationSkippedDefaults(out);

        if (!ex.isExecuted()) {
            shadowObservationNullLatencyDefaults(out);
            return;
        }
        out.put("compositeShadowLatencyMs", ex.getCompositeShadowLatencyMs());
        out.put("compositeShadowComparedWithLegacy", ex.getCompositeShadowComparedWithLegacy());
        out.put("compositeShadowLegacyAnswerPresent", ex.getCompositeShadowLegacyAnswerPresent());
        out.put("compositeShadowCompositeAnswerPresent", ex.getCompositeShadowCompositeAnswerPresent());
        Boolean replaced = ex.getCompositeShadowFinalAnswerReplaced();
        out.put("compositeShadowFinalAnswerReplaced", replaced != null ? replaced : Boolean.FALSE);
        out.put("compositeShadowWhitelistMatched", ex.getCompositeShadowWhitelistMatched());
    }

    private static void shadowObservationNullDefaults(LinkedHashMap<String, Object> out) {
        out.put("compositeShadowLatencyMs", null);
        out.put("compositeShadowComparedWithLegacy", null);
        out.put("compositeShadowLegacyAnswerPresent", null);
        out.put("compositeShadowCompositeAnswerPresent", null);
        out.put("compositeShadowFinalAnswerReplaced", null);
        out.put("compositeShadowSkipped", null);
        out.put("compositeShadowSkipReason", null);
        out.put("compositeShadowThrottleHit", null);
        out.put("compositeShadowWhitelistMatched", null);
    }

    private static void shadowObservationSkippedDefaults(LinkedHashMap<String, Object> out) {
        out.put("compositeShadowSkipped", Boolean.FALSE);
        out.put("compositeShadowSkipReason", null);
        out.put("compositeShadowThrottleHit", Boolean.FALSE);
    }

    private static void shadowObservationNullLatencyDefaults(LinkedHashMap<String, Object> out) {
        out.put("compositeShadowLatencyMs", null);
        out.put("compositeShadowComparedWithLegacy", null);
        out.put("compositeShadowLegacyAnswerPresent", null);
        out.put("compositeShadowCompositeAnswerPresent", null);
        out.put("compositeShadowFinalAnswerReplaced", null);
    }
}
