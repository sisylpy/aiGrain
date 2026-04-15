package com.nongxinle.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.hutool.json.JSONObjectIter;
import com.nongxinle.entity.*;
import com.nongxinle.service.GbDepFoodService;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.utils.R;


@RestController
@RequestMapping("/gbdistributerfoodgoods")
public class GbDistributerFoodGoodsController {
	@Autowired
	private GbDistributerFoodGoodsService gbDistributerFoodGoodsService;
	@Autowired
	private GbDistributerFoodService gbDistributerFoodService;


	@RequestMapping(value = "/saveGbFoodGoods", method = RequestMethod.POST)
	@ResponseBody
	public R saveGbFoodGoods(@RequestBody List<GbDistributerFoodGoodsEntity> foodGoodsList) {
		for (GbDistributerFoodGoodsEntity foodGoods : foodGoodsList) {
			foodGoods.setGbDfgStatus(1);
			gbDistributerFoodGoodsService.save(foodGoods);
		}
		return R.ok();
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
