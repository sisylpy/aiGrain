package com.nongxinle.ai.semantic.intake.grounding;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.intake.SemanticIntakeDishIngredientCoverDaysSupport;
import com.nongxinle.ai.semantic.intake.SemanticIntakeGoodsAnchorInventoryBundleSupport;
import com.nongxinle.ai.semantic.intake.SemanticIntakeGoodsStockBatchDetailSupport;
import com.nongxinle.ai.semantic.intake.SemanticIntakeGoodsSupportedDishCoverSupport;
import com.nongxinle.ai.semantic.intake.SemanticIntakeInput;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Cover days Intake 协议与信号检测；存在性落地主链已迁入 {@link EntityExistenceGroundingService}。
 */
@Service
@RequiredArgsConstructor
public class CoverDaysEntityGroundingService {

    /** cover-days 销量基线修饰（Intake §34d）；须与 goods/dish cover reason 并列。 */
    public static final String SALES_BASELINE_REASON_MARKER = "cover_days_sales_baseline";

    private final EntityExistenceGroundingService entityExistenceGroundingService;

    /** Intake 已输出 cover days 实体名时，跳过 reason-marker 域 reconcile，交给存在性落地。 */
    public static boolean hasCoverDaysEntityGroundingSignals(SemanticIntakeResult intake) {
        return intake != null && StringUtils.hasText(intake.getCoverDaysEntityName());
    }

    /**
     * 当前轮 Intake 已点名非 DISH 实体（原料/商品 anchor）；用于 WH-H 与 WH-I 互斥门禁。
     */
    public static boolean intakeDeclaresNamedGoodsEntityAnchor(SemanticIntakeResult intake) {
        if (!hasCoverDaysEntityGroundingSignals(intake)) {
            return false;
        }
        return !CoverDaysEntityType.DISH.equals(CoverDaysEntityType.normalize(intake.getCoverDaysEntityType()));
    }

    /** 已锁定任一 GOODS 锚点库存详情合同族（bundle / WH-H / WH-J）。 */
    public static boolean intakeDeclaresNamedGoodsInventoryDetail(SemanticIntakeResult intake) {
        if (intake == null) {
            return false;
        }
        return intakeDeclaresNamedGoodsEntityAnchor(intake)
                || SemanticIntakeGoodsSupportedDishCoverSupport.intakeDeclaresGoodsSupportedDishCover(
                        intake)
                || SemanticIntakeGoodsAnchorInventoryBundleSupport.intakeDeclaresGoodsAnchorInventoryBundle(
                        intake)
                || SemanticIntakeGoodsStockBatchDetailSupport.intakeDeclaresGoodsStockBatchDetail(
                        intake);
    }

    /** Parsed 层：cover-days 语义（实体名 alone 不算 cover-days）。 */
    public static boolean signalsCoverDaysParsed(LlmSemanticIntakeParsed parsed) {
        if (parsed == null) {
            return false;
        }
        return parsedSignalsCoverDaysSemantics(parsed);
    }

    /** Intake 层：cover-days 能力信号；销量基线 marker 仅为观测字段，不能单独决定能力。 */
    public static boolean intakeSignalsCoverDaysSemantics(SemanticIntakeResult intake) {
        if (intake == null) {
            return false;
        }
        return SemanticIntakeDishIngredientCoverDaysSupport.intakeDeclaresDishIngredientCoverDays(intake)
                || SemanticIntakeGoodsSupportedDishCoverSupport.intakeDeclaresGoodsSupportedDishCover(intake);
    }

    public static boolean parsedSignalsCoverDaysSemantics(LlmSemanticIntakeParsed parsed) {
        if (parsed == null) {
            return false;
        }
        return SemanticIntakeDishIngredientCoverDaysSupport.parsedDeclaresDishIngredientCoverDays(parsed)
                || SemanticIntakeGoodsSupportedDishCoverSupport.parsedDeclaresGoodsSupportedDishCover(parsed);
    }

    public static boolean intakeDeclaresCoverDaysSalesBaseline(String reason) {
        return parsedDeclaresCoverDaysSalesBaseline(reason);
    }

    public static boolean parsedDeclaresCoverDaysSalesBaseline(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        return reason.trim().toLowerCase(java.util.Locale.ROOT).contains(SALES_BASELINE_REASON_MARKER);
    }

    /** cover days 问法必须输出实体名；实体类型可选，缺失时由存在性落地。 */
    public static void collectCoverDaysEntityProtocolErrors(
            LlmSemanticIntakeParsed parsed, List<String> errors) {
        if (parsed == null || errors == null) {
            return;
        }
        boolean dishCover = SemanticIntakeDishIngredientCoverDaysSupport.parsedDeclaresDishIngredientCoverDays(parsed);
        boolean goodsCover = SemanticIntakeGoodsSupportedDishCoverSupport.parsedDeclaresGoodsSupportedDishCover(parsed);
        boolean salesBaseline = parsedDeclaresCoverDaysSalesBaseline(parsed.getReason());
        boolean hasEntityName = StringUtils.hasText(parsed.getCoverDaysEntityName());
        if (!dishCover && !goodsCover && !salesBaseline) {
            return;
        }
        if ("DISH_SALES".equals(parsed.getPrimaryDomain())) {
            errors.add(
                    "cover_days: primaryDomain must not be DISH_SALES; use WAREHOUSE or DISH_COST with "
                            + "coverDaysEntityName (§34d)");
        }
        String reason = parsed.getReason();
        if (hasEntityName
                && StringUtils.hasText(reason)
                && reason.trim().toLowerCase(java.util.Locale.ROOT).contains("named_dish_sales")) {
            errors.add(
                    "coverDaysEntityName: cannot combine with named_dish_sales reason; use "
                            + "goods_supported_dish_cover or dish_ingredient_cover_days (§34d)");
        }
        if ((dishCover || goodsCover) && !hasEntityName) {
            errors.add("coverDaysEntityName: required for cover days queries (§34a/§34b/§34d)");
        }
    }

    /** @deprecated 使用 {@link EntityExistenceGroundingService#reconcileIntake} */
    @Deprecated
    public SemanticIntakeResult reconcileIntake(SemanticIntakeInput input, SemanticIntakeResult intake) {
        return entityExistenceGroundingService.reconcileIntake(input, intake);
    }

    static boolean signalsCoverDaysQuery(AiQuerySemanticParseResult sem, SemanticIntakeResult intake) {
        if (intake != null
                && intakeSignalsCoverDaysSemantics(intake)
                && StringUtils.hasText(intake.getCoverDaysEntityName())) {
            return true;
        }
        String selected = SemanticContractCompletionEngine.extractSelectedContractId(sem);
        if (DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS.equals(
                        blank(selected))
                || WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_SUPPORTED_DISH_COVER.equals(
                        blank(selected))
                || WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_ANCHOR_INVENTORY_BUNDLE.equals(
                        blank(selected))) {
            return true;
        }
        String wire = resolveStructuredWire(sem);
        return AiQuerySemanticLexicon.isStructuredDishIngredientCoverDaysDetail(wire)
                || AiQuerySemanticLexicon.STRUCTURED_GOODS_SUPPORTED_DISH_COVER.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_GOODS_ANCHOR_INVENTORY_BUNDLE.equals(wire);
    }

    static String resolveLlmEntityType(AiQuerySemanticParseResult sem, SemanticIntakeResult intake) {
        if (intake != null) {
            if (StringUtils.hasText(intake.getCoverDaysEntityName())) {
                return CoverDaysEntityType.resolveForGrounding(intake.getCoverDaysEntityType());
            }
            String fromIntake = CoverDaysEntityType.normalize(intake.getCoverDaysEntityType());
            if (fromIntake != null) {
                return fromIntake;
            }
        }
        if (sem != null && sem.getSemanticSlots() != null) {
            String qo = CoverDaysEntityType.normalize(sem.getSemanticSlots().getQueryObject());
            if (qo != null) {
                return qo;
            }
        }
        String dish = sem != null ? sem.effectiveMentionedDishName() : null;
        String goods = sem != null ? sem.effectiveMentionedGoodsName() : null;
        if (StringUtils.hasText(goods) && !StringUtils.hasText(dish)) {
            return CoverDaysEntityType.GOODS;
        }
        if (StringUtils.hasText(dish) && !StringUtils.hasText(goods)) {
            return CoverDaysEntityType.DISH;
        }
        String selected = sem != null ? SemanticContractCompletionEngine.extractSelectedContractId(sem) : null;
        if (WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_SUPPORTED_DISH_COVER.equals(blank(selected))) {
            return CoverDaysEntityType.GOODS;
        }
        if (WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_ANCHOR_INVENTORY_BUNDLE.equals(
                blank(selected))) {
            return CoverDaysEntityType.GOODS;
        }
        if (WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_STOCK_BATCH_DETAIL.equals(blank(selected))) {
            return CoverDaysEntityType.GOODS;
        }
        if (DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS.equals(blank(selected))) {
            return CoverDaysEntityType.DISH;
        }
        return null;
    }

    static String resolveEntityName(AiQuerySemanticParseResult sem, SemanticIntakeResult intake) {
        if (intake != null && StringUtils.hasText(intake.getCoverDaysEntityName())) {
            return intake.getCoverDaysEntityName().trim();
        }
        if (sem == null) {
            return null;
        }
        String dish = sem.effectiveMentionedDishName();
        if (StringUtils.hasText(dish)) {
            return dish.trim();
        }
        String goods = sem.effectiveMentionedGoodsName();
        if (StringUtils.hasText(goods)) {
            return goods.trim();
        }
        return null;
    }

    private static String resolveStructuredWire(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return null;
        }
        if (sem.getSemanticSlots() != null
                && StringUtils.hasText(sem.getSemanticSlots().getStructuredIntentDetailWire())) {
            return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                    sem.getSemanticSlots().getStructuredIntentDetailWire());
        }
        return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                sem.getCurrentTurnStructuredIntentDetailWire());
    }

    private static String blank(String s) {
        return s == null ? null : s.trim();
    }

}
