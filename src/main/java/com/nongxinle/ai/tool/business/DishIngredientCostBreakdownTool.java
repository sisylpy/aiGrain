package com.nongxinle.ai.tool.business;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DISH_NAME_FOCUS_HINT;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DISH_PROFIT_STRUCTURED_DETAIL;

/**
 * D-13.3B：单菜原料成本明细；复用 {@link GbDishCostAnalysisService#buildIngredientRowsForFoodIds}，不新增 SQL、不重算核心分摊。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DishIngredientCostBreakdownTool implements AiTool {

    public static final String PRICE_SOURCE_OUTBOUND_TYPE1_AVG = "OUTBOUND_TYPE1_AVG";
    public static final String PRICE_SOURCE_ALLOCATED_WASTE_LOSS = "ALLOCATED_WASTE_LOSS";
    public static final String PRICE_SOURCE_MISSING_NO_REDUCE = "MISSING_NO_REDUCE";
    public static final String PRICE_SOURCE_UNKNOWN = "UNKNOWN";

    /** 商品规格与配方/出库计价尺度可能不一致；系统无自动换算时不要另编单价。 */
    public static final String WARN_UNIT_CONVERSION_MISSING = "UNIT_CONVERSION_MISSING";

    private final GbDishCostAnalysisService gbDishCostAnalysisService;
    private final GbDistributerFoodService gbDistributerFoodService;
    private final GbDistributerFoodGoodsService gbDistributerFoodGoodsService;

    @Override
    public String name() {
        return AiBusinessToolIds.DISH_INGREDIENT_COST_BREAKDOWN;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        DishProfitToolScopeSupport.BaseArgs base = DishProfitToolScopeSupport.parseBaseArgs(request);
        Map<String, Object> args = base.args();
        Long dept = base.departmentFatherId();
        Long disLong = base.disId();
        String start = base.startDate();
        String stop = base.stopDate();
        String dishFocus = str(args.get(ARG_DISH_NAME_FOCUS_HINT));
        String structuredDetail = str(args.get(ARG_DISH_PROFIT_STRUCTURED_DETAIL));

        if (base.missingRequired()) {
            Map<String, Object> data = baseDataPayload(null, null, start, stop, null, false,
                    "missing disId/departmentFatherId/dates", List.of(), List.of(), Map.of());
            return ToolResult.builder()
                    .success(false)
                    .message("missing_args")
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, disLong, data,
                            "参数不完整"))
                    .build();
        }

        DishProfitToolScopeSupport.ResolvedScope scope = DishProfitToolScopeSupport.resolveScope(disLong, dept, args);
        int disId = scope.disId();
        int depFatherIdInt = scope.depFatherIdInt();
        dept = scope.departmentFatherId();
        boolean groupWideAgg = scope.groupWideMendianAggregate();

        AiResolvedQueryContext rq = request.getResolvedQueryContext();
        String dishName = firstNonBlank(dishFocus, rq != null ? rq.getMentionedDishName() : null);
        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put("structuredIntentDetail", structuredDetail);
        debug.put("dishNameFocusHint", dishFocus);
        String scopeLabel = rq != null && rq.getQueryScopeBanner() != null ? rq.getQueryScopeBanner().trim() : null;
        if (scopeLabel != null) {
            debug.put("scopeLabelFromContext", scopeLabel);
        }
        debug.put("groupWideMendianAggregate", groupWideAgg);

        FoodResolveResult resolved = resolveTargetFoodId(disId, dishName, rq, debug);
        if (resolved.status == FoodResolveStatus.NEED_CLARIFICATION) {
            Map<String, Object> data = baseDataPayload(
                    dishName, null, start, stop, scopeLabel, false, "NEED_DISH_CLARIFICATION", List.of(), List.of(), debug);
            data.put("needClarification", true);
            data.put("candidates", resolved.candidates);
            return ToolResult.builder()
                    .success(true)
                    .message("need_clarification")
                    .data(AiBusinessToolResponses.envelope(name(), true, false, start, stop, dept, disLong, data,
                            "同名多菜需澄清"))
                    .build();
        }
        if (resolved.status == FoodResolveStatus.NOT_FOUND) {
            Map<String, Object> data =
                    baseDataPayload(dishName, null, start, stop, scopeLabel, false, "FOOD_NOT_FOUND", List.of(), List.of(), debug);
            return ToolResult.builder()
                    .success(true)
                    .message("food_not_found")
                    .data(AiBusinessToolResponses.envelope(name(), true, false, start, stop, dept, disLong, data,
                            "未找到菜品"))
                    .build();
        }

        int foodId = resolved.foodId;
        GbDistributerFoodEntity food = gbDistributerFoodService.queryObject(foodId);
        String resolvedDishName =
                food != null && StringUtils.hasText(food.getGbDfFoodName())
                        ? food.getGbDfFoodName().trim()
                        : (dishName != null ? dishName : "");

        List<GbDistributerFoodGoodsEntity> recipeLines = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(foodId);
        if (recipeLines == null) {
            recipeLines = List.of();
        }
        long activeRecipe =
                recipeLines.stream().filter(GbDepartmentGoodsStockReduceSupport::isActiveFoodGoodsLine).count();
        debug.put("recipeLineCount", recipeLines.size());
        debug.put("activeRecipeLineCount", activeRecipe);
        if (activeRecipe == 0) {
            Map<String, Object> data =
                    baseDataPayload(resolvedDishName, String.valueOf(foodId), start, stop, scopeLabel, false,
                            "RECIPE_NOT_FOUND", List.of(), List.of(), debug);
            return ToolResult.builder()
                    .success(true)
                    .message("recipe_not_found")
                    .data(AiBusinessToolResponses.envelope(name(), true, false, start, stop, dept, disLong, data,
                            "无有效配方行"))
                    .build();
        }

        debug.put(
                "recipeBusinessFieldsZh",
                "单份配方用量取自 gb_distributer_food_goods.gbDfgGoodsAmount（按 disGoodsId 合并后写入报表 recipeUnitPerDish）；"
                        + "出库分摊重量与 unitPrice（type1 生产出库均价）口径见 GbDishCostAnalysisServiceImpl 配料分析，多与「斤」等扣库汇总一致。");
        debug.put("recipeQuantityDbColumn", "gb_distributer_food_goods.gbDfgGoodsAmount");

        try {
            Set<Integer> idSet = Set.of(foodId);
            Map<Integer, List<Map<String, Object>>> byFood =
                    gbDishCostAnalysisService.buildIngredientRowsForFoodIds(start, stop, disId, depFatherIdInt, idSet);
            List<Map<String, Object>> rawRows =
                    byFood.getOrDefault(foodId, Collections.emptyList());
            debug.put("ingredientRowCount", rawRows.size());

            Map<String, Object> dishAgg = new LinkedHashMap<>();
            dishAgg.put("salesPortions", "0");
            if (!rawRows.isEmpty()) {
                BigDecimal q = BigDecimal.ZERO;
                for (Map<String, Object> ir : rawRows) {
                    BigDecimal app = portionQtyFromIngredientRow(ir);
                    if (app.compareTo(q) > 0) {
                        q = app;
                    }
                }
                if (q.compareTo(BigDecimal.ZERO) > 0) {
                    dishAgg.put("salesPortions", q.stripTrailingZeros().toPlainString());
                }
            }

            List<Map<String, Object>> outRows = new ArrayList<>();
            List<Map<String, Object>> missing = new ArrayList<>();
            BigDecimal totalCost = BigDecimal.ZERO;
            BigDecimal sold = GbDepartmentGoodsStockReduceSupport.coerceDecimal(dishAgg.get("salesPortions"));
            int unitConvWarn = 0;
            List<String> gapHints = new ArrayList<>();

            for (Map<String, Object> ir : rawRows) {
                PriceSourceTag tag = classifyPriceSource(ir);
                Map<String, Object> line = mapOutputRow(ir, sold, tag, missing);
                String w = str(line.get("warning"));
                if (w.contains(WARN_UNIT_CONVERSION_MISSING)) {
                    unitConvWarn++;
                }
                outRows.add(line);
                BigDecimal lineTotal = GbDepartmentGoodsStockReduceSupport.coerceDecimal(line.get("totalCost"));
                totalCost = totalCost.add(lineTotal);
                noteRowFieldGaps(ir, gapHints);
            }
            if (unitConvWarn > 0) {
                debug.put("unitConversionWarningCount", unitConvWarn);
                debug.put(
                        "unitConversionNoteZh",
                        "部分行打了 "
                                + WARN_UNIT_CONVERSION_MISSING
                                + "：规格/配方与出库计价单位是否一致需人工核对；金额仍使用 GbDishCostAnalysisServiceImpl 已算结果，未在 Tool 内另编单价或强行换算。");
            }
            if (!gapHints.isEmpty()) {
                debug.put("ingredientServiceRowFieldGaps", gapHints.stream().distinct().limit(12).toList());
            }

            if (sold.compareTo(BigDecimal.ZERO) > 0 && totalCost.compareTo(BigDecimal.ZERO) > 0) {
                for (Map<String, Object> line : outRows) {
                    BigDecimal tc = GbDepartmentGoodsStockReduceSupport.coerceDecimal(line.get("totalCost"));
                    BigDecimal ratio =
                            tc.multiply(new BigDecimal("100"))
                                    .divide(totalCost, 2, RoundingMode.HALF_UP);
                    line.put("costRatio", ratio.stripTrailingZeros().toPlainString() + "%");
                }
            } else {
                for (Map<String, Object> line : outRows) {
                    line.put("costRatio", null);
                }
            }

            boolean hasDetail = !outRows.isEmpty();
            String totalStr =
                    totalCost.compareTo(BigDecimal.ZERO) > 0
                            ? totalCost.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                            : null;
            debug.put("totalIngredientCostComputed", totalStr);
            Map<String, Object> data =
                    baseDataPayload(resolvedDishName, String.valueOf(foodId), start, stop, scopeLabel, hasDetail,
                            hasDetail ? null : "EMPTY_INGREDIENT_ROWS", outRows, missing, debug);
            data.put("totalIngredientCost", totalStr);
            data.put("missingPriceItems", missing);
            data.put("ingredientRows", outRows);
            data.put("ingredientBreakdownAvailable", hasDetail);
            data.put("ingredientBreakdownUnavailableReason", hasDetail ? null : "EMPTY_INGREDIENT_ROWS");

            if (!missing.isEmpty()) {
                debug.put("priceMissingCount", missing.size());
            }

            return ToolResult.builder()
                    .success(true)
                    .message("ok")
                    .data(AiBusinessToolResponses.envelope(name(), true, rawRows.isEmpty(), start, stop, dept, disLong,
                            data, rawRows.isEmpty() ? "配料行为空（区间核销或销量口径）" : null))
                    .build();
        } catch (Exception e) {
            log.warn("[DishIngredientCostBreakdownTool] runId={}: {}", request.getRunId(), e.toString());
            Map<String, Object> data =
                    baseDataPayload(resolvedDishName, String.valueOf(foodId), start, stop, scopeLabel, false,
                            "QUERY_ERROR", List.of(), List.of(), debug);
            debug.put("error", e.getMessage());
            return ToolResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .data(AiBusinessToolResponses.envelope(name(), false, true, start, stop, dept, disLong, data,
                            "查询异常"))
                    .build();
        }
    }

    private enum FoodResolveStatus {
        OK, NOT_FOUND, NEED_CLARIFICATION
    }

    private record FoodResolveResult(FoodResolveStatus status, int foodId, List<Map<String, Object>> candidates) {
        FoodResolveResult(FoodResolveStatus s, int id) {
            this(s, id, List.of());
        }
    }

    private FoodResolveResult resolveTargetFoodId(
            int disId, String dishNameHint, AiResolvedQueryContext rq, LinkedHashMap<String, Object> debug) {
        Integer fromAnchor = parsePreferredFoodIdFromAnchors(dishNameHint, rq, debug);
        if (fromAnchor != null && fromAnchor > 0) {
            GbDistributerFoodEntity f = verifyFoodInDistributor(fromAnchor, disId, debug);
            if (f != null) {
                return new FoodResolveResult(FoodResolveStatus.OK, fromAnchor);
            }
            debug.put("anchorFoodIdRejected", fromAnchor);
        }

        if (!StringUtils.hasText(dishNameHint)) {
            debug.put("resolveStage", "no_name_no_anchor");
            return new FoodResolveResult(FoodResolveStatus.NOT_FOUND, -1);
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("disId", disId);
        map.put("foodName", dishNameHint.trim());
        List<GbDistributerFoodEntity> foods = gbDistributerFoodService.queryFoodByParams(map);
        if (foods == null || foods.isEmpty()) {
            debug.put("resolveStage", "queryFoodByParams_empty");
            return new FoodResolveResult(FoodResolveStatus.NOT_FOUND, -1);
        }
        LinkedHashSet<Integer> ids =
                foods.stream()
                        .map(GbDistributerFoodEntity::getGbDistributerFoodId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.size() > 1) {
            List<Map<String, Object>> cands = new ArrayList<>();
            for (Integer id : ids) {
                GbDistributerFoodEntity e = foods.stream()
                        .filter(x -> id.equals(x.getGbDistributerFoodId()))
                        .findFirst()
                        .orElse(null);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("dishId", id);
                m.put(
                        "dishName",
                        e != null && e.getGbDfFoodName() != null ? e.getGbDfFoodName().trim() : "");
                cands.add(m);
            }
            debug.put("resolveStage", "ambiguous_name");
            debug.put("candidates", cands);
            return new FoodResolveResult(FoodResolveStatus.NEED_CLARIFICATION, -1, cands);
        }
        int only = ids.iterator().next();
        return new FoodResolveResult(FoodResolveStatus.OK, only);
    }

    private GbDistributerFoodEntity verifyFoodInDistributor(int foodId, int disId, LinkedHashMap<String, Object> dbg) {
        GbDistributerFoodEntity f = gbDistributerFoodService.queryObject(foodId);
        if (f == null) {
            return null;
        }
        if (f.getGbDfDistributerId() != null && !Integer.valueOf(disId).equals(f.getGbDfDistributerId())) {
            dbg.put("anchorFoodDisIdMismatch", f.getGbDfDistributerId());
            return null;
        }
        return f;
    }

    private Integer parsePreferredFoodIdFromAnchors(
            String dishNameHint, AiResolvedQueryContext rq, LinkedHashMap<String, Object> debug) {
        if (rq == null) {
            return null;
        }
        AiConversationTurnMemory pt = rq.getPreviousTurn();
        if (pt == null || pt.getLastResultAnchors() == null || pt.getLastResultAnchors().isEmpty()) {
            return null;
        }
        List<AiResultAnchor> dishAnchors =
                pt.getLastResultAnchors().stream()
                        .filter(a -> AiResultAnchor.ENTITY_TYPE_DISH.equals(a.getEntityType()))
                        .collect(Collectors.toList());
        if (dishAnchors.isEmpty()) {
            return null;
        }
        if (dishAnchors.size() == 1) {
            return extractFoodIdFromAnchor(dishAnchors.get(0), debug, "single_dish_anchor");
        }
        if (!StringUtils.hasText(dishNameHint)) {
            debug.put("anchorConflict", "multi_dish_anchor_no_name");
            return null;
        }
        String hint = dishNameHint.trim();
        for (AiResultAnchor a : dishAnchors) {
            String en = a.getEntityName();
            if (en != null && (hint.equals(en.trim()) || hint.contains(en.trim()) || en.contains(hint))) {
                Integer id = extractFoodIdFromAnchor(a, debug, "anchor_matched_name");
                if (id != null) {
                    return id;
                }
            }
        }
        debug.put("anchorConflict", "multi_dish_anchor_unmatched_name");
        return null;
    }

    private static Integer extractFoodIdFromAnchor(AiResultAnchor a, LinkedHashMap<String, Object> debug, String tag) {
        Integer id = tryParseInt(a.getEntityId());
        if (id != null && id > 0) {
            debug.put("resolvedFoodIdSource", tag + "_entityId");
            return id;
        }
        if (!StringUtils.hasText(a.getExtraJson())) {
            return null;
        }
        try {
            Map<String, Object> ex =
                    JSON.parseObject(a.getExtraJson(), new TypeReference<>() {
                    });
            if (ex != null && ex.get("foodId") != null) {
                Integer id2 = tryParseInt(ex.get("foodId").toString());
                if (id2 != null && id2 > 0) {
                    debug.put("resolvedFoodIdSource", tag + "_extraJson");
                    return id2;
                }
            }
        } catch (Exception e) {
            debug.put("anchorExtraJsonParseError", e.getMessage());
        }
        return null;
    }

    private static Integer tryParseInt(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> baseDataPayload(
            String dishName,
            String dishId,
            String start,
            String stop,
            String scopeLabel,
            boolean available,
            String unavailableReason,
            List<Map<String, Object>> rows,
            List<Map<String, Object>> missing,
            Map<String, Object> debug) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dishName", dishName);
        data.put("dishId", dishId);
        data.put("timeRange", start + ".." + stop);
        data.put("scopeLabel", scopeLabel);
        data.put("ingredientBreakdownAvailable", available);
        data.put("ingredientBreakdownUnavailableReason", unavailableReason);
        data.put("ingredientRows", rows);
        data.put("missingPriceItems", missing);
        data.put("debug", debug);
        return data;
    }

    private static BigDecimal portionQtyFromIngredientRow(Map<String, Object> ir) {
        BigDecimal q =
                inferSoldPortionsFromAllocatedPerPortion(
                        ir.get("allocatedOutboundPerSoldPortion"), ir.get("actualUsage"));
        if (q.compareTo(BigDecimal.ZERO) > 0) {
            return q;
        }
        return inferSoldPortionsFromAllocatedPerPortion(
                ir.get("produceAllocatedPerSoldPortion"), ir.get("actualProduceUsage"));
    }

    private static BigDecimal inferSoldPortionsFromAllocatedPerPortion(Object perPortionRaw, Object numerRaw) {
        BigDecimal per = BigDecimal.ZERO;
        if (perPortionRaw != null && StringUtils.hasText(perPortionRaw.toString())) {
            try {
                per = new BigDecimal(perPortionRaw.toString().trim());
            } catch (Exception ignore) {
                per = BigDecimal.ZERO;
            }
        }
        if (per.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal num = GbDepartmentGoodsStockReduceSupport.coerceDecimal(numerRaw);
        if (num.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return num.divide(per, 4, RoundingMode.HALF_UP);
    }

    private record PriceSourceTag(String priceSource, String warning) {
    }

    private static PriceSourceTag classifyPriceSource(Map<String, Object> ir) {
        BigDecimal act = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("actualCostPerPortion"));
        BigDecimal prod = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("produceCostPerPortion"));
        BigDecimal waste = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("wasteCostPerPortion"));
        BigDecimal loss = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("lossCostPerPortion"));

        if (act.compareTo(BigDecimal.ZERO) <= 0) {
            String w = "本期未能摊入出库成本（无有效 reduce/销量或未出库）";
            return new PriceSourceTag(PRICE_SOURCE_MISSING_NO_REDUCE, w);
        }
        if (prod.compareTo(BigDecimal.ZERO) > 0) {
            return new PriceSourceTag(PRICE_SOURCE_OUTBOUND_TYPE1_AVG, null);
        }
        if (waste.add(loss).compareTo(BigDecimal.ZERO) > 0) {
            String w = "主要由损耗/损失出库分摊，非生产出库均价主因";
            return new PriceSourceTag(PRICE_SOURCE_ALLOCATED_WASTE_LOSS, w);
        }
        return new PriceSourceTag(PRICE_SOURCE_UNKNOWN, "成本来源未分类");
    }

    private static void noteRowFieldGaps(Map<String, Object> ir, List<String> gapHints) {
        if (ir.get("recipeUnitPerDish") == null || !StringUtils.hasText(String.valueOf(ir.get("recipeUnitPerDish")).trim())) {
            gapHints.add("missing_recipeUnitPerDish");
        }
        if (ir.get("unitPrice") == null || !StringUtils.hasText(String.valueOf(ir.get("unitPrice")).trim())) {
            gapHints.add("missing_unitPrice_for_" + ir.get("disGoodsId"));
        }
    }

    /**
     * 规格文案与「斤」类出库口径并存时提示风险；不修改 Service 已算金额。
     */
    private static String unitConversionWarningMessage(Map<String, Object> ir) {
        String std = str(ir.get("gbDgGoodsStandardname"));
        if (!StringUtils.hasText(std)) {
            return null;
        }
        String s = std.trim();
        boolean specGramLike =
                s.contains("克")
                        || s.contains("千克")
                        || s.contains("公斤")
                        || s.matches("(?i).*[0-9]+\\s*(g|gram|grams)\\b.*");
        if (!specGramLike) {
            return null;
        }
        // 配料分析出库重量语义多为「斤」；若主档规格明确写克/g，则可能存在未自动换算风险。
        if (isWeightGoods(ir)) {
            return WARN_UNIT_CONVERSION_MISSING
                    + "：gb_distributer_goods 规格名含克/g 等，而配料分析 recipeUnitPerDish 与生产出库重量常与「斤」同口径衔接；"
                    + "系统未在 Tool 层做克↔斤换算，下图金额仍沿用 GbDishCostAnalysisServiceImpl 输出，请核对主数据是否已统一单位。";
        }
        return WARN_UNIT_CONVERSION_MISSING
                + "：规格名含克/g 等，与成本分量口径是否一致请人工核对；未在 Tool 层硬算换算。";
    }

    private static boolean isWeightGoods(Map<String, Object> ir) {
        Object w = ir.get("gbDgGoodsIsWeight");
        return "1".equals(str(w)) || Boolean.TRUE.equals(w);
    }

    /**
     * 与 {@link GbDishCostAnalysisServiceImpl} 配料行一致：称重品配方用量按「斤」理解与出库汇总衔接；非称重品标注为计数单位。
     */
    private static String deriveRecipeUnit(Map<String, Object> ir) {
        if (isWeightGoods(ir)) {
            return "斤";
        }
        return "份";
    }

    private Map<String, Object> mapOutputRow(
            Map<String, Object> ir, BigDecimal soldPortions, PriceSourceTag tag, List<Map<String, Object>> missing) {
        Map<String, Object> line = new LinkedHashMap<>();
        Object nm = ir.get("gbDgGoodsName");
        line.put("ingredientName", nm == null ? "" : nm.toString().trim());
        line.put("goodsId", ir.get("disGoodsId"));

        line.put("recipeQuantityPerDish", ir.get("recipeUnitPerDish"));
        String recipeUnit = deriveRecipeUnit(ir);
        line.put("recipeUnit", recipeUnit);
        line.put("unit", recipeUnit);

        line.put("soldPortions", soldPortions.compareTo(BigDecimal.ZERO) > 0 ? soldPortions.stripTrailingZeros().toPlainString() : null);
        line.put("theoryUsage", ir.get("theoryUsage"));
        line.put("actualUsage", ir.get("actualUsage"));
        line.put("utilizationRate", ir.get("utilizationRate"));

        String ucWarn = unitConversionWarningMessage(ir);
        String mergedWarn = tag.warning;
        if (ucWarn != null) {
            mergedWarn = mergedWarn == null || mergedWarn.isBlank() ? ucWarn : ucWarn + " " + mergedWarn;
        }

        BigDecimal costPerDish = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("actualCostPerPortion"));
        BigDecimal unitPriceBd = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("unitPrice"));
        line.put(
                "unitCostExplainZh",
                "unitPrice 为配料分析中该料 type1（生产）出库「金额÷重量」均价，分量口径与库存扣减汇总一致（多为斤），不是最近一次采购价；"
                        + "costPerDish=actualCostPerPortion 为该料摊到每份菜的总成本（含 type1+2+3）。");
        line.put("unitCost", ir.get("unitPrice"));
        line.put(
                "unitCostUomZh",
                isWeightGoods(ir) ? "元/斤（与生产出库重量口径一致）" : "元/单位（与生产出库计价口径一致，详见报表）");
        line.put("costPerDish", ir.get("actualCostPerPortion"));

        BigDecimal tot =
                soldPortions.compareTo(BigDecimal.ZERO) > 0
                        ? costPerDish.multiply(soldPortions).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
        line.put("totalCost", tot.compareTo(BigDecimal.ZERO) > 0 ? tot.stripTrailingZeros().toPlainString() : null);

        line.put("priceSource", tag.priceSource);
        line.put("warning", mergedWarn);
        line.put("produceCostPerPortion", ir.get("produceCostPerPortion"));
        line.put("wasteCostPerPortion", ir.get("wasteCostPerPortion"));
        line.put("lossCostPerPortion", ir.get("lossCostPerPortion"));

        if (PRICE_SOURCE_MISSING_NO_REDUCE.equals(tag.priceSource)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("goodsId", ir.get("disGoodsId"));
            m.put("ingredientName", line.get("ingredientName"));
            m.put("reason", "MISSING_PRICE_OR_REDUCE");
            missing.add(m);
        }
        return line;
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a.trim();
        }
        if (StringUtils.hasText(b)) {
            return b.trim();
        }
        return "";
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
