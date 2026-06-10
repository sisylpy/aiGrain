package com.nongxinle.ai.identity;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.PurchaseGoodsBusinessAnalysisSupport;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** PR1：Identity 执行链与 Harness replay 可见 debug 的 SSOT（不写 DisplayName / AnswerPlan）。 */
public final class BusinessEntityIdentityHarnessDebugSupport {

    public static final String KEY_RESOLVER_INVOKED = "goodsIdentityResolverInvoked";
    public static final String KEY_CONTRACT_LOCKED = "goodsIdentityContractLocked";
    public static final String KEY_DIS_ID_HINT = "goodsIdentityDisIdHint";
    public static final String KEY_TOOL_ARGS_DIS_ID = "goodsIdentityToolArgsDisId";
    public static final String KEY_EFFECTIVE_MENTION = "goodsIdentityEffectiveMentionedGoodsName";
    public static final String KEY_TOOL_ARGS_FOCUS_ID = "goodsIdentityToolArgsFocusDisGoodsId";
    public static final String KEY_TOOL_ARGS_FOCUS_NAME = "goodsIdentityToolArgsFocusGoodsName";

    private BusinessEntityIdentityHarnessDebugSupport() {}

    public static void appendPreResolveExecutionTrace(
            Map<String, Object> debug,
            AiResolvedQueryContext rq,
            Map<String, Object> toolArgs,
            Integer distributerIdHint) {
        if (debug == null) {
            return;
        }
        debug.put(KEY_RESOLVER_INVOKED, Boolean.TRUE);
        AiQuerySemanticParseResult sem = rq == null ? null : rq.getQuerySemanticParse();
        debug.put(KEY_CONTRACT_LOCKED, SemanticContractCompletionEngine.isContractLockedParse(sem));
        debug.put(KEY_DIS_ID_HINT, distributerIdHint);
        debug.put(KEY_TOOL_ARGS_DIS_ID, BusinessEntityIdentityScopeSupport.disIdFromToolArgs(toolArgs));
        if (sem != null && StringUtils.hasText(sem.effectiveMentionedGoodsName())) {
            debug.put(KEY_EFFECTIVE_MENTION, sem.effectiveMentionedGoodsName().trim());
        }
        if (toolArgs != null) {
            Object focusId = toolArgs.get(AiBusinessToolIds.ARG_PURCHASE_FOCUS_DIS_GOODS_ID);
            if (focusId != null) {
                debug.put(KEY_TOOL_ARGS_FOCUS_ID, focusId);
            }
            Object focusName = toolArgs.get(AiBusinessToolIds.ARG_PURCHASE_FOCUS_GOODS_NAME);
            if (focusName != null && StringUtils.hasText(focusName.toString())) {
                debug.put(KEY_TOOL_ARGS_FOCUS_NAME, focusName.toString().trim());
            }
        }
    }

    /** 从 {@link AiRunState#getToolResults()} 读取 purchase_goods_business_analysis 的 data.debug。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractPurchaseGoodsBusinessAnalysisToolDebug(AiRunState state) {
        if (state == null || state.getToolResults() == null) {
            return null;
        }
        Object raw = state.getToolResults().get(AiBusinessToolIds.PURCHASE_GOODS_BUSINESS_ANALYSIS);
        if (!(raw instanceof Map<?, ?> envelope)) {
            return null;
        }
        Object data = envelope.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return null;
        }
        Object dbg = dataMap.get("debug");
        if (!(dbg instanceof Map<?, ?> dm) || dm.isEmpty()) {
            return null;
        }
        return (Map<String, Object>) dm;
    }

    private static final String[] FLAT_HARNESS_IDENTITY_DEBUG_KEYS = {
            "identityLookupDisId",
            "identityLookupHint",
            "identityLookupSearchParam",
            "identityLookupHitCount",
            "identityLookupFailureReason",
            "resolvedDisGoodsId",
            "userMentionedGoodsName",
            "resolvedCanonicalGoodsName",
            "entityIdentityResolutionStatus",
            "entityIdentityResolutionSource",
            KEY_RESOLVER_INVOKED,
            KEY_CONTRACT_LOCKED,
            KEY_DIS_ID_HINT,
            KEY_TOOL_ARGS_DIS_ID,
            KEY_EFFECTIVE_MENTION,
            KEY_TOOL_ARGS_FOCUS_ID,
            KEY_TOOL_ARGS_FOCUS_NAME,
            "failureReason",
    };

    public static String[] flatHarnessIdentityDebugKeys() {
        return FLAT_HARNESS_IDENTITY_DEBUG_KEYS.clone();
    }

    /** 将 Tool debug 摊平到 Harness replay 顶层摘要（用户可见字段）。 */
    public static void mirrorIdentityDebugToHarnessSummary(
            LinkedHashMap<String, Object> out, AiRunState state) {
        if (out == null) {
            return;
        }
        ensureIdentityDebugKeySlots(out);
        Map<String, Object> toolDebug = extractPurchaseGoodsBusinessAnalysisToolDebug(state);
        if (toolDebug != null && !toolDebug.isEmpty()) {
            overlayIdentityDebug(out, toolDebug);
            out.put("purchaseGoodsBusinessAnalysisIdentityDebug", new LinkedHashMap<>(toolDebug));
        }
        Map<String, Object> md = state != null ? state.getMasterBusinessAgentDebug() : null;
        if (md != null && !md.isEmpty()) {
            overlayIdentityDebugAbsentOnly(out, md);
            if (out.get("purchaseGoodsBusinessAnalysisIdentityDebug") == null
                    && hasAnyIdentityDebugSignal(md)) {
                out.put("purchaseGoodsBusinessAnalysisIdentityDebug", copyIdentityDebugSlice(md));
            }
        }
        Object coreFailure = extractPayloadFailureReason(state);
        if (coreFailure != null) {
            out.put("purchaseGoodsBusinessAnalysisFailureReason", coreFailure);
        }
    }

    public static void putHarnessIdentityDebugDefaults(LinkedHashMap<String, Object> out) {
        if (out == null) {
            return;
        }
        out.put("identityLookupDisId", null);
        out.put("identityLookupHint", null);
        out.put("identityLookupSearchParam", null);
        out.put("identityLookupHitCount", null);
        out.put("identityLookupFailureReason", null);
        out.put("resolvedDisGoodsId", null);
        out.put("userMentionedGoodsName", null);
        out.put("resolvedCanonicalGoodsName", null);
        out.put("entityIdentityResolutionStatus", null);
        out.put("entityIdentityResolutionSource", null);
        out.put(KEY_RESOLVER_INVOKED, null);
        out.put(KEY_CONTRACT_LOCKED, null);
        out.put(KEY_DIS_ID_HINT, null);
        out.put(KEY_TOOL_ARGS_DIS_ID, null);
        out.put(KEY_EFFECTIVE_MENTION, null);
        out.put(KEY_TOOL_ARGS_FOCUS_ID, null);
        out.put(KEY_TOOL_ARGS_FOCUS_NAME, null);
        out.put("purchaseGoodsBusinessAnalysisIdentityDebug", null);
        out.put("purchaseGoodsBusinessAnalysisFailureReason", null);
    }

    @SuppressWarnings("unchecked")
    private static Object extractPayloadFailureReason(AiRunState state) {
        Map<String, Object> toolDebug = extractPurchaseGoodsBusinessAnalysisToolDebug(state);
        if (toolDebug != null && toolDebug.get("failureReason") != null) {
            return toolDebug.get("failureReason");
        }
        if (state == null || state.getToolResults() == null) {
            return null;
        }
        Object raw = state.getToolResults().get(AiBusinessToolIds.PURCHASE_GOODS_BUSINESS_ANALYSIS);
        if (!(raw instanceof Map<?, ?> envelope)) {
            return null;
        }
        Object data = envelope.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return null;
        }
        Object core = dataMap.get(PurchaseGoodsBusinessAnalysisSupport.PAYLOAD_KEY);
        if (core instanceof Map<?, ?> cm) {
            return cm.get("failureReason");
        }
        return null;
    }

    private static void ensureIdentityDebugKeySlots(LinkedHashMap<String, Object> out) {
        for (String key : FLAT_HARNESS_IDENTITY_DEBUG_KEYS) {
            if (!out.containsKey(key)) {
                out.put(key, null);
            }
        }
        if (!out.containsKey("purchaseGoodsBusinessAnalysisIdentityDebug")) {
            out.put("purchaseGoodsBusinessAnalysisIdentityDebug", null);
        }
        if (!out.containsKey("purchaseGoodsBusinessAnalysisFailureReason")) {
            out.put("purchaseGoodsBusinessAnalysisFailureReason", null);
        }
    }

    private static void overlayIdentityDebug(LinkedHashMap<String, Object> out, Map<String, Object> source) {
        for (String key : FLAT_HARNESS_IDENTITY_DEBUG_KEYS) {
            if (source.containsKey(key)) {
                out.put(key, source.get(key));
            }
        }
    }

    private static void overlayIdentityDebugAbsentOnly(LinkedHashMap<String, Object> out, Map<String, Object> source) {
        for (String key : FLAT_HARNESS_IDENTITY_DEBUG_KEYS) {
            if (out.get(key) == null && source.containsKey(key)) {
                out.put(key, source.get(key));
            }
        }
    }

    private static boolean hasAnyIdentityDebugSignal(Map<String, Object> source) {
        for (String key : FLAT_HARNESS_IDENTITY_DEBUG_KEYS) {
            if (source.get(key) != null) {
                return true;
            }
        }
        return false;
    }

    private static LinkedHashMap<String, Object> copyIdentityDebugSlice(Map<String, Object> source) {
        LinkedHashMap<String, Object> slice = new LinkedHashMap<>();
        for (String key : FLAT_HARNESS_IDENTITY_DEBUG_KEYS) {
            if (source.containsKey(key)) {
                slice.put(key, source.get(key));
            }
        }
        return slice;
    }
}
