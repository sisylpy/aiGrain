package com.nongxinle.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.nongxinle.utils.PinYin4jUtils.hanziToPinyin;

@RestController
@RequestMapping("/gbdistributerfoodgoods")
public class GbDistributerFoodGoodsController {
	@Autowired
	private GbDistributerFoodGoodsService gbDistributerFoodGoodsService;
	@Autowired
	private GbDistributerFoodService gbDistributerFoodService;
	@Autowired
	private GbDistributerGoodsService gbDgService;

	/**
	 * 菜品配料快速搜索（批发商商品库）。
	 * 检索规则与 {@link GbDistributerGoodsController#queryDisGoodsByQuickSearchGb} 一致；
	 * 若传入 {@code foodId}，则从结果中剔除已是该菜品配料的批发商商品（gb_dfg_dis_goods_id）。
	 * 路径: POST /gbdistributerfoodgoods/quickSearchDisGoodsForFoodIngredient
	 */
	@RequestMapping(value = "/quickSearchDisGoodsForFoodIngredient", method = RequestMethod.POST)
	@ResponseBody
	public R quickSearchDisGoodsForFoodIngredient(String searchStr, String disId, Integer foodId) {
		Map<String, Object> map = new HashMap<>();
		map.put("disId", disId);

		for (int i = 0; i < searchStr.length(); i++) {
			String str = searchStr.substring(i, i + 1);
			if (str.matches("[\\u4E00-\\u9FFF]")) {
				String pinyin = hanziToPinyin(searchStr);
				map.put("searchStr", searchStr);
				map.put("searchPinyin", pinyin);
			} else {
				map.put("searchPinyin", searchStr);
			}
		}

		List<GbDistributerGoodsEntity> goodsEntities = gbDgService.queryGbDisGoodsQuickSearchStr(map);

		if (foodId != null) {
			List<GbDistributerFoodGoodsEntity> existing = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(foodId);
			Set<Integer> linkedDisGoodsIds = existing.stream()
					.map(GbDistributerFoodGoodsEntity::getGbDfgDisGoodsId)
					.filter(Objects::nonNull)
					.collect(Collectors.toSet());
			goodsEntities.removeIf(g -> linkedDisGoodsIds.contains(g.getGbDistributerGoodsId()));
		}

		return R.ok().put("data", goodsEntities);
	}


	@RequestMapping(value = "/saveGbFoodGoods", method = RequestMethod.POST)
	@ResponseBody
	public R saveGbFoodGoods(@RequestBody List<GbDistributerFoodGoodsEntity> foodGoodsList) {
		for (GbDistributerFoodGoodsEntity foodGoods : foodGoodsList) {
			foodGoods.setGbDfgStatus(1);
			gbDistributerFoodGoodsService.save(foodGoods);
		}
		return R.ok();
	}

	@RequestMapping(value = "/saveGbFoodGoodsItem", method = RequestMethod.POST)
	@ResponseBody
	public R saveGbFoodGoodsItem(@RequestBody GbDistributerFoodGoodsEntity foodGoods) {

			foodGoods.setGbDfgStatus(1);
			gbDistributerFoodGoodsService.save(foodGoods);

		return R.ok().put("data", foodGoods);
	}


	@RequestMapping(value = "/deleteGbFoodGoods/{id}")
	@ResponseBody
	public R deleteGbFoodGoods(@PathVariable Integer id) {
		gbDistributerFoodGoodsService.delete(id);
		return R.ok();
	}


	@RequestMapping(value = "/updateGbFoodGoods", method = RequestMethod.POST)
	@ResponseBody
	public R updateGbFoodGoods(@RequestBody GbDistributerFoodGoodsEntity foodGoods) {
		gbDistributerFoodGoodsService.update(foodGoods);
		Integer gbDfoodgFoodId = foodGoods.getGbDfgFoodId();
		System.out.println("abc");
		List<GbDistributerFoodGoodsEntity> gbDistributerFoodGoodsEntities = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(gbDfoodgFoodId);
		return R.ok().put("data", gbDistributerFoodGoodsEntities);
	}


	@RequestMapping(value = "/getFoodGoodsList/{foodId}")
	@ResponseBody
	public R getFoodGoodsList(@PathVariable Integer foodId) {
		List<GbDistributerFoodGoodsEntity> goodsEntities = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(foodId);
		return R.ok().put("data", goodsEntities);
	}

}
