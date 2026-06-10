package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.identity.CanonicalResultAnchorIdentitySupport;
import com.nongxinle.ai.semantic.intake.grounding.CoverDaysEntityType;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Intake REWRITE 选中 {@code resultAnchors} 条目的结构化 provenance（type/id/name/source）。
 * 不读 {@code canonicalUserQuery}，不做 NL 名称查库。
 */
public final class SemanticIntakeRewriteResultAnchorProvenanceSupport {

    public static final String SOURCE_INTAKE_REWRITE_RESULT_ANCHOR = "intake.rewrite.resultAnchor";

    private SemanticIntakeRewriteResultAnchorProvenanceSupport() {}

    public record Provenance(
            String entityType,
            String entityId,
            String entityName,
            String source,
            String sourcePlanType) {

        public Map<String, String> toWireMap() {
            LinkedHashMap<String, String> wire = new LinkedHashMap<>();
            if (StringUtils.hasText(entityType)) {
                wire.put("entityType", entityType.trim());
            }
            if (StringUtils.hasText(entityId)) {
                wire.put("entityId", entityId.trim());
            }
            if (StringUtils.hasText(entityName)) {
                wire.put("entityName", entityName.trim());
            }
            if (StringUtils.hasText(source)) {
                wire.put("source", source.trim());
            }
            if (StringUtils.hasText(sourcePlanType)) {
                wire.put("sourcePlanType", sourcePlanType.trim());
            }
            return wire;
        }
    }

    public static Provenance resolve(SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (input == null || intake == null) {
            return null;
        }
        if (intake.getNormalizationType() != SemanticIntakeNormalizationType.REWRITE) {
            return null;
        }
        if (!Boolean.TRUE.equals(intake.getUsedPreviousContext())
                && !Boolean.TRUE.equals(intake.getIsFollowUp())) {
            return null;
        }
        List<AiResultAnchor> anchors = input.getResultAnchors();
        if (anchors == null || anchors.isEmpty()) {
            return null;
        }
        AiResultAnchor adopted = selectAdoptedAnchor(input, intake, anchors);
        if (adopted == null) {
            return null;
        }
        return toProvenance(adopted);
    }

    private static Provenance toProvenance(AiResultAnchor adopted) {
        String type = trim(adopted.getEntityType());
        String id = trim(adopted.getEntityId());
        String name = trim(adopted.getEntityName());
        if (!StringUtils.hasText(type) || (!StringUtils.hasText(id) && !StringUtils.hasText(name))) {
            return null;
        }
        return new Provenance(
                type,
                id,
                name,
                SOURCE_INTAKE_REWRITE_RESULT_ANCHOR,
                trim(adopted.getSourcePlanType()));
    }

    private static AiResultAnchor selectAdoptedAnchor(
            SemanticIntakeInput input, SemanticIntakeResult intake, List<AiResultAnchor> anchors) {
        String hintedType = CoverDaysEntityType.normalize(intake.getCoverDaysEntityType());
        String hintedName = trim(intake.getCoverDaysEntityName());
        if (StringUtils.hasText(hintedType) && StringUtils.hasText(hintedName)) {
            AiResultAnchor matched = matchByTypeAndName(anchors, hintedType, hintedName);
            if (matched != null) {
                return matched;
            }
        }

        List<AiResultAnchor> trustworthyGoods = filterTrustworthyGoods(anchors);
        if (trustworthyGoods.size() == 1) {
            return trustworthyGoods.get(0);
        }

        if (signalsGoodsAnchorCarryForward(input, intake) && !trustworthyGoods.isEmpty()) {
            return trustworthyGoods.get(0);
        }

        List<AiResultAnchor> dishAnchors = filterNamedAnchors(anchors, AiResultAnchor.ENTITY_TYPE_DISH);
        if (dishAnchors.size() == 1) {
            return dishAnchors.get(0);
        }

        return null;
    }

    private static boolean signalsGoodsAnchorCarryForward(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (SemanticIntakeGoodsAnchorFollowUpSupport.reasonDeclaresGoodsAnchorStockFollowUp(
                intake.getReason())) {
            return true;
        }
        if (!Boolean.TRUE.equals(intake.getUsedPreviousContext())) {
            return false;
        }
        if (!SemanticIntakePrimaryDomain.WAREHOUSE.equals(
                SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain()))) {
            return false;
        }
        return SemanticIntakeGoodsAnchorFollowUpSupport.previousTurnDeclaresGoodsAnchorInventory(input);
    }

    private static AiResultAnchor matchByTypeAndName(
            List<AiResultAnchor> anchors, String entityType, String entityName) {
        AiResultAnchor idMatch = null;
        AiResultAnchor nameMatch = null;
        for (AiResultAnchor anchor : anchors) {
            if (anchor == null || !StringUtils.hasText(anchor.getEntityType())) {
                continue;
            }
            if (!entityType.equalsIgnoreCase(anchor.getEntityType().trim())) {
                continue;
            }
            if (StringUtils.hasText(anchor.getEntityId())
                    && namesEqual(entityName, anchor.getEntityName())) {
                idMatch = anchor;
                break;
            }
            if (namesEqual(entityName, anchor.getEntityName())) {
                nameMatch = anchor;
            }
        }
        return idMatch != null ? idMatch : nameMatch;
    }

    private static List<AiResultAnchor> filterTrustworthyGoods(List<AiResultAnchor> anchors) {
        List<AiResultAnchor> out = new ArrayList<>();
        for (AiResultAnchor anchor : anchors) {
            if (CanonicalResultAnchorIdentitySupport.isTrustworthyGoodsAnchor(anchor)) {
                out.add(anchor);
            }
        }
        return out;
    }

    private static List<AiResultAnchor> filterNamedAnchors(List<AiResultAnchor> anchors, String entityType) {
        List<AiResultAnchor> out = new ArrayList<>();
        for (AiResultAnchor anchor : anchors) {
            if (anchor == null || !StringUtils.hasText(anchor.getEntityType())) {
                continue;
            }
            if (!entityType.equalsIgnoreCase(anchor.getEntityType().trim())) {
                continue;
            }
            if (StringUtils.hasText(anchor.getEntityName()) || StringUtils.hasText(anchor.getEntityId())) {
                out.add(anchor);
            }
        }
        return out;
    }

    private static boolean namesEqual(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private static String trim(String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }
}
