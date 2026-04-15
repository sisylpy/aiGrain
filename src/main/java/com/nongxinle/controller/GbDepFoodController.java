package com.nongxinle.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nongxinle.entity.*;
import com.nongxinle.service.GbDepFoodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.nongxinle.utils.R;


@RestController
@RequestMapping("/gbdepfood")
public class GbDepFoodController {
	@Autowired
	private GbDepFoodService gbDepFoodService;


	@RequestMapping(value = "/depGetAllFood", method = RequestMethod.POST)
	@ResponseBody
	public R depGetAllFood (Integer disId, Integer depFatherId) {
		Map<String, Object> map = new HashMap<>();
		map.put("disId", disId);
		map.put("depFatherId", depFatherId);
		List<GbDepFoodEntity> foodEntities = gbDepFoodService.queryDepAllFood(map);
		return R.ok().put("data", foodEntities);
	}


	@RequestMapping(value = "/downLoadDepFood", method = RequestMethod.POST)
	@ResponseBody
	public R downLoadDepFood(@RequestBody GbDepFoodEntity depFood) {
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
