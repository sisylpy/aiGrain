package com.nongxinle.ai.semantic.intake.grounding;

import com.nongxinle.ai.identity.BusinessEntityExistenceLookup;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

/** 双向对称存在性探测：DISH-first / GOODS-first，输出结构化 probe 结果。 */
@UtilityClass
public final class EntityExistenceProbeSupport {

    private static final String CLARIFICATION_NOT_FOUND =
            "未找到匹配的菜品或库存原料，请确认名称是否正确，或说出更准确的菜品/原料名。";
    private static final String CLARIFICATION_DISH_AMBIGUOUS = "找到多个同名菜品，请说更完整的菜名。";
    private static final String CLARIFICATION_GOODS_AMBIGUOUS = "找到多个同名库存原料，请说更完整的原料名。";

    public static EntityExistenceProbeResult probeDishFirst(
            BusinessEntityExistenceLookup lookup, int disId, String entityName) {
        EntityExistence dish = lookup.probeDish(disId, entityName);
        if (dish == EntityExistence.UNIQUE) {
            return EntityExistenceProbeResult.resolvedDish(entityName, dish, null);
        }
        if (dish == EntityExistence.AMBIGUOUS) {
            return EntityExistenceProbeResult.clarify(
                    CLARIFICATION_DISH_AMBIGUOUS, entityName, CoverDaysEntityType.DISH, dish, null);
        }
        EntityExistence goods = lookup.probeGoods(disId, entityName);
        if (goods == EntityExistence.UNIQUE) {
            return EntityExistenceProbeResult.resolvedGoods(entityName, dish, goods);
        }
        if (goods == EntityExistence.AMBIGUOUS) {
            return EntityExistenceProbeResult.clarify(
                    CLARIFICATION_GOODS_AMBIGUOUS, entityName, CoverDaysEntityType.GOODS, dish, goods);
        }
        return EntityExistenceProbeResult.clarify(
                CLARIFICATION_NOT_FOUND, entityName, CoverDaysEntityType.DISH, dish, goods);
    }

    public static EntityExistenceProbeResult probeGoodsFirst(
            BusinessEntityExistenceLookup lookup, int disId, String entityName) {
        EntityExistence goods = lookup.probeGoods(disId, entityName);
        if (goods == EntityExistence.UNIQUE) {
            return EntityExistenceProbeResult.resolvedGoods(entityName, null, goods);
        }
        if (goods == EntityExistence.AMBIGUOUS) {
            return EntityExistenceProbeResult.clarify(
                    CLARIFICATION_GOODS_AMBIGUOUS, entityName, CoverDaysEntityType.GOODS, null, goods);
        }
        EntityExistence dish = lookup.probeDish(disId, entityName);
        if (dish == EntityExistence.UNIQUE) {
            return EntityExistenceProbeResult.resolvedDish(entityName, dish, goods);
        }
        if (dish == EntityExistence.AMBIGUOUS) {
            return EntityExistenceProbeResult.clarify(
                    CLARIFICATION_DISH_AMBIGUOUS, entityName, CoverDaysEntityType.DISH, dish, goods);
        }
        return EntityExistenceProbeResult.clarify(
                CLARIFICATION_NOT_FOUND, entityName, CoverDaysEntityType.GOODS, dish, goods);
    }

    public static EntityExistenceProbeResult probe(
            BusinessEntityExistenceLookup lookup,
            int disId,
            String llmEntityType,
            String entityName) {
        if (CoverDaysEntityType.DISH.equals(llmEntityType)) {
            return probeDishFirst(lookup, disId, entityName);
        }
        if (CoverDaysEntityType.GOODS.equals(llmEntityType)) {
            return probeGoodsFirst(lookup, disId, entityName);
        }
        return probeDishFirst(lookup, disId, entityName);
    }

    /** 可信 canonical disGoodsId 优先于同名模糊匹配。 */
    public static EntityExistenceProbeResult probeGoodsByCanonicalId(
            BusinessEntityExistenceLookup lookup, int disGoodsId) {
        if (disGoodsId <= 0) {
            return null;
        }
        BusinessEntityExistenceLookup.GoodsIdLookupResult byId = lookup.lookupGoodsById(disGoodsId);
        if (byId.status() != com.nongxinle.ai.identity.EntityIdentityResolutionStatus.OK) {
            return null;
        }
        String canonicalName =
                StringUtils.hasText(byId.canonicalName()) ? byId.canonicalName().trim() : "";
        return EntityExistenceProbeResult.resolvedGoods(
                canonicalName, null, EntityExistence.UNIQUE);
    }

    public record EntityExistenceProbeResult(
            EntityExistenceOutcome outcome,
            String entityName,
            String llmEntityType,
            EntityExistence dishExistence,
            EntityExistence goodsExistence,
            String clarificationQuestion) {

        static EntityExistenceProbeResult resolvedDish(
                String entityName, EntityExistence dishExistence, EntityExistence goodsExistence) {
            return new EntityExistenceProbeResult(
                    EntityExistenceOutcome.DISH_UNIQUE,
                    entityName,
                    CoverDaysEntityType.DISH,
                    dishExistence,
                    goodsExistence,
                    null);
        }

        static EntityExistenceProbeResult resolvedGoods(
                String entityName, EntityExistence dishExistence, EntityExistence goodsExistence) {
            return new EntityExistenceProbeResult(
                    EntityExistenceOutcome.GOODS_UNIQUE,
                    entityName,
                    CoverDaysEntityType.GOODS,
                    dishExistence,
                    goodsExistence,
                    null);
        }

        static EntityExistenceProbeResult clarify(
                String question,
                String entityName,
                String llmEntityType,
                EntityExistence dishExistence,
                EntityExistence goodsExistence) {
            return new EntityExistenceProbeResult(
                    EntityExistenceOutcome.NEED_CLARIFICATION,
                    entityName,
                    llmEntityType,
                    dishExistence,
                    goodsExistence,
                    question);
        }
    }

    public enum EntityExistenceOutcome {
        DISH_UNIQUE,
        GOODS_UNIQUE,
        NEED_CLARIFICATION
    }
}
