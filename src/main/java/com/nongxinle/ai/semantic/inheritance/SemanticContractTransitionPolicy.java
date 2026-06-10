package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.dimension.BareRankingDimensionSwitchPlan;
import com.nongxinle.ai.semantic.intake.SemanticIntakeFollowUpIntent;
import com.nongxinle.ai.semantic.intake.SemanticIntakeFollowUpKind;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.WarehouseInventoryShortageSemanticsSupport;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同状态迁移统一策略：在 sovereign / cross-family 阻断之前，先判定已注册的合法 transition。
 * 输出 {@link SemanticContractTransitionDecision} 供 {@link SemanticSlotInheritancePolicy} 与
 * {@link SemanticContractSovereigntySupport} 消费。
 */
public final class SemanticContractTransitionPolicy {

    public static final String REASON_SAME_CAPABILITY_TIME_OVERRIDE = "SAME_CAPABILITY_TIME_OVERRIDE";
    public static final String REASON_SAME_FAMILY_TIME_OVERRIDE = "SAME_FAMILY_TIME_OVERRIDE";
    public static final String REASON_SAME_CAPABILITY_NAMED_ENTITY = "SAME_CAPABILITY_NAMED_ENTITY";
    public static final String REASON_SAME_GOODS_ANCHOR = "SAME_GOODS_ANCHOR_ENTITY";
    public static final String REASON_BARE_RANKING_DIMENSION_SWITCH = "BARE_RANKING_DIMENSION_SWITCH";
    public static final String REASON_SOVEREIGN_NEW_CAPABILITY = "SOVEREIGN_NEW_CAPABILITY";
    public static final String REASON_CROSS_FAMILY_CONTEXT_ONLY = "CROSS_FAMILY_SOVEREIGN";
    public static final String REASON_EXPLICIT_ENTITY = "EXPLICIT_ENTITY_FOLLOWUP";
    public static final String REASON_NONE = "NO_APPLICABLE_TRANSITION";

    public static final List<String> PRESERVED_BUSINESS_FRAME =
            List.of(
                    "selectedContractId",
                    "queryObject",
                    "operation",
                    "metric",
                    "sourceFacet",
                    "mentionedDishName",
                    "mentionedGoodsName",
                    "anchorPolicy");
    public static final List<String> OVERRIDDEN_TIME_AND_BASELINE =
            List.of("time", "timeAction", "timeSource", "salesBaselineWindow");

    private SemanticContractTransitionPolicy() {}

    public static SemanticContractTransitionDecision decide(SemanticSlotInheritanceRequest request) {
        if (request == null || request.getCurrentParse() == null) {
            return noneDecision(null);
        }
        AiQuerySemanticParseResult current = request.getCurrentParse();
        AiConversationTurnMemory previous = request.getPreviousTurn();
        SemanticIntakeResult intake = request.getSemanticIntake();
        DomainContractSelectionResult selection = request.getContractSelection();
        BareRankingDimensionSwitchPlan dimensionSwitchPlan = request.getBareRankingDimensionSwitchPlan();

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
        SemanticIntakeFollowUpIntent followUpIntent =
                intake != null ? intake.getFollowUpIntent() : null;

        if (previous == null || !SemanticContractFamilySupport.previousTurnHasStableBusinessFrame(previous)) {
            return noneDecision(
                    trace(currentContractId, previousContractId, currentFamily, previousFamily));
        }

        // --- Registered legal transitions (priority order) ---

        if (NamedEntitySameCapabilityFollowUpSupport.isNamedEntitySameCapabilityFollowUp(
                current, previous, intake, explicitEntityFollowUp)) {
            String stable =
                    NamedEntitySameCapabilityFollowUpSupport.resolvePreviousStableContractId(
                            previous);
            return transition(
                    SemanticContractTransitionType.SAME_CAPABILITY_NAMED_ENTITY,
                    REASON_SAME_CAPABILITY_NAMED_ENTITY,
                    stable,
                    SemanticSlotInheritanceMode.INHERIT_SAME_CAPABILITY_NAMED_ENTITY,
                    NamedEntitySameCapabilityFollowUpSupport
                            .FOLLOW_UP_PATH_NAMED_ENTITY_SAME_CAPABILITY,
                    false,
                    null,
                    currentContractId,
                    previousContractId,
                    currentFamily,
                    previousFamily);
        }

        if (GoodsAnchorSameEntityFollowUpSupport.isGoodsAnchorSameEntityFollowUp(
                current, previous, intake)) {
            String stable = GoodsAnchorSameEntityFollowUpSupport.resolvePreviousStableContractId(previous);
            return transition(
                    SemanticContractTransitionType.SAME_GOODS_ANCHOR_ENTITY,
                    REASON_SAME_GOODS_ANCHOR,
                    stable,
                    SemanticSlotInheritanceMode.INHERIT_SAME_GOODS_ANCHOR_FOLLOWUP,
                    GoodsAnchorSameEntityFollowUpSupport.FOLLOW_UP_PATH_GOODS_ANCHOR_SAME_ENTITY,
                    false,
                    null,
                    currentContractId,
                    previousContractId,
                    currentFamily,
                    previousFamily);
        }

        SemanticContractTransitionDecision sameCapabilityTime =
                resolveSameCapabilityTimeOverride(
                        current,
                        previous,
                        intake,
                        followUpIntent,
                        structuredTimeFollowUp,
                        currentContractId,
                        previousContractId,
                        currentFamily,
                        previousFamily);
        if (sameCapabilityTime != null) {
            return sameCapabilityTime;
        }

        if (explicitEntityFollowUp) {
            return transition(
                    SemanticContractTransitionType.EXPLICIT_ENTITY_NEW_CAPABILITY,
                    REASON_EXPLICIT_ENTITY,
                    currentContractId,
                    SemanticSlotInheritanceMode.INHERIT_NONE,
                    null,
                    false,
                    null,
                    currentContractId,
                    previousContractId,
                    currentFamily,
                    previousFamily);
        }

        if (structuredTimeFollowUp && isRankingTimeOnlyFollowUp(followUpIntent)) {
            return transition(
                    SemanticContractTransitionType.SAME_FAMILY_TIME_OVERRIDE,
                    REASON_SAME_FAMILY_TIME_OVERRIDE,
                    previousContractId,
                    SemanticSlotInheritanceMode.INHERIT_SAME_FAMILY_TIME_FOLLOWUP,
                    StructuredRankingTimeOnlyIntakeSupport.FOLLOW_UP_PATH_RANKING_TIME_ONLY,
                    false,
                    null,
                    currentContractId,
                    previousContractId,
                    currentFamily,
                    previousFamily);
        }

        if (dimensionSwitchPlan != null && dimensionSwitchPlan.isActive()) {
            return transition(
                    SemanticContractTransitionType.BARE_RANKING_DIMENSION_SWITCH,
                    REASON_BARE_RANKING_DIMENSION_SWITCH,
                    dimensionSwitchPlan.getTargetContractId(),
                    SemanticSlotInheritanceMode.INHERIT_BARE_RANKING_DIMENSION_SWITCH,
                    StructuredRankingTimeOnlyIntakeSupport.FOLLOW_UP_PATH_DIMENSION_SWITCH,
                    true,
                    dimensionSwitchPlan.getTargetContractId(),
                    currentContractId,
                    previousContractId,
                    currentFamily,
                    previousFamily);
        }

        // --- Sovereign / fallback ---

        if (isSovereignNewCapability(
                current,
                previous,
                currentContractId,
                previousContractId,
                currentFamily,
                previousFamily,
                domainHint,
                selection,
                structuredTimeFollowUp)) {
            return transition(
                    SemanticContractTransitionType.SOVEREIGN_NEW_CAPABILITY,
                    REASON_SOVEREIGN_NEW_CAPABILITY,
                    currentContractId,
                    SemanticSlotInheritanceMode.INHERIT_NONE,
                    null,
                    false,
                    null,
                    currentContractId,
                    previousContractId,
                    currentFamily,
                    previousFamily);
        }

        if (SemanticContractFamilySupport.crossFamily(currentFamily, previousFamily)) {
            boolean suppressPreviousDishAnchor =
                    WarehouseInventoryShortageSemanticsSupport.intakeSignalsInventoryShortageSemantics(
                            intake);
            return transition(
                    SemanticContractTransitionType.CROSS_FAMILY_CONTEXT_ONLY,
                    REASON_CROSS_FAMILY_CONTEXT_ONLY,
                    currentContractId,
                    SemanticSlotInheritanceMode.INHERIT_CONTEXT_ONLY,
                    null,
                    suppressPreviousDishAnchor,
                    null,
                    currentContractId,
                    previousContractId,
                    currentFamily,
                    previousFamily);
        }

        return noneDecision(
                trace(currentContractId, previousContractId, currentFamily, previousFamily));
    }

    private static boolean isRankingTimeOnlyFollowUp(SemanticIntakeFollowUpIntent followUpIntent) {
        return followUpIntent != null
                && followUpIntent.getKind() == SemanticIntakeFollowUpKind.RANKING_TIME_OVERRIDE;
    }

    private static SemanticContractTransitionDecision resolveSameCapabilityTimeOverride(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previous,
            SemanticIntakeResult intake,
            SemanticIntakeFollowUpIntent followUpIntent,
            boolean structuredTimeFollowUp,
            String currentContractId,
            String previousContractId,
            String currentFamily,
            String previousFamily) {
        if (!structuredTimeFollowUp) {
            return null;
        }
        if (!SameCapabilityTimeOverrideSupport.isSameCapabilityTimeOverrideSignal(
                intake, followUpIntent, previous)) {
            return null;
        }
        if (BusinessFrameMaterialChangeSupport.currentMateriallyChangesStableBusinessFrame(
                current, previous)) {
            return null;
        }
        String stableContractId =
                SameCapabilityTimeOverrideSupport.resolveStableContractId(followUpIntent, previous);
        if (!StringUtils.hasText(stableContractId)) {
            return null;
        }
        return transition(
                SemanticContractTransitionType.SAME_CAPABILITY_TIME_OVERRIDE,
                REASON_SAME_CAPABILITY_TIME_OVERRIDE,
                stableContractId,
                SemanticSlotInheritanceMode.INHERIT_SAME_CAPABILITY_TIME_FOLLOWUP,
                SameCapabilityTimeOverrideSupport.FOLLOW_UP_PATH_STRUCTURED_TIME_ONLY,
                false,
                null,
                currentContractId,
                previousContractId,
                currentFamily,
                previousFamily);
    }

    static boolean isSovereignNewCapability(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previous,
            String currentContractId,
            String previousContractId,
            String currentFamily,
            String previousFamily,
            String domainHint,
            DomainContractSelectionResult selection,
            boolean structuredTimeFollowUp) {
        if (!SemanticContractSovereigntySupport.isCatalogValidActiveContract(
                currentContractId, domainHint, selection)) {
            return false;
        }
        if (BusinessFrameMaterialChangeSupport.currentMateriallyChangesStableBusinessFrame(
                current, previous)) {
            return true;
        }
        if (SemanticContractFamilySupport.crossFamily(currentFamily, previousFamily)) {
            return true;
        }
        if (!structuredTimeFollowUp) {
            return true;
        }
        if (StringUtils.hasText(previousContractId)
                && StringUtils.hasText(currentContractId)
                && !currentContractId.equals(previousContractId)) {
            return false;
        }
        return true;
    }

    private static SemanticContractTransitionDecision transition(
            SemanticContractTransitionType type,
            String reasonCode,
            String effectiveContractId,
            SemanticSlotInheritanceMode mode,
            String followUpPath,
            boolean suppressPreviousDishAnchor,
            String targetContractId,
            String currentContractId,
            String previousContractId,
            String currentFamily,
            String previousFamily) {
        Map<String, Object> trace =
                trace(currentContractId, previousContractId, currentFamily, previousFamily);
        trace.put("transitionType", type.name());
        trace.put("reasonCode", reasonCode);
        if (StringUtils.hasText(followUpPath)) {
            trace.put("followUpPath", followUpPath);
        }
        List<String> preserved =
                switch (type) {
                    case SAME_CAPABILITY_TIME_OVERRIDE, SAME_FAMILY_TIME_OVERRIDE,
                            SAME_CAPABILITY_NAMED_ENTITY, SAME_GOODS_ANCHOR_ENTITY,
                            BARE_RANKING_DIMENSION_SWITCH -> PRESERVED_BUSINESS_FRAME;
                    default -> List.of();
                };
        List<String> overridden =
                switch (type) {
                    case SAME_CAPABILITY_TIME_OVERRIDE, SAME_FAMILY_TIME_OVERRIDE ->
                            OVERRIDDEN_TIME_AND_BASELINE;
                    default -> List.of();
                };
        return SemanticContractTransitionDecision.builder()
                .transitionType(type)
                .effectiveContractId(effectiveContractId)
                .preservedFields(preserved)
                .overriddenFields(overridden)
                .reasonCode(reasonCode)
                .inheritanceMode(mode)
                .followUpPath(followUpPath)
                .suppressPreviousDishAnchor(suppressPreviousDishAnchor)
                .targetContractId(targetContractId)
                .trace(trace)
                .build();
    }

    private static SemanticContractTransitionDecision noneDecision(Map<String, Object> trace) {
        return SemanticContractTransitionDecision.builder()
                .transitionType(SemanticContractTransitionType.NONE)
                .reasonCode(REASON_NONE)
                .inheritanceMode(SemanticSlotInheritanceMode.INHERIT_NONE)
                .preservedFields(List.of())
                .overriddenFields(List.of())
                .trace(trace != null ? trace : Map.of())
                .build();
    }

    private static Map<String, Object> trace(
            String currentContractId,
            String previousContractId,
            String currentFamily,
            String previousFamily) {
        Map<String, Object> t = new LinkedHashMap<>();
        if (StringUtils.hasText(currentContractId)) {
            t.put("currentContractId", currentContractId);
        }
        if (StringUtils.hasText(previousContractId)) {
            t.put("previousContractId", previousContractId);
        }
        if (StringUtils.hasText(currentFamily)) {
            t.put("currentFamily", currentFamily);
        }
        if (StringUtils.hasText(previousFamily)) {
            t.put("previousFamily", previousFamily);
        }
        return t;
    }
}
