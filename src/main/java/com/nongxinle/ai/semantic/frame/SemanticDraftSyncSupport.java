package com.nongxinle.ai.semantic.frame;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import org.springframework.util.StringUtils;

/**
 * 将 flat parse 字段同步回 {@link SchemaValidatedSemanticDraft}，供 inheritance / applier 在
 * Completion 前保持 Draft 为唯一可变语义载体。
 */
public final class SemanticDraftSyncSupport {

    private SemanticDraftSyncSupport() {}

    public static AiQuerySemanticParseResult syncDraftFromParse(AiQuerySemanticParseResult parse) {
        if (parse == null) {
            return null;
        }
        SchemaValidatedSemanticDraft existing = parse.getSemanticDraft();
        AiQuerySemanticParseResult.SemanticSlotsPart slots = parse.getSemanticSlots();

        SchemaValidatedSemanticDraft draft =
                SchemaValidatedSemanticDraft.builder()
                        .contractFields(
                                SchemaValidatedSemanticDraft.ContractFields.builder()
                                        .selectedContractId(
                                                slots != null ? trim(slots.getSelectedContractId()) : null)
                                        .llmStructuredIntentDetailWire(
                                                slots != null
                                                        ? trim(slots.getStructuredIntentDetailWire())
                                                        : null)
                                        .llmAnswerPlanType(
                                                slots != null ? trim(slots.getAnswerPlanType()) : null)
                                        .llmSelectedTools(
                                                parse.getOrchestrationDecisionCandidate() != null
                                                        ? parse.getOrchestrationDecisionCandidate()
                                                                .getSelectedTools()
                                                        : null)
                                        .build())
                        .businessSlots(
                                SchemaValidatedSemanticDraft.BusinessSlots.builder()
                                        .semanticSlots(slots)
                                        .metric(parse.getMetric())
                                        .build())
                        .timeSlots(
                                SchemaValidatedSemanticDraft.TimeSlots.builder()
                                        .time(parse.getTime())
                                        .build())
                        .scopeSlots(
                                SchemaValidatedSemanticDraft.ScopeSlots.builder()
                                        .requestedScope(parse.getRequestedScope())
                                        .build())
                        .entitySlots(
                                SchemaValidatedSemanticDraft.EntitySlots.builder()
                                        .mentionedDishName(
                                                firstNonBlank(
                                                        parse.getMentionedDishName(),
                                                        slots != null
                                                                ? slots.getMentionedDishName()
                                                                : null))
                                        .mentionedGoodsName(
                                                firstNonBlank(
                                                        parse.getMentionedGoodsName(),
                                                        slots != null
                                                                ? slots.getMentionedGoodsName()
                                                                : null))
                                        .build())
                        .domainExtensions(
                                existing != null ? existing.getDomainExtensions() : null)
                        .presence(existing != null ? existing.getPresence() : null)
                        .protocolErrors(existing != null ? existing.getProtocolErrors() : null)
                        .build();
        return parse.toBuilder().semanticDraft(draft).build();
    }

    /**
     * 多轮继承：只写入用户业务实体槽位与 anchorPolicy，不复制 contract-owned wire / planType / tools。
     */
    public static AiQuerySemanticParseResult inheritGoodsEntity(
            AiQuerySemanticParseResult parse, String goodsName, String anchorPolicy) {
        if (parse == null || !StringUtils.hasText(goodsName)) {
            return parse;
        }
        String trimmedGoods = goodsName.trim();
        AiQuerySemanticParseResult.SemanticSlotsPart slots = parse.getSemanticSlots();
        AiQuerySemanticParseResult.SemanticSlotsPart updatedSlots =
                slots != null
                        ? AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .selectedContractId(slots.getSelectedContractId())
                                .queryObject(slots.getQueryObject())
                                .operation(slots.getOperation())
                                .metric(slots.getMetric())
                                .sourceFacet(slots.getSourceFacet())
                                .anchorPolicy(anchorPolicy)
                                .detailWanted(slots.getDetailWanted())
                                .structuredIntentDetailWire(slots.getStructuredIntentDetailWire())
                                .answerPlanType(slots.getAnswerPlanType())
                                .mentionedGoodsName(trimmedGoods)
                                .mentionedDishName(slots.getMentionedDishName())
                                .requestedTargetGrossMarginRate(slots.getRequestedTargetGrossMarginRate())
                                .expiryRiskFilter(slots.getExpiryRiskFilter())
                                .capabilitySpecificity(slots.getCapabilitySpecificity())
                                .build()
                        : AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .mentionedGoodsName(trimmedGoods)
                                .anchorPolicy(anchorPolicy)
                                .build();
        AiQuerySemanticParseResult merged =
                parse.toBuilder()
                        .mentionedGoodsName(trimmedGoods)
                        .semanticSlots(updatedSlots)
                        .build();
        return syncDraftFromParse(merged);
    }

    public static AiQuerySemanticParseResult inheritDishEntity(
            AiQuerySemanticParseResult parse, String dishName, String anchorPolicy) {
        if (parse == null || !StringUtils.hasText(dishName)) {
            return parse;
        }
        String trimmedDish = dishName.trim();
        AiQuerySemanticParseResult.SemanticSlotsPart slots = parse.getSemanticSlots();
        AiQuerySemanticParseResult.SemanticSlotsPart updatedSlots =
                slots != null
                        ? AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .selectedContractId(slots.getSelectedContractId())
                                .queryObject(slots.getQueryObject())
                                .operation(slots.getOperation())
                                .metric(slots.getMetric())
                                .sourceFacet(slots.getSourceFacet())
                                .anchorPolicy(anchorPolicy)
                                .detailWanted(slots.getDetailWanted())
                                .structuredIntentDetailWire(slots.getStructuredIntentDetailWire())
                                .answerPlanType(slots.getAnswerPlanType())
                                .mentionedDishName(trimmedDish)
                                .mentionedGoodsName(slots.getMentionedGoodsName())
                                .requestedTargetGrossMarginRate(slots.getRequestedTargetGrossMarginRate())
                                .expiryRiskFilter(slots.getExpiryRiskFilter())
                                .capabilitySpecificity(slots.getCapabilitySpecificity())
                                .build()
                        : AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .mentionedDishName(trimmedDish)
                                .anchorPolicy(anchorPolicy)
                                .build();
        AiQuerySemanticParseResult merged =
                parse.toBuilder()
                        .mentionedDishName(trimmedDish)
                        .semanticSlots(updatedSlots)
                        .build();
        return syncDraftFromParse(merged);
    }

    private static String trim(String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a.trim();
        }
        if (StringUtils.hasText(b)) {
            return b.trim();
        }
        return null;
    }
}
