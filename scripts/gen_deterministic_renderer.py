#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
stub_path = root / "src/main/java/com/nongxinle/ai/graph/business/StubAnswerComposerNode.java"
out_path = root / "src/main/java/com/nongxinle/ai/composer/renderer/DeterministicAnswerRenderer.java"
lines = stub_path.read_text(encoding="utf-8").splitlines(keepends=True)


def ln(a, b):
    return "".join(lines[a - 1 : b])


def rename_extract(s: str) -> str:
    s = s.replace(
        "private static Map<String, Object> extractPurchaseOverviewPayload(AiRunState state)",
        "private static Map<String, Object> extractPurchaseOverviewPayloadForRenderer(AiRunState state)",
    )
    s = s.replace(
        "private static Map<String, Object> extractWarehouseOverviewPayload(AiRunState state)",
        "private static Map<String, Object> extractWarehouseOverviewPayloadForRenderer(AiRunState state)",
    )
    return s


header = '''package com.nongxinle.ai.composer.renderer;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiBusinessOverviewResult;
import com.nongxinle.ai.dto.business.AiDishProfitDishBrief;
import com.nongxinle.ai.dto.business.AiDishProfitOverviewResult;
import com.nongxinle.ai.dto.business.BusinessDiagnosisPlan;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.cost.AiCostDiagnosisResult;
import com.nongxinle.ai.composer.summary.BusinessOverviewDeterministicSummaryBuilder;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiNumericPlainText;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic prose for Answer Composer LLM fallbacks: reads AnswerPlans, tool payloads,
 * {@link AiRunState#getResolvedQueryContext()} structured signals, diagnosis plans.<br>
 * Does not call the LLM. Copy-frozen wording from composer node.
 */
@Component
public final class DeterministicAnswerRenderer {

'''

prefer_headline = '''    private static String extractOverviewNumericHeadlinePreferAnswerPlan(AiRunState state,
            AiBusinessOverviewResult o) {
        if (BusinessOverviewDeterministicSummaryBuilder.hasAuthoritativeBusinessOverviewRevenuePlan(state)) {
            return nz(BusinessOverviewDeterministicSummaryBuilder.businessOverviewResolvedRevenueParagraph(state)).trim();
        }
        return BusinessOverviewDeterministicSummaryBuilder.extractOverviewNumericHeadline(state, o);
    }

'''

tail = '''

    public String renderCostFallback(AiCostDiagnosisResult d) {
        return shortFallbackCost(d);
    }

    public String renderHarnessDiagnosisPlan(DiagnosisPlan plan) {
        return shortDeterministicHarnessDiagnosisPlan(plan);
    }

    public String renderStorePriorityRanking(BusinessDiagnosisPlan plan) {
        return shortFallbackStorePriorityRanking(plan);
    }

    public String renderBusinessDiagnosisFallback(AiRunState state, BusinessDiagnosisPlan plan) {
        return shortFallbackBusinessDiagnosis(state, plan);
    }

    public String renderDishProfitFallback(AiDishProfitOverviewResult r, AiRunState state) {
        return shortFallbackDishProfit(r, state);
    }

    public String renderBusinessOverviewFallback(AiRunState state, AiBusinessOverviewResult o) {
        return shortFallbackBusiness(state, o);
    }

    public String renderPurchaseCostFallback(AiRunState state) {
        return purchaseCostFallback(state);
    }

    public String renderWarehouseStockFallback(AiRunState state) {
        return warehouseStockFallback(state);
    }

    public String renderRevenueEnvelopeFallback(AiRunState state) {
        return revenueOverviewDeterministicFallback(state);
    }

    public String renderStockReduceToolFallback(AiRunState state) {
        return stockReduceQueryDeterministicFallback(state);
    }

    public String genericEmptyLlmFallback() {
        return GENERIC_CHAT_EMPTY_LLM_FALLBACK;
    }
}
'''

extractors = rename_extract(ln(2764, 2789))

purch = ln(1734, 2421).replace(
    "extractPurchaseOverviewPayload(state)",
    "extractPurchaseOverviewPayloadForRenderer(state)",
)
wh_block = ln(2561, 2761).replace(
    "extractWarehouseOverviewPayload(state)",
    "extractWarehouseOverviewPayloadForRenderer(state)",
).replace(
    "stockSnapshotHasSignal(sq, stk, extractWarehouseOverviewPayload(state))",
    "stockSnapshotHasSignal(sq, stk, extractWarehouseOverviewPayloadForRenderer(state))",
)

parts = []
# Core constants first; numeric helpers before fmtStockWeight (uses plainNumericHint).
parts.append(ln(61, 72))
parts.append(ln(1403, 1423))
parts.append(ln(2820, 2846))
parts.append(ln(3738, 3754))  # toolEnvelope + toolDataInnerMap
parts.append(extractors)
parts.append(ln(2791, 2818))
parts.append(ln(73, 92))  # fmtStock helpers + GENERIC_CHAT
parts.append(ln(3246, 3301))
parts.append(ln(398, 1161))
parts.append(prefer_headline)
parts.append(ln(1323, 1401))
parts.append(ln(1425, 1430))  # isBusinessOverviewToPurchaseConvergence
parts.append(purch)
parts.append(wh_block)
parts.append(ln(3648, 3735))

out_path.parent.mkdir(parents=True, exist_ok=True)
out_path.write_text(header + "".join(parts) + tail, encoding="utf-8")
