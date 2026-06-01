package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Run 请求显式 {@code scopeMode=GROUP} 时的 org 收窄边界：请求级 GROUP 优先于多轮 silent 继承，
 * 仅当本句用户原文点名门店或指代「这个店/刚才那个店」等时才允许 GROUP→STORE。
 */
public final class RequestExplicitGroupScopeSupport {

    public static final String EFFECTIVE_SCOPE_SOURCE_REQUEST = "REQUEST_SCOPE";

    private RequestExplicitGroupScopeSupport() {
    }

    public static boolean isExplicitGroupScopeRequest(AiRunCreateRequest request) {
        return request != null
                && StringUtils.hasText(request.getScopeMode())
                && AiConversationScopeMode.fromApiString(request.getScopeMode())
                        == AiConversationScopeMode.GROUP;
    }

    /**
     * Run 请求显式 {@code scopeMode=GROUP} 优先于会话持久化 {@code STORE}，避免 baseline org 先落成单店。
     */
    public static AiConversationScopeMode resolveEffectiveScopeMode(
            AiRunCreateRequest request, AiConversationScopeMode conversationScopeMode) {
        if (isExplicitGroupScopeRequest(request)) {
            return AiConversationScopeMode.GROUP;
        }
        if (conversationScopeMode != null) {
            return conversationScopeMode;
        }
        return AiConversationScopeMode.inferForRun(request, null);
    }

    public static AiResolvedOrgScope pinBaselineGroupOrgScope(
            AiResolvedOrgScopeAssembler assembler,
            AiUserContext userContext,
            Long effectiveDepartmentId,
            AiRunCreateRequest request,
            AiResolvedOrgScope currentMergedOrg) {
        if (assembler == null || userContext == null || request == null) {
            return currentMergedOrg;
        }
        AiResolvedOrgScope baseline =
                assembler.buildBaselineGroupOrgScopeForRequest(userContext, effectiveDepartmentId, request);
        if (baseline != null && AiResolvedOrgScope.SCOPE_GROUP.equals(baseline.getScopeType())) {
            return baseline;
        }
        return currentMergedOrg;
    }

    /**
     * 显式 GROUP 请求下，是否允许将 GROUP 收窄到 STORE（多轮继承或语义点名收窄共用）。
     */
    public static boolean shouldAllowGroupToStoreNarrowing(
            String normalizedUserMessage, AiQuerySemanticParseResult semantic) {
        if (ContractDrivenStoreScopeSupport.allowExplicitGroupNarrowingForContractStoreQuery(semantic)) {
            return true;
        }
        if (semanticDeclaresGroupOverride(semantic)) {
            return false;
        }
        if (semantic != null && !semantic.isParseMissing()) {
            String action = normalizeSemanticAction(semantic.getScopeAction());
            if ("INHERIT_PREVIOUS".equals(action)) {
                if (currentMessageRequestsInheritedStoreScope(normalizedUserMessage)) {
                    return true;
                }
                List<String> mentions = semantic.effectiveMentionedStoreNames();
                return currentMessageLexicallyMentionsAny(normalizedUserMessage, mentions);
            }
        }
        if (currentMessageRequestsInheritedStoreScope(normalizedUserMessage)) {
            return true;
        }
        List<String> mentions = semantic != null ? semantic.effectiveMentionedStoreNames() : List.of();
        if (mentions == null || mentions.isEmpty()) {
            return false;
        }
        return currentMessageLexicallyMentionsAny(normalizedUserMessage, mentions);
    }

    static boolean semanticDeclaresGroupOverride(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.isParseMissing()) {
            return false;
        }
        if (ContractDrivenStoreScopeSupport.contractSingleStoreOverridesGroupScopeDeclaration(sem)) {
            return false;
        }
        String action = normalizeSemanticAction(sem.getScopeAction());
        if (!("OVERRIDE".equals(action) || "NEW".equals(action))) {
            return false;
        }
        List<String> mentions = sem.effectiveMentionedStoreNames();
        return mentions == null || mentions.isEmpty();
    }

    static boolean currentMessageRequestsInheritedStoreScope(String rawMessage) {
        if (!StringUtils.hasText(rawMessage)) {
            return false;
        }
        String compact = rawMessage.replace(" ", "").trim();
        return compact.contains("这个店")
                || compact.contains("刚才那个店")
                || compact.contains("那家店")
                || compact.contains("上述门店")
                || compact.contains("该门店");
    }

    private static boolean currentMessageLexicallyMentionsAny(String normalizedUserMessage, List<String> labels) {
        if (!StringUtils.hasText(normalizedUserMessage) || labels == null || labels.isEmpty()) {
            return false;
        }
        String compact = normalizedUserMessage.replace(" ", "").trim();
        for (String raw : labels) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String label = raw.replace(" ", "").trim();
            if (!label.isEmpty() && compact.contains(label)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeSemanticAction(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
