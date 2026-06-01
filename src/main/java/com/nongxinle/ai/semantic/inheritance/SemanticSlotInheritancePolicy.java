package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.dimension.BareRankingDimensionSwitchPlan;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.WarehouseInventoryShortageSemanticsSupport;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V2 raw 之后、contract completion 之前的 previousTurn 继承决策。
 *
 * <p><b>硬边界（见 {@code docs/ai/semantic-inheritance-architecture.md}）：</b>
 * <ul>
 *   <li>本类只做结构化门禁（sovereign / cross-family / time-only / explicit-entity），
 *       <b>不得</b>成为 per-domain 业务 if/else 补丁中心。</li>
 *   <li>当前轮 sovereign ACTIVE contract 时，决策必须阻止 previousTurn 覆盖业务语义。</li>
 *   <li>同域 time-only follow-up 输出 {@link SemanticSlotInheritanceMode#INHERIT_SAME_FAMILY_TIME_FOLLOWUP}；
 *       完整 Business Frame 由 {@link SemanticSlotInheritanceApplier} + {@link CanonicalContractFrameSupport}
 *       从 Catalog 派生，<b>禁止</b>在此类做字段级 slot 拼装。</li>
 *   <li>禁止 {@code contains} / alias / rawMessage 猜业务语义。</li>
 * </ul>
 */
public final class SemanticSlotInheritancePolicy {

    public static final String REASON_NO_PREVIOUS_TURN = "NO_PREVIOUS_TURN";
    public static final String REASON_EXPLICIT_ENTITY_FOLLOWUP = "EXPLICIT_ENTITY_FOLLOWUP";
    public static final String REASON_CURRENT_TURN_SOVEREIGN = "CURRENT_TURN_SOVEREIGN";
    public static final String REASON_CROSS_FAMILY_SOVEREIGN = "CROSS_FAMILY_SOVEREIGN";
    public static final String REASON_SAME_FAMILY_TIME_ONLY_FOLLOWUP = "SAME_FAMILY_TIME_ONLY_FOLLOWUP";
    public static final String REASON_NOT_TIME_FOLLOWUP = "NOT_TIME_FOLLOWUP";
    public static final String REASON_BARE_RANKING_DIMENSION_SWITCH = "BARE_RANKING_DIMENSION_SWITCH";
    public static final String REASON_SAME_CAPABILITY_NAMED_ENTITY = "SAME_CAPABILITY_NAMED_ENTITY";
    public static final String REASON_SAME_GOODS_ANCHOR_FOLLOWUP = "SAME_GOODS_ANCHOR_FOLLOWUP";
    public static final String REASON_NO_PREVIOUS_FRAME = "NO_PREVIOUS_FRAME";

    private SemanticSlotInheritancePolicy() {}

    public static SemanticSlotInheritanceDecision decide(SemanticSlotInheritanceRequest request) {
        if (request == null || request.getCurrentParse() == null) {
            return decision(SemanticSlotInheritanceMode.INHERIT_NONE, REASON_NO_PREVIOUS_TURN, request);
        }
        AiQuerySemanticParseResult current = request.getCurrentParse();
        AiConversationTurnMemory previous = request.getPreviousTurn();
        DomainContractSelectionResult selection = request.getContractSelection();

        String domainHint = SemanticContractFamilySupport.resolveSelectedDomain(selection, current);
        String currentContractId = SemanticContractFamilySupport.contractIdFromParse(current);
        String previousContractId = SemanticContractFamilySupport.contractIdFromPreviousTurn(previous);
        String currentFamily = SemanticContractFamilySupport.resolveFamily(currentContractId);
        String previousFamily = SemanticContractFamilySupport.resolveFamily(previousContractId);

        boolean structuredTimeFollowUp =
                StructuredTimeFollowUpSupport.isStructuredTimeOnlyFollowUp(current);
        boolean explicitEntityFollowUp =
                ExplicitEntityFollowUpSupport.isExplicitEntityFollowUp(
                        current, previous, domainHint);
        SemanticIntakeResult intake = request.getSemanticIntake();
        boolean sameCapabilityNamedEntity =
                NamedEntitySameCapabilityFollowUpSupport.isNamedEntitySameCapabilityFollowUp(
                        current, previous, intake, explicitEntityFollowUp);
        boolean goodsAnchorSameEntity =
                GoodsAnchorSameEntityFollowUpSupport.isGoodsAnchorSameEntityFollowUp(
                        current, previous, intake);
        boolean crossFamily =
                SemanticContractFamilySupport.crossFamily(currentFamily, previousFamily);
        boolean sovereign =
                SemanticContractSovereigntySupport.hasSovereignActiveContract(
                        current,
                        currentContractId,
                        previousContractId,
                        currentFamily,
                        previousFamily,
                        domainHint,
                        selection,
                        structuredTimeFollowUp,
                        explicitEntityFollowUp,
                        sameCapabilityNamedEntity,
                        goodsAnchorSameEntity);

        if (previous == null || !SemanticContractFamilySupport.previousTurnHasStableBusinessFrame(previous)) {
            return buildDecision(
                    SemanticSlotInheritanceMode.INHERIT_NONE,
                    REASON_NO_PREVIOUS_FRAME,
                    "no stable previous business frame",
                    currentContractId,
                    previousContractId,
                    currentFamily,
                    previousFamily,
                    domainHint,
                    sovereign,
                    structuredTimeFollowUp,
                    crossFamily,
                    explicitEntityFollowUp,
                    false,
                    null);
        }

        if (sameCapabilityNamedEntity) {
            String stablePreviousContractId =
                    NamedEntitySameCapabilityFollowUpSupport.resolvePreviousStableContractId(
                            previous);
            return buildDecisionWithFollowUpPath(
                    SemanticSlotInheritanceMode.INHERIT_SAME_CAPABILITY_NAMED_ENTITY,
                    REASON_SAME_CAPABILITY_NAMED_ENTITY,
                    "same DISH_COST capability named-entity follow-up; restore previous contract frame",
                    NamedEntitySameCapabilityFollowUpSupport
                            .FOLLOW_UP_PATH_NAMED_ENTITY_SAME_CAPABILITY,
                    currentContractId,
                    stablePreviousContractId,
                    currentFamily,
                    previousFamily,
                    domainHint,
                    false,
                    structuredTimeFollowUp,
                    crossFamily,
                    true,
                    true,
                    stablePreviousContractId);
        }

        if (goodsAnchorSameEntity) {
            String stablePreviousContractId =
                    GoodsAnchorSameEntityFollowUpSupport.resolvePreviousStableContractId(previous);
            return buildDecisionWithFollowUpPath(
                    SemanticSlotInheritanceMode.INHERIT_SAME_GOODS_ANCHOR_FOLLOWUP,
                    REASON_SAME_GOODS_ANCHOR_FOLLOWUP,
                    "same GOODS anchor stock follow-up; restore warehouse.goods_supported_dish_cover frame",
                    GoodsAnchorSameEntityFollowUpSupport.FOLLOW_UP_PATH_GOODS_ANCHOR_SAME_ENTITY,
                    currentContractId,
                    stablePreviousContractId,
                    currentFamily,
                    previousFamily,
                    domainHint,
                    false,
                    structuredTimeFollowUp,
                    crossFamily,
                    false,
                    false,
                    stablePreviousContractId);
        }

        if (explicitEntityFollowUp) {
            return buildDecision(
                    SemanticSlotInheritanceMode.INHERIT_NONE,
                    REASON_EXPLICIT_ENTITY_FOLLOWUP,
                    "V2 structured explicit entity follow-up",
                    currentContractId,
                    previousContractId,
                    currentFamily,
                    previousFamily,
                    domainHint,
                    sovereign,
                    structuredTimeFollowUp,
                    crossFamily,
                    true,
                    false,
                    null);
        }

        if (sovereign) {
            SemanticSlotInheritanceMode mode =
                    crossFamily ? SemanticSlotInheritanceMode.INHERIT_CONTEXT_ONLY : SemanticSlotInheritanceMode.INHERIT_NONE;
            String reason =
                    crossFamily ? REASON_CROSS_FAMILY_SOVEREIGN : REASON_CURRENT_TURN_SOVEREIGN;
            boolean suppressPreviousDishAnchor =
                    crossFamily
                            && WarehouseInventoryShortageSemanticsSupport
                                    .intakeSignalsInventoryShortageSemantics(intake);
            return buildDecision(
                    mode,
                    reason,
                    "current turn sovereign ACTIVE contract",
                    currentContractId,
                    previousContractId,
                    currentFamily,
                    previousFamily,
                    domainHint,
                    true,
                    structuredTimeFollowUp,
                    crossFamily,
                    false,
                    suppressPreviousDishAnchor,
                    null);
        }

        if (structuredTimeFollowUp
                && SemanticContractFamilySupport.sameFamily(currentFamily, previousFamily)) {
            return buildDecisionWithFollowUpPath(
                    SemanticSlotInheritanceMode.INHERIT_SAME_FAMILY_TIME_FOLLOWUP,
                    REASON_SAME_FAMILY_TIME_ONLY_FOLLOWUP,
                    "same family structured time-only follow-up; inherit previous contract frame",
                    StructuredRankingTimeOnlyIntakeSupport.FOLLOW_UP_PATH_TIME_ONLY,
                    currentContractId,
                    previousContractId,
                    currentFamily,
                    previousFamily,
                    domainHint,
                    false,
                    true,
                    false,
                    false,
                    true,
                    null);
        }

        BareRankingDimensionSwitchPlan dimensionSwitchPlan = request.getBareRankingDimensionSwitchPlan();
        if (dimensionSwitchPlan != null && dimensionSwitchPlan.isActive()) {
            return buildDecisionWithFollowUpPath(
                    SemanticSlotInheritanceMode.INHERIT_BARE_RANKING_DIMENSION_SWITCH,
                    REASON_BARE_RANKING_DIMENSION_SWITCH,
                    "bare ranking dimension switch; regenerate frame from target contract",
                    StructuredRankingTimeOnlyIntakeSupport.FOLLOW_UP_PATH_DIMENSION_SWITCH,
                    currentContractId,
                    previousContractId,
                    currentFamily,
                    previousFamily,
                    dimensionSwitchPlan.getTargetDomain(),
                    true,
                    false,
                    true,
                    false,
                    true,
                    dimensionSwitchPlan.getTargetContractId());
        }

        return buildDecision(
                SemanticSlotInheritanceMode.INHERIT_NONE,
                REASON_NOT_TIME_FOLLOWUP,
                "no applicable inheritance policy",
                currentContractId,
                previousContractId,
                currentFamily,
                previousFamily,
                domainHint,
                sovereign,
                structuredTimeFollowUp,
                crossFamily,
                false,
                false,
                null);
    }

    private static SemanticSlotInheritanceDecision decision(
            SemanticSlotInheritanceMode mode, String reasonCode, SemanticSlotInheritanceRequest request) {
        return buildDecision(
                mode,
                reasonCode,
                reasonCode,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                null);
    }

    private static SemanticSlotInheritanceDecision buildDecisionWithFollowUpPath(
            SemanticSlotInheritanceMode mode,
            String reasonCode,
            String reasonDetail,
            String followUpPath,
            String currentContractId,
            String previousContractId,
            String currentFamily,
            String previousFamily,
            String currentDomain,
            boolean sovereign,
            boolean structuredTimeFollowUp,
            boolean crossFamily,
            boolean explicitEntityFollowUp,
            boolean suppressPreviousDishAnchor,
            String targetContractId) {
        SemanticSlotInheritanceDecision decision =
                buildDecision(
                        mode,
                        reasonCode,
                        reasonDetail,
                        currentContractId,
                        previousContractId,
                        currentFamily,
                        previousFamily,
                        currentDomain,
                        sovereign,
                        structuredTimeFollowUp,
                        crossFamily,
                        explicitEntityFollowUp,
                        suppressPreviousDishAnchor,
                        targetContractId);
        if (!StringUtils.hasText(followUpPath)) {
            return decision;
        }
        Map<String, Object> trace =
                decision.getTrace() != null
                        ? new LinkedHashMap<>(decision.getTrace())
                        : new LinkedHashMap<>();
        trace.put("followUpPath", followUpPath);
        return decision.toBuilder().trace(trace).build();
    }

    private static SemanticSlotInheritanceDecision buildDecision(
            SemanticSlotInheritanceMode mode,
            String reasonCode,
            String reasonDetail,
            String currentContractId,
            String previousContractId,
            String currentFamily,
            String previousFamily,
            String currentDomain,
            boolean sovereign,
            boolean structuredTimeFollowUp,
            boolean crossFamily,
            boolean explicitEntityFollowUp,
            boolean suppressPreviousDishAnchor,
            String targetContractId) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("inheritanceMode", mode.name());
        trace.put("reasonCode", reasonCode);
        if (StringUtils.hasText(currentContractId)) {
            trace.put("currentContractId", currentContractId);
        }
        if (StringUtils.hasText(previousContractId)) {
            trace.put("previousContractId", previousContractId);
        }
        if (StringUtils.hasText(targetContractId)) {
            trace.put("targetContractId", targetContractId);
        }
        return SemanticSlotInheritanceDecision.builder()
                .mode(mode)
                .reasonCode(reasonCode)
                .reasonDetail(reasonDetail)
                .currentContractId(currentContractId)
                .previousContractId(previousContractId)
                .currentFamily(currentFamily)
                .previousFamily(previousFamily)
                .currentDomain(currentDomain)
                .currentHasSovereignActiveContract(sovereign)
                .structuredTimeFollowUp(structuredTimeFollowUp)
                .crossFamily(crossFamily)
                .explicitEntityFollowUp(explicitEntityFollowUp)
                .suppressPreviousDishAnchor(suppressPreviousDishAnchor)
                .targetContractId(targetContractId)
                .trace(trace)
                .build();
    }
}
