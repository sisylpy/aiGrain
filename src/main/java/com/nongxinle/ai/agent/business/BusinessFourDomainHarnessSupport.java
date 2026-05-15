package com.nongxinle.ai.agent.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import org.springframework.util.StringUtils;

/**
 * 四域 Harness（经营概览 / 经营诊断复用）：Master 在每步子 Agent 调用前写入
 * surface / purpose / {@code harnessTarget*}，子 Agent {@code supports} 只看 target 专线，不误判原始
 * {@code business_diagnosis_path} 与各域 {@code revenue_overview_path} 的差异。
 */
public final class BusinessFourDomainHarnessSupport {

    private BusinessFourDomainHarnessSupport() {
    }

    public static void populateHarnessContract(
            BusinessAgentRequest.BusinessAgentRequestBuilder builder, AiRunState state, AiResolvedQueryContext rq) {
        if (builder == null) {
            return;
        }
        boolean diagnosisSurface = state != null && state.isBusinessDiagnosisPath();
        boolean overviewSurface = state != null && state.isBusinessOverviewPath();

        String surfaceIntent;
        String surfacePath;
        String purposeIntent;
        if (diagnosisSurface) {
            surfaceIntent = AiResolvedQueryIntent.BUSINESS_DIAGNOSIS;
            surfacePath = AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS;
            purposeIntent = AiResolvedQueryIntent.BUSINESS_DIAGNOSIS;
        } else if (overviewSurface) {
            surfaceIntent = AiResolvedQueryIntent.BUSINESS_OVERVIEW;
            surfacePath = AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW;
            purposeIntent = AiResolvedQueryIntent.BUSINESS_OVERVIEW;
        } else {
            surfaceIntent = null;
            surfacePath = null;
            purposeIntent = null;
        }

        builder.orchestratedSurfaceIntentCode(surfaceIntent)
                .orchestratedSurfacePathCode(surfacePath)
                .orchestratedPurposeIntentCode(purposeIntent);
        if (rq != null) {
            builder.orchestratedOriginalIntentCode(rq.getEffectiveIntentCode())
                    .orchestratedOriginalPathCode(rq.getEffectivePathCode());
        }
    }

    /**
     * 当前步是否合法的Harness 调用：surface 为概览/诊断二路之一；purpose 与 surface 同属 Harness 契约。
     */
    public static boolean harnessSurfaceAndPurposeOk(BusinessAgentRequest request) {
        if (request == null || !request.isOrchestratedBusinessOverviewMultiAgent()) {
            return false;
        }
        String surfacePath = request.getOrchestratedSurfacePathCode();
        boolean surfaceOk =
                AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(surfacePath)
                        || AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(surfacePath);
        if (!surfaceOk) {
            return false;
        }
        String purpose = request.getOrchestratedPurposeIntentCode();
        return AiResolvedQueryIntent.BUSINESS_OVERVIEW.equals(purpose)
                || AiResolvedQueryIntent.BUSINESS_DIAGNOSIS.equals(purpose);
    }

    /** 本子 Agent 步的 target 专线是否与给定 intent/path 一致（均已规范非空比对）。 */
    public static boolean harnessTargetMatchesDomain(
            BusinessAgentRequest request, String domainIntentCode, String domainPathCode) {
        if (!harnessSurfaceAndPurposeOk(request)) {
            return false;
        }
        if (!StringUtils.hasText(request.getHarnessTargetDomainPathCode())
                || !StringUtils.hasText(request.getHarnessTargetDomainIntentCode())) {
            return false;
        }
        return domainPathCode.equals(request.getHarnessTargetDomainPathCode())
                && domainIntentCode.equals(request.getHarnessTargetDomainIntentCode());
    }

    /** Master 在 {@code supports}/{@code execute} 前对每个子 Agent 调用一次。 */
    public static void applyHarnessStepTarget(BusinessAgentRequest request, String agentBeanName) {
        if (request == null || agentBeanName == null) {
            return;
        }
        switch (agentBeanName) {
            case BusinessAgentNames.REVENUE_OVERVIEW -> {
                request.setHarnessTargetDomainIntentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW);
                request.setHarnessTargetDomainPathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
            }
            case BusinessAgentNames.PURCHASE_OVERVIEW -> {
                request.setHarnessTargetDomainIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
                request.setHarnessTargetDomainPathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
            }
            case BusinessAgentNames.STOCK_REDUCE_QUERY -> {
                request.setHarnessTargetDomainIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
                request.setHarnessTargetDomainPathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
            }
            case BusinessAgentNames.DISH_PROFIT_ANALYSIS -> {
                request.setHarnessTargetDomainIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
                request.setHarnessTargetDomainPathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
            }
            default -> {
                request.setHarnessTargetDomainIntentCode(null);
                request.setHarnessTargetDomainPathCode(null);
            }
        }
    }
}
