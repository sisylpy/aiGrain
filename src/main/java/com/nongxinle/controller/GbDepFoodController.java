package com.nongxinle.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import com.nongxinle.entity.*;
import com.nongxinle.service.GbDepFoodService;
import com.nongxinle.service.GbDepFoodSalesService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import com.nongxinle.service.GbDistributerGoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.nongxinle.utils.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/gbdepfood")
public class GbDepFoodController {
	@Autowired
	private GbDepFoodService gbDepFoodService;
	@Autowired
	private GbDepFoodSalesService gbDepFoodSalesService;
	@Autowired
	private GbDistributerFoodService gbDistributerFoodService;
	@Autowired
	private GbDistributerFoodGoodsService gbDistributerFoodGoodsService;
	@Autowired
	private GbDistributerGoodsService gbDistributerGoodsService;


	@RequestMapping(value = "/depGetAllFood", method = RequestMethod.POST)
	@ResponseBody
	public R depGetAllFood (Integer disId, Integer depFatherId, String startDate, String stopDate) {
		Map<String, Object> map = new HashMap<>();
		map.put("disId", disId);
		map.put("depFatherId", depFatherId);
		List<GbDepFoodEntity> foodEntities = gbDepFoodService.queryDepAllFood(map);

		Map<Integer, BigDecimal> salesByFoodId = new HashMap<>();
		boolean hasDateRange = startDate != null && !startDate.trim().isEmpty()
				&& stopDate != null && !stopDate.trim().isEmpty();
		if (disId != null && depFatherId != null && hasDateRange) {
			LambdaQueryWrapper<GbDepFoodSalesEntity> w = new LambdaQueryWrapper<>();
			w.eq(GbDepFoodSalesEntity::getGbDfsDistributerId, disId)
					.eq(GbDepFoodSalesEntity::getGbDfsDepFatherId, depFatherId)
					.ge(GbDepFoodSalesEntity::getGbDfsFullDate, startDate.trim())
					.le(GbDepFoodSalesEntity::getGbDfsFullDate, stopDate.trim());
			List<GbDepFoodSalesEntity> salesRows = gbDepFoodSalesService.list(w);
			for (GbDepFoodSalesEntity row : salesRows) {
				if (row.getGbDfsFoodId() == null) {
					continue;
				}
				BigDecimal amount = parseAmountSafe(row.getGbDfsAmount());
				if (amount.compareTo(BigDecimal.ZERO) <= 0) {
					continue;
				}
				salesByFoodId.merge(row.getGbDfsFoodId(), amount, BigDecimal::add);
			}
		}
		for (GbDepFoodEntity food : foodEntities) {
			Integer foodId = food.getGbDfFoodId();
			BigDecimal qty = foodId == null ? BigDecimal.ZERO : salesByFoodId.getOrDefault(foodId, BigDecimal.ZERO);
			food.setGbDfSalesAmount(qty.stripTrailingZeros().toPlainString());
			if (foodId != null) {
				GbDistributerFoodEntity disFood = gbDistributerFoodService.queryObject(foodId);
				if (disFood != null) {
					List<GbDistributerFoodGoodsEntity> recipe = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(foodId);
					if (recipe == null) {
						recipe = new ArrayList<>();
					}
					disFood.setGbdisFoodGoodsEntities(recipe);
				}
				food.setGbDistributerFoodEntity(disFood);
				if ((food.getGbDfFoodName() == null || food.getGbDfFoodName().trim().isEmpty())
						&& disFood != null && disFood.getGbDfFoodName() != null) {
					food.setGbDfFoodName(disFood.getGbDfFoodName());
				}
			}
		}

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
		Map<Integer, GbDistributerGoodsEntity> disGoodsById = new HashMap<>();
		if (!disGoodsIds.isEmpty()) {
			for (GbDistributerGoodsEntity e : gbDistributerGoodsService.listByIds(disGoodsIds)) {
				if (e != null && e.getGbDistributerGoodsId() != null) {
					disGoodsById.put(e.getGbDistributerGoodsId(), e);
				}
			}
		}
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
		return R.ok().put("data", foodEntities);
	}

	private static BigDecimal parseAmountSafe(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return BigDecimal.ZERO;
		}
		try {
			return new BigDecimal(raw.trim());
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
