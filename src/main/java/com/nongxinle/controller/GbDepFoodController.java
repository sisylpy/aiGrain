package com.nongxinle.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.nongxinle.entity.*;
import com.nongxinle.dto.GbDepFoodDailySalesQueryRequest;
import com.nongxinle.dto.GbDepFoodDailySalesSubmitRequest;
import com.nongxinle.service.GbDepFoodBusinessInsightService;
import com.nongxinle.service.GbDepFoodSalesExcelImportService;
import com.nongxinle.service.GbDepFoodService;
import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.nongxinle.utils.R;

@Slf4j
@RestController
@RequestMapping("/gbdepfood")
public class GbDepFoodController {
	@Autowired
	private GbDepFoodService gbDepFoodService;
	@Autowired
	private GbDistributerFoodService gbDistributerFoodService;
	@Autowired
	private GbDistributerFoodGoodsService gbDistributerFoodGoodsService;
	@Autowired
	private GbDistributerGoodsService gbDistributerGoodsService;
	@Autowired
	private GbDepFoodBusinessInsightService gbDepFoodBusinessInsightService;
	@Autowired
	private GbDishCostAnalysisService gbDishCostAnalysisService;
	@Autowired
	private GbDepFoodSalesExcelImportService gbDepFoodSalesExcelImportService;

	/**
	 * 提交某日菜品销量（份数）及日营业额指标：写 {@code gb_dep_food_sales} / 原料展开，并按菜品小计更新 {@code gb_ai_daily_revenue} 堂食与其它字段。
	 */
	@RequestMapping(value = "/submitDailyFoodSalesAndRevenue", method = RequestMethod.POST)
	@ResponseBody
	public R submitDailyFoodSalesAndRevenue(@RequestBody GbDepFoodDailySalesSubmitRequest body) {
		try {
			Map<String, Object> data = gbDepFoodSalesExcelImportService.submitDailyFoodSalesAndRevenue(body);
			return R.ok().put("data", data);
		} catch (IllegalArgumentException e) {
			return R.error(-1, e.getMessage());
		} catch (Exception e) {
			log.warn("submitDailyFoodSalesAndRevenue failed", e);
			return R.error(-1, e.getMessage());
		}
	}

	/**
	 * 获取某日菜品销售 + 同日营业额表单字段；{@code recordDate} 不传则为中国时区当天（“今日”）。
	 * 请求体为 JSON（与微信小程序等客户端一致）；{@code data.submitShape} 与 {@link GbDepFoodDailySalesSubmitRequest} 一致。
	 */
	@RequestMapping(value = "/getDailyFoodSalesAndRevenue", method = RequestMethod.POST)
	@ResponseBody
	public R getDailyFoodSalesAndRevenue(@RequestBody GbDepFoodDailySalesQueryRequest body) {
		try {
			if (body == null) {
				return R.error(-1, "请求体不能为空");
			}
			Map<String, Object> data = gbDepFoodSalesExcelImportService.getDailyFoodSalesAndRevenue(
					body.getDepFatherId(), body.getDistributerId(), body.getRecordDate());
			return R.ok().put("data", data);
		} catch (IllegalArgumentException e) {
			return R.error(-1, e.getMessage());
		} catch (Exception e) {
			log.warn("getDailyFoodSalesAndRevenue failed", e);
			return R.error(-1, e.getMessage());
		}
	}

	/** 更新某日菜品销售与营业额指标；请求体与 {@code /submitDailyFoodSalesAndRevenue} 相同（upsert 覆盖）。 */
	@RequestMapping(value = "/updateDailyFoodSalesAndRevenue", method = RequestMethod.POST)
	@ResponseBody
	public R updateDailyFoodSalesAndRevenue(@RequestBody GbDepFoodDailySalesSubmitRequest body) {
		try {
			Map<String, Object> data = gbDepFoodSalesExcelImportService.updateDailyFoodSalesAndRevenue(body);
			return R.ok().put("data", data);
		} catch (IllegalArgumentException e) {
			return R.error(-1, e.getMessage());
		} catch (Exception e) {
			log.warn("updateDailyFoodSalesAndRevenue failed", e);
			return R.error(-1, e.getMessage());
		}
	}

	/**
	 * 部门菜品列表：配方、主档商品、可选日期内销量。
	 * <p>当 {@code startDate}、{@code stopDate}、{@code disId}、{@code depFatherId} 齐全时：在每条 {@code GbDepFoodEntity} 上填充 {@code gbDfBusinessInsight}
	 *（周销量 0=周日、标价收入、type=1 实际/理论成本、{@code grossMarginRateOnListPrice} = (标价收入−type1 实际成本)÷标价收入；
	 * {@code actualCostPerPortion123}、{@code actualCostTotalAmount123}（单份 type1+2+3 实际成本×本行实销份数，与配料分析整菜金额口径一致）、{@code blendedGrossMarginRateOnListPrice} = 部门标价下（标价−type1+2+3 单份实际成本）÷标价，与配料分析整菜 {@code actualCostPerPortion} 同口径；与 {@code wasteLossRatioInOutbound123} 区间损耗率并列），
	 * 本响应同时带上 {@code businessInsightSummary}（含 {@code comprehensiveGrossMarginRateOnListPrice}：列表标价收入合计相对区间 1+2+3 出库总成本、及仅 type1 的 blended 毛利率等）、{@code scopeOutboundSubtotals}、{@code weekdayLegend}、{@code bossColumnHintsZh} 等；
	 * 有销量时的配方行 {@code gbDistributerFoodEntity.gbdisFoodGoodsEntities} 另挂本区间出库价、type1 制作量/额、2+3 量/差分额、1+2+3 量/额。
	 * {@code gbDfSalesAmount} 与经营分析总销量（子部门口径）对齐。缺参时 {@code gbDfSalesAmount} 为 {@code "0"}，且不填 {@code gbDfBusinessInsight}。</p>
	 * <p>流程：四参齐全时先 {@code attachToFoodRows}，再剔除 {@code gb_df_status=1}（与 {@link GbConstants.DistributerFoodStatus} 中停用取值一致）且本区间销量为 0 的部门菜；
	 * 再批量生成与 {@code /gbDishCostAnalysis/ingredientAnalysis} 中 {@code salesDishRows[].ingredientRows} 同结构的 {@code ingredientAnalysisRows}；
	 * ①加载批发商主档与<strong>有效配方行</strong>（同配料分析：{@code gb_dfg_status≠0}）；②③④ 收集 {@code gbDfgDisGoodsId}、批量主档、挂回配方行；最后按 {@code sortBy}/{@code sortOrder} 排序（仅四参齐全时），默认 {@code gbDfSalesAmount} 降序。未传齐四参时不填 {@code ingredientAnalysisRows}、配方仍全量（与旧版一致）。</p>
	 *
	 * @param sortBy 仅四参齐全时生效：{@code gbDfSalesAmount|sales|salesAmount|销量|份数} 销售份数；{@code blendedGrossMarginRateOnListPrice|margin|毛利率} 综合毛利率（%）；{@code actualProfit|profit|实际利润} {@code listPriceRevenue − actualCostTotalAmount123}。空则同 {@code gbDfSalesAmount}。
	 * @param sortOrder {@code desc|降序}（默认）、{@code asc|升序}。
	 */
	@RequestMapping(value = "/depGetAllFood", method = RequestMethod.POST)
	@ResponseBody
	public R depGetAllFood(Integer disId, Integer depFatherId, String startDate, String stopDate, String sortBy,
			String sortOrder) {
		Map<String, Object> map = new HashMap<>();
		map.put("disId", disId);
		map.put("depFatherId", depFatherId);
		List<GbDepFoodEntity> foodEntities = gbDepFoodService.queryDepAllFood(map);

		boolean hasDateRange = startDate != null && !startDate.trim().isEmpty()
				&& stopDate != null && !stopDate.trim().isEmpty();
		boolean useBusinessInsight = hasDateRange && disId != null && depFatherId != null;

		R r = R.ok().put("data", foodEntities);
		Map<String, Object> businessExtras = null;
		if (useBusinessInsight) {
			try {
				businessExtras = gbDepFoodBusinessInsightService.attachToFoodRows(
						foodEntities, disId, depFatherId, startDate.trim(), stopDate.trim());
			} catch (IllegalArgumentException e) {
				return R.error(-1, e.getMessage());
			}
			foodEntities.removeIf(food -> GbConstants.DistributerFoodStatus.DISABLED_WITH_DEP_FOOD_SALES.equals(food.getGbDfStatus())
					&& !hasPositiveSalesInPeriod(food));
		} else {
			for (GbDepFoodEntity food : foodEntities) {
				food.setGbDfSalesAmount(BigDecimal.ZERO.stripTrailingZeros().toPlainString());
			}
		}

		Map<Integer, List<Map<String, Object>>> ingredientRowsByFoodId = new HashMap<>();
		if (useBusinessInsight) {
			Set<Integer> disFoodIds = new HashSet<>();
			for (GbDepFoodEntity food : foodEntities) {
				if (food.getGbDfFoodId() != null) {
					disFoodIds.add(food.getGbDfFoodId());
				}
			}
			if (!disFoodIds.isEmpty()) {
				try {
					ingredientRowsByFoodId = gbDishCostAnalysisService.buildIngredientRowsForFoodIds(
							startDate.trim(), stopDate.trim(), disId, depFatherId, disFoodIds);
				} catch (IllegalArgumentException e) {
					return R.error(-1, e.getMessage());
				}
			}
		}

		// ① 分销商菜品主档 + 有效配方行（与 ingredientAnalysis 一致）；② 同结构的 ingredientAnalysisRows
		for (GbDepFoodEntity food : foodEntities) {
			Integer foodId = food.getGbDfFoodId();
			if (foodId != null) {
				GbDistributerFoodEntity disFood = gbDistributerFoodService.queryObject(foodId);
				if (disFood != null) {
					List<GbDistributerFoodGoodsEntity> recipeRaw = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(foodId);
					disFood.setGbdisFoodGoodsEntities(recipeLinesActiveOnly(recipeRaw));
				}
				food.setGbDistributerFoodEntity(disFood);
				if (useBusinessInsight) {
					food.setIngredientAnalysisRows(ingredientRowsByFoodId.getOrDefault(foodId, Collections.emptyList()));
				} else {
					food.setIngredientAnalysisRows(null);
				}
				if (disFood != null && disFood.getGbDfFoodName() != null && !disFood.getGbDfFoodName().trim().isEmpty()) {
					food.setGbDfFoodName(disFood.getGbDfFoodName().trim());
				}
				if (log.isDebugEnabled()) {
					int recipeN = disFood != null && disFood.getGbdisFoodGoodsEntities() != null
							? disFood.getGbdisFoodGoodsEntities().size() : 0;
					int ingN = food.getIngredientAnalysisRows() != null ? food.getIngredientAnalysisRows().size() : 0;
					log.debug("depGetAllFood load depFoodId={} gbDfFoodId={} distributerFoodPresent={} activeRecipeLines={} ingredientAnalysisRows={}",
							food.getGbDepFoodId(), foodId, disFood != null, recipeN, ingN);
				}
			}
		}

		// ② 收集配方行上的分销商商品 id
		Set<Integer> disGoodsIds = new HashSet<>();
		for (GbDepFoodEntity food : foodEntities) {
			GbDistributerFoodEntity disFood = food.getGbDistributerFoodEntity();
			if (disFood == null || disFood.getGbdisFoodGoodsEntities() == null) {
				continue;
			}
			for (GbDistributerFoodGoodsEntity line : disFood.getGbdisFoodGoodsEntities()) {
				if (line.getGbDfgDisGoodsId() != null) {
					disGoodsIds.add(line.getGbDfgDisGoodsId());
				}
			}
		}
		// ③ 批量加载主档商品（避免每行配方里 N 次单条查询）
		Map<Integer, GbDistributerGoodsEntity> disGoodsById = new HashMap<>();
		if (!disGoodsIds.isEmpty()) {
			for (GbDistributerGoodsEntity e : gbDistributerGoodsService.listByIds(disGoodsIds)) {
				if (e != null && e.getGbDistributerGoodsId() != null) {
					disGoodsById.put(e.getGbDistributerGoodsId(), e);
				}
			}
		}
		// ④ 配方行挂上 GbDistributerGoodsEntity，并补全配方商品名/标准名
		for (GbDepFoodEntity food : foodEntities) {
			GbDistributerFoodEntity disFood = food.getGbDistributerFoodEntity();
			if (disFood == null || disFood.getGbdisFoodGoodsEntities() == null) {
				continue;
			}
			for (GbDistributerFoodGoodsEntity line : disFood.getGbdisFoodGoodsEntities()) {
				GbDistributerGoodsEntity goods = line.getGbDfgDisGoodsId() == null ? null : disGoodsById.get(line.getGbDfgDisGoodsId());
				line.setGbDistributerGoodsEntity(goods);
				if ((line.getGbDfgGoodsName() == null || line.getGbDfgGoodsName().trim().isEmpty())
						&& goods != null && goods.getGbDgGoodsName() != null) {
					line.setGbDfgGoodsName(goods.getGbDgGoodsName());
				}
				if ((line.getGbDfgGoodsStandardname() == null || line.getGbDfgGoodsStandardname().trim().isEmpty())
						&& goods != null && goods.getGbDgGoodsStandardname() != null) {
					line.setGbDfgGoodsStandardname(goods.getGbDgGoodsStandardname());
				}
			}
		}
		if (useBusinessInsight) {
			for (GbDepFoodEntity food : foodEntities) {
				GbDistributerFoodEntity disFood = food.getGbDistributerFoodEntity();
				if (disFood == null) {
					continue;
				}
				List<GbDistributerFoodGoodsEntity> recipe = disFood.getGbdisFoodGoodsEntities();
				if (recipe == null || recipe.isEmpty()) {
					continue;
				}
				gbDepFoodBusinessInsightService.enrichFoodGoodsOutboundStats(recipe, disId, depFatherId, startDate.trim(), stopDate.trim());
			}
		}
		if (log.isInfoEnabled()) {
			int n = foodEntities.size();
			long withDis = foodEntities.stream().filter(f -> f.getGbDistributerFoodEntity() != null).count();
			log.info("depGetAllFood: totalFoods={} withGbDistributerFoodEntity={} useBusinessInsight={}",
					n, withDis, useBusinessInsight);
		}
		if (businessExtras != null) {
			for (Map.Entry<String, Object> e : businessExtras.entrySet()) {
				r.put(e.getKey(), e.getValue());
			}
		}
		if (useBusinessInsight) {
			try {
				Comparator<GbDepFoodEntity> primary = buildDepFoodListComparator(sortBy, sortOrder);
				Comparator<GbDepFoodEntity> tieBreak = Comparator.comparing(GbDepFoodEntity::getGbDepFoodId,
						Comparator.nullsLast(Comparator.naturalOrder()));
				foodEntities.sort(primary.thenComparing(tieBreak));
			} catch (IllegalArgumentException e) {
				return R.error(-1, e.getMessage());
			}
		}
		return r;
	}

	private static Comparator<GbDepFoodEntity> buildDepFoodListComparator(String sortBy, String sortOrder) {
		String mode = normalizeDepFoodListSortBy(sortBy);
		boolean asc = normalizeDepFoodListSortAscending(sortOrder);
		Comparator<BigDecimal> valueCmp = asc ? Comparator.naturalOrder() : Comparator.reverseOrder();
		Comparator<BigDecimal> nullSafeValue = Comparator.nullsLast(valueCmp);
		switch (mode) {
			case "margin":
				return Comparator.comparing(GbDepFoodController::blendedMarginBdNullable, nullSafeValue);
			case "profit":
				return Comparator.comparing(GbDepFoodController::actualProfitBdNullable, nullSafeValue);
			case "sales":
			default:
				return Comparator.comparing(GbDepFoodController::soldAmountForSort, nullSafeValue);
		}
	}

	/** @return {@code sales}|{@code margin}|{@code profit} */
	private static String normalizeDepFoodListSortBy(String sortBy) {
		if (sortBy == null || sortBy.trim().isEmpty()) {
			return "sales";
		}
		String s = sortBy.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
		if ("blendedgrossmarginrateonlistprice".equals(s) || "blendedmargin".equals(s) || "margin".equals(s)
				|| "毛利率".equals(s)) {
			return "margin";
		}
		if ("gbdfsalesamount".equals(s) || "sales".equals(s) || "salesamount".equals(s) || "销量".equals(s)
				|| "份数".equals(s)) {
			return "sales";
		}
		if ("actualprofit".equals(s) || "profit".equals(s) || "实际利润".equals(s)) {
			return "profit";
		}
		throw new IllegalArgumentException(
				"sortBy 仅支持 gbDfSalesAmount（销售份数）、blendedGrossMarginRateOnListPrice（毛利率）、actualProfit（标价收入−实际成本123），当前: "
						+ sortBy);
	}

	private static boolean normalizeDepFoodListSortAscending(String sortOrder) {
		if (sortOrder == null || sortOrder.trim().isEmpty()) {
			return false;
		}
		String s = sortOrder.trim().toLowerCase(Locale.ROOT).replace(" ", "");
		if ("asc".equals(s) || "ascending".equals(s) || "升序".equals(s)) {
			return true;
		}
		if ("desc".equals(s) || "descending".equals(s) || "降序".equals(s)) {
			return false;
		}
		throw new IllegalArgumentException("sortOrder 仅支持 asc(升序) 或 desc(降序)，当前: " + sortOrder);
	}

	private static BigDecimal blendedMarginBdNullable(GbDepFoodEntity food) {
		return businessInsightDecimal(food, "blendedGrossMarginRateOnListPrice");
	}

	/** {@code listPriceRevenue − actualCostTotalAmount123}，无 {@code gbDfBusinessInsight} 时为 null。 */
	private static BigDecimal actualProfitBdNullable(GbDepFoodEntity food) {
		if (food == null || food.getGbDfBusinessInsight() == null) {
			return null;
		}
		BigDecimal rev = businessInsightDecimal(food, "listPriceRevenue");
		BigDecimal cost = businessInsightDecimal(food, "actualCostTotalAmount123");
		if (rev == null && cost == null) {
			return null;
		}
		return nzBd(rev).subtract(nzBd(cost));
	}

	private static BigDecimal businessInsightDecimal(GbDepFoodEntity food, String key) {
		if (food == null || food.getGbDfBusinessInsight() == null || key == null) {
			return null;
		}
		return GbDepartmentGoodsStockReduceSupport.coerceDecimal(food.getGbDfBusinessInsight().get(key));
	}

	private static BigDecimal nzBd(BigDecimal v) {
		return v == null ? BigDecimal.ZERO : v;
	}

	/**
	 * 与经营分析对齐：{@code gbDfSalesAmount} 在 attach 后与区间内总销量一致；大于 0 才加载配方。
	 */
	private static boolean hasPositiveSalesInPeriod(GbDepFoodEntity food) {
		return soldAmountForSort(food).signum() > 0;
	}

	/** 与配料分析一致：仅保留 {@code gb_dfg_status != 0} 的配方行。 */
	private static List<GbDistributerFoodGoodsEntity> recipeLinesActiveOnly(List<GbDistributerFoodGoodsEntity> raw) {
		if (raw == null || raw.isEmpty()) {
			return new ArrayList<>();
		}
		List<GbDistributerFoodGoodsEntity> out = new ArrayList<>();
		for (GbDistributerFoodGoodsEntity line : raw) {
			if (GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
				out.add(line);
			}
		}
		return out;
	}

	private static BigDecimal soldAmountForSort(GbDepFoodEntity food) {
		if (food == null) {
			return BigDecimal.ZERO;
		}
		String s = food.getGbDfSalesAmount();
		if (s == null || s.trim().isEmpty()) {
			return BigDecimal.ZERO;
		}
		try {
			return new BigDecimal(s.trim());
		} catch (NumberFormatException e) {
			return BigDecimal.ZERO;
		}
	}

	@RequestMapping(value = "/downLoadDepFood", method = RequestMethod.POST)
	@ResponseBody
	public R downLoadDepFood(@RequestBody GbDepFoodEntity depFood) {
		System.out.println("saveFood");
		gbDepFoodService.save(depFood);
		return R.ok();
	}


	@RequestMapping(value = "/cancleDownloadFood", method = RequestMethod.POST)
	@ResponseBody
	public R cancleDownloadFood(@RequestBody GbDepFoodEntity depFood) {
		gbDepFoodService.delete(depFood.getGbDepFoodId());
		return R.ok();
	}

}
