package com.nongxinle.ai.semantic.intake.grounding;

import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.identity.BusinessEntityExistenceLookup;
import com.nongxinle.ai.identity.CanonicalResultAnchorIdentitySupport;
import com.nongxinle.ai.semantic.intake.SemanticIntakeInput;
import com.nongxinle.ai.semantic.intake.SemanticIntakePrimaryDomain;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeStatus;
import com.nongxinle.ai.semantic.intake.grounding.CoverDaysEntityType;
import com.nongxinle.ai.semantic.intake.grounding.EntityExistenceProbeSupport.EntityExistenceProbeResult;
import com.nongxinle.ai.semantic.intake.grounding.EntityExistenceProbeSupport.EntityExistenceOutcome;
import com.nongxinle.ai.semantic.intake.grounding.EntityGroundingFamilyRegistry.FamilyEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 公共实体存在性落地（V2 前）。
 *
 * <p>仅对已登记 {@link EntityGroundingFamily} 生效；Java 不读用户原文猜合同。
 */
@Service
@RequiredArgsConstructor
public class EntityExistenceGroundingService {

    private static final String CLARIFICATION_PURCHASE_DISH_FOUND =
            "未找到匹配的库存原料，但找到同名菜品。请确认您要查询的是菜品还是原料。";

    private static final String NAMED_SALES_GOODS_UNIQUE_CLARIFICATION_TEMPLATE =
            "系统找到的是原料『%s』，没有找到同名菜品。你想查看%s的采购情况、库存及可用天数，还是使用%s的菜品销售情况？";

    private final BusinessEntityExistenceLookup existenceLookup;
    private final EntityGroundingFamilyRegistry familyRegistry;

    /** Intake reconcile（V2 前）：cover-days 族可升级 primaryDomain / NEED_CLARIFICATION。 */
    public SemanticIntakeResult reconcileIntake(SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (intake == null) {
            return null;
        }
        if (intake.getStatus() != SemanticIntakeStatus.READY
                && intake.getStatus() != SemanticIntakeStatus.NEED_CLARIFICATION) {
            return intake;
        }
        if (!CoverDaysEntityGroundingService.intakeSignalsCoverDaysSemantics(intake)) {
            return intake;
        }
        if (!CoverDaysEntityGroundingService.hasCoverDaysEntityGroundingSignals(intake)) {
            return intake;
        }
        Integer disId = resolveDisId(input);
        String entityName = resolveEntityNameFromIntake(intake);
        String llmType = CoverDaysEntityType.resolveForGrounding(intake.getCoverDaysEntityType());
        if (disId == null || !StringUtils.hasText(entityName)) {
            return intake;
        }
        FamilyEntry family = familyRegistry.coverDaysEntry();
        if (family == null) {
            return intake;
        }
        EntityExistenceProbeResult probe =
                probeWithCanonicalGoodsIdPreference(
                        input == null ? null : input.getResultAnchors(), disId, llmType, entityName.trim());
        FamilyGroundingDecision decision = decideForFamily(family, probe);
        if (decision.kind == GroundingDecisionKind.NEED_CLARIFICATION) {
            return rebuildIntake(intake, SemanticIntakeStatus.NEED_CLARIFICATION, true, decision.clarificationQuestion);
        }
        if (decision.kind == GroundingDecisionKind.SWITCH_TO_DISH) {
            SemanticIntakeResult ready = rebuildIntake(intake, SemanticIntakeStatus.READY, false, null);
            ready.setPrimaryDomain(SemanticIntakePrimaryDomain.DISH_COST);
            ready.setCandidateDomains(java.util.List.of(SemanticIntakePrimaryDomain.DISH_COST));
            return ready;
        }
        if (decision.kind == GroundingDecisionKind.SWITCH_TO_GOODS) {
            SemanticIntakeResult ready = rebuildIntake(intake, SemanticIntakeStatus.READY, false, null);
            ready.setPrimaryDomain(SemanticIntakePrimaryDomain.WAREHOUSE);
            ready.setCandidateDomains(java.util.List.of(SemanticIntakePrimaryDomain.WAREHOUSE));
            return ready;
        }
        return intake;
    }

    private FamilyGroundingDecision decideForFamily(
            FamilyEntry family, EntityExistenceProbeResult probe) {
        if (probe.outcome() == EntityExistenceOutcome.NEED_CLARIFICATION) {
            return FamilyGroundingDecision.clarify(probe.clarificationQuestion());
        }
        return switch (family.family()) {
            case COVER_DAYS -> decideCoverDays(family, probe);
            case NAMED_SALES -> decideNamedSales(probe);
            case PURCHASE_GOODS_BIZ -> decidePurchaseGoodsBiz(probe);
        };
    }

    private static FamilyGroundingDecision decideCoverDays(
            FamilyEntry family, EntityExistenceProbeResult probe) {
        if (probe.outcome() == EntityExistenceOutcome.DISH_UNIQUE) {
            return FamilyGroundingDecision.switchToDish(
                    family.dishPeerContractId(), family.dishPeerDomain());
        }
        if (probe.outcome() == EntityExistenceOutcome.GOODS_UNIQUE) {
            return FamilyGroundingDecision.switchToGoods(
                    family.goodsPeerContractId(), family.goodsPeerDomain());
        }
        return FamilyGroundingDecision.clarify(probe.clarificationQuestion());
    }

    private static FamilyGroundingDecision decideNamedSales(EntityExistenceProbeResult probe) {
        if (probe.outcome() == EntityExistenceOutcome.DISH_UNIQUE) {
            return FamilyGroundingDecision.unchanged();
        }
        if (probe.outcome() == EntityExistenceOutcome.GOODS_UNIQUE) {
            String name = probe.entityName();
            String question = String.format(NAMED_SALES_GOODS_UNIQUE_CLARIFICATION_TEMPLATE, name, name, name);
            return FamilyGroundingDecision.clarify(question);
        }
        return FamilyGroundingDecision.clarify(probe.clarificationQuestion());
    }

    private static FamilyGroundingDecision decidePurchaseGoodsBiz(EntityExistenceProbeResult probe) {
        if (probe.outcome() == EntityExistenceOutcome.GOODS_UNIQUE) {
            return FamilyGroundingDecision.unchanged();
        }
        if (probe.outcome() == EntityExistenceOutcome.DISH_UNIQUE) {
            return FamilyGroundingDecision.clarify(CLARIFICATION_PURCHASE_DISH_FOUND);
        }
        return FamilyGroundingDecision.clarify(probe.clarificationQuestion());
    }

    private static SemanticIntakeResult rebuildIntake(
            SemanticIntakeResult intake,
            SemanticIntakeStatus status,
            boolean needClarification,
            String clarificationQuestion) {
        return SemanticIntakeResult.builder()
                .status(status)
                .questionMode(intake.getQuestionMode())
                .normalizationType(intake.getNormalizationType())
                .canonicalUserQuery(intake.getCanonicalUserQuery())
                .isFollowUp(intake.getIsFollowUp())
                .usedPreviousContext(intake.getUsedPreviousContext())
                .primaryDomain(intake.getPrimaryDomain())
                .candidateDomains(intake.getCandidateDomains())
                .routeType(intake.getRouteType())
                .confidence(intake.getConfidence())
                .needClarification(needClarification)
                .clarificationQuestion(clarificationQuestion)
                .reason(intake.getReason())
                .warehouseInventorySemantics(intake.getWarehouseInventorySemantics())
                .coverDaysEntityType(intake.getCoverDaysEntityType())
                .coverDaysEntityName(intake.getCoverDaysEntityName())
                .subQuestions(intake.getSubQuestions())
                .promptId(intake.getPromptId())
                .llmRawText(intake.getLlmRawText())
                .parseError(intake.getParseError())
                .build();
    }

    private static String resolveEntityNameFromIntake(SemanticIntakeResult intake) {
        if (intake == null || !StringUtils.hasText(intake.getCoverDaysEntityName())) {
            return null;
        }
        return intake.getCoverDaysEntityName().trim();
    }

    private EntityExistenceProbeResult probeWithCanonicalGoodsIdPreference(
            List<AiResultAnchor> previousAnchors, int disId, String llmType, String entityName) {
        if (CoverDaysEntityType.GOODS.equals(llmType)) {
            Integer goodsId = resolveTrustworthyGoodsDisId(previousAnchors);
            if (goodsId != null) {
                EntityExistenceProbeResult byId =
                        EntityExistenceProbeSupport.probeGoodsByCanonicalId(existenceLookup, goodsId);
                if (byId != null) {
                    return byId;
                }
            }
        }
        return EntityExistenceProbeSupport.probe(existenceLookup, disId, llmType, entityName);
    }

    private static Integer resolveTrustworthyGoodsDisId(List<AiResultAnchor> anchors) {
        return CanonicalResultAnchorIdentitySupport.resolveTrustworthyGoodsDisId(anchors);
    }

    private static Integer resolveDisId(SemanticIntakeInput input) {
        if (input == null || input.getDistributerId() == null || input.getDistributerId() <= 0) {
            return null;
        }
        return input.getDistributerId().intValue();
    }

    private static String blank(String s) {
        return s == null ? null : s.trim();
    }

    private enum GroundingDecisionKind {
        UNCHANGED,
        SWITCH_TO_DISH,
        SWITCH_TO_GOODS,
        NEED_CLARIFICATION
    }

    private record FamilyGroundingDecision(
            GroundingDecisionKind kind,
            String targetContractId,
            String targetDomain,
            String clarificationQuestion) {

        static FamilyGroundingDecision unchanged() {
            return new FamilyGroundingDecision(GroundingDecisionKind.UNCHANGED, null, null, null);
        }

        static FamilyGroundingDecision switchToDish(String contractId, String domain) {
            return new FamilyGroundingDecision(
                    GroundingDecisionKind.SWITCH_TO_DISH, contractId, domain, null);
        }

        static FamilyGroundingDecision switchToGoods(String contractId, String domain) {
            return new FamilyGroundingDecision(
                    GroundingDecisionKind.SWITCH_TO_GOODS, contractId, domain, null);
        }

        static FamilyGroundingDecision clarify(String question) {
            return new FamilyGroundingDecision(
                    GroundingDecisionKind.NEED_CLARIFICATION, null, null, question);
        }

        Map<String, Object> trace(FamilyEntry family, EntityExistenceProbeResult probe) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("entityGroundingFamily", family.family().name());
            m.put("entityGroundingDecision", kind.name());
            if (StringUtils.hasText(probe.entityName())) {
                m.put("entityGroundingEntityName", probe.entityName());
            }
            if (probe.dishExistence() != null) {
                m.put("dishExistence", probe.dishExistence().name());
            }
            if (probe.goodsExistence() != null) {
                m.put("goodsExistence", probe.goodsExistence().name());
            }
            return m;
        }
    }

}
