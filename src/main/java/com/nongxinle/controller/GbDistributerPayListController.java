package com.nongxinle.controller;

/**
 * 
 *
 * @author lpy
 * @date 02-12 21:10
 */

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nongxinle.entity.*;
import com.nongxinle.service.GbDistributerService;
import com.nongxinle.service.NxGoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.nongxinle.entity.GbDistributerPayListEntity;
import com.nongxinle.service.GbDistributerPayListService;
import com.nongxinle.utils.R;

import static com.nongxinle.utils.DateUtils.*;
import static com.nongxinle.utils.DateUtils.formatWhatYear;
import static com.nongxinle.utils.GbTypeUtils.getGbDisPayListRecord;


@RestController
@RequestMapping("gbdistributerpaylist")
public class GbDistributerPayListController {
	@Autowired
	private GbDistributerPayListService gbDistributerPayListService;
	@Autowired
	private GbDistributerService gbDistributerService;
	@Autowired
	private NxGoodsService nxGoodsService;

	@RequestMapping(value = "/addRecord", method = RequestMethod.POST)
	@ResponseBody
	public R addRecord (@RequestBody GbDistributerPayListEntity payListEntity) {

		System.out.println("palisss" + payListEntity);
		GbDistributerEntity nxDistributerEntity = gbDistributerService.getById(payListEntity.getGbNdplGbDisId());

		payListEntity.setGbNdplPayTime(formatFullTime());
		payListEntity.setGbNdplPayDate(formatWhatDay(0));
		payListEntity.setGbNdplPayMonth(formatWhatMonth(0));
		payListEntity.setGbNdplPayYear(formatWhatYear(0));
		payListEntity.setGbNdplStatus(0);
		payListEntity.setGbNdplType(getGbDisPayListRecord());
		payListEntity.setGbNdplRestPoints(nxDistributerEntity.getGbDistributerBuyQuantity());
		payListEntity.setGbNdplNxSupplierId(-1);
		System.out.println("savemee" + payListEntity);
		gbDistributerPayListService.save(payListEntity);

		BigDecimal decimal0 = new BigDecimal(nxDistributerEntity.getGbDistributerBuyQuantity());
		System.out.println("00000000" + decimal0);
		BigDecimal decimal1 = new BigDecimal(payListEntity.getGbNdplPaySubtotal());
		BigDecimal restPoints = decimal0.subtract(decimal1);
		nxDistributerEntity.setGbDistributerBuyQuantity(restPoints.toString());
		gbDistributerService.updateById(nxDistributerEntity);
		return R.ok();
	}

	@RequestMapping(value = "/depGetTodayRecordSeconds/{disId}")
	@ResponseBody
	public R depGetTodayRecordSeconds(@PathVariable Integer disId) {
		System.out.println("depGetTodayRecordSecondsdepGetTodayRecordSeconds" + disId);
		GbDistributerEntity distributerEntity = gbDistributerService.getById(disId);
		BigDecimal recordMinutes = new BigDecimal(distributerEntity.getGbDistributerRecordSeconds());

		Map<String, Object> map = new HashMap<>();
		map.put("disId", disId);
		map.put("date", formatWhatDay(0));
		map.put("type", getGbDisPayListRecord());
		System.out.println("mapmapapapap" + map);
		int count =	gbDistributerPayListService.queryDisPayListCount(map);
		System.out.println("tootttc" + count);
		if(count > 0){
			int total =	gbDistributerPayListService.queryDisRecordSecondsTotal(map);
			recordMinutes = recordMinutes.subtract(new BigDecimal(total));
		}

		return R.ok().put("data", recordMinutes);
	}



	@RequestMapping(value = "/addPoints", method = RequestMethod.POST)
	@ResponseBody
	public R addPoints (Integer disId, String points) {
		GbDistributerEntity gbDistributerEntity = gbDistributerService.getById(disId);
		BigDecimal decimal = new BigDecimal(gbDistributerEntity.getGbDistributerBuyQuantity());
		System.out.println("decimdall========" + decimal);
		BigDecimal decimal1 = new BigDecimal(points);
		System.out.println("nxdkkddkdk00" + gbDistributerEntity.getGbDistributerBuyQuantity());
		BigDecimal add = decimal.add(decimal1);
		gbDistributerEntity.setGbDistributerBuyQuantity(add.toString());
		System.out.println("nxdkkddkdk11" + gbDistributerEntity.getGbDistributerBuyQuantity());
		gbDistributerService.updateById(gbDistributerEntity);
		return R.ok().put("data",gbDistributerEntity);
	}

	@RequestMapping(value = "/disGetPayListDetail",method = RequestMethod.POST)
	@ResponseBody
	public R disGetPayListDetail(Integer disId,Integer limit, Integer page, Integer type) {

		System.out.println("ididid" + disId);
		Map<String, Object> map = new HashMap<>();
		map.put("disId", disId);
		map.put("offset", (page - 1) * limit);
		map.put("limit", limit);
		map.put("type", type);
		System.out.println("ididid" + map);
		List<GbDistributerPayListEntity> listEntities = gbDistributerPayListService.queryPayListListByParams(map);
		System.out.println("rlisisisis" + listEntities);
		Map<String, Object> mapCount = new HashMap<>();
		mapCount.put("disId", disId);
		mapCount.put("type", type);
		int total = gbDistributerPayListService.queryDisPayListCount(mapCount);
		Map<String, Object> pageMap = new HashMap<>();
		pageMap.put("totalCount", total);
		pageMap.put("pageSize", limit);
		pageMap.put("currPage", page);
		pageMap.put("list", listEntities);
		return R.ok().put("page", pageMap);
	}


	
}
