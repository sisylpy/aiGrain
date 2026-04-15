package com.nongxinle.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nongxinle.entity.*;
import com.nongxinle.service.GbDepFoodService;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import com.nongxinle.utils.UploadFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.utils.R;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/gbdistributerfood")
public class GbDistributerFoodController {

    private static final Logger logger = LoggerFactory.getLogger(GbDistributerFoodController.class);

	@Autowired
	private GbDistributerFoodService gbDistributerFoodService;
	@Autowired
	private GbDistributerFoodGoodsService gbDistributerFoodGoodsService;
	@Autowired
	private GbDepFoodService gbDepFoodService;


	@RequestMapping(value = "/depGetAllFood", method = RequestMethod.POST)
	@ResponseBody
	public R depGetAllFood (Integer disId, Integer depFatherId) {
		Map<String, Object> map = new HashMap<>();
		map.put("disId", disId);
		map.put("depFatherId", depFatherId);
		List<GbDistributerFoodEntity> foodEntities = gbDistributerFoodService.queryDisAllFood(map);
		return R.ok().put("data", foodEntities);
	}


	@RequestMapping(value = "/getDisAllFood/{disId}")
	@ResponseBody
	public R getDisAllFood(@PathVariable Integer disId) {
		Map<String, Object> map = new HashMap<>();
		map.put("disId", disId);
		List<GbDistributerFoodEntity> foodEntities = gbDistributerFoodService.queryDisAllFood(map);
		return R.ok().put("data", foodEntities);
	}


	@RequestMapping(value = "/getFoodList/{fatherId}")
	@ResponseBody
	public R getFoodList(@PathVariable Integer fatherId) {
		Map<String, Object> map = new HashMap<>();
		map.put("fatherId", fatherId);
		List<GbDistributerFoodEntity> foodEntities = gbDistributerFoodService.queryFoodByParams(map);
		return R.ok().put("data", foodEntities);
	}


	@RequestMapping(value = "/deleteFood",  method = RequestMethod.POST)
	@ResponseBody
	public R deleteFood(Integer id) {
		Map<String, Object> map = new HashMap<>();
		map.put("foodId", id);
		List<GbDepFoodEntity> depFoodEntities = gbDepFoodService.queryDepFoodByParams(map);
		if(depFoodEntities.size() > 0){
			return R.error(-1, "有门店下载了菜品");
		}else{
			GbDistributerFoodEntity foodEntity = gbDistributerFoodService.queryObject(id);
			String oldPath = foodEntity.getGbDfFoodImg();
			if (oldPath != null && !oldPath.trim().isEmpty()) {
				UploadFile.deleteFile(oldPath);
			}
			gbDistributerFoodService.delete(id);
			return R.ok();
		}
	}

	@RequestMapping(value = "/updateFood", method = RequestMethod.POST)
	@ResponseBody
	public R updateFood (@RequestBody GbDistributerFoodEntity food) {
		Map<String, Object> map = new HashMap<>();
		map.put("foodName", food.getGbDfFoodName());
		map.put("dayuFatherId", 0);
		List<GbDistributerFoodEntity> foodEntities = gbDistributerFoodService.queryFoodByParams(map);
		if(foodEntities.size() > 0 && foodEntities.get(0).getGbDistributerFoodId() != food.getGbDistributerFoodId()){
			return R.error(-1, "名称重复了");
		}else{
			gbDistributerFoodService.update(food);
			return R.ok();
		}
	}

	@RequestMapping(value = "/updateFoodWithFile", method = RequestMethod.POST)
	@ResponseBody
	public R updateFoodWithFile (@RequestParam("file") MultipartFile file,
								 @RequestParam("foodName") String foodName,
								 @RequestParam("id") Integer id,
								 @RequestParam("price") String price,
								 @RequestParam("method") String method
	) {
		Map<String, Object> map = new HashMap<>();
		map.put("foodName", foodName);
		map.put("dayuFatherId", 0);
		List<GbDistributerFoodEntity> foodEntities = gbDistributerFoodService.queryFoodByParams(map);
		if(foodEntities.size() > 0 && foodEntities.get(0).getGbDistributerFoodId() != id){
			return R.error(-1, "名称重复了");
		}else{
			GbDistributerFoodEntity foodEntity = gbDistributerFoodService.queryObject(id);
			String oldPath = foodEntity.getGbDfFoodImg();
			if (oldPath != null && !oldPath.trim().isEmpty()) {
				UploadFile.deleteFile(oldPath);
			}

			// 上传新图片
			String newUploadName = "foodImage";
			String headByString = com.nongxinle.utils.PinYin4jUtils.hanziToPinyin(foodName);
			String filePath = UploadFile.uploadFileName(newUploadName, file, headByString);

			foodEntity.setGbDfFoodImg(filePath);
			foodEntity.setGbDfFoodMethod(method);
			foodEntity.setGbDfFoodName(foodName);
			foodEntity.setGbDfFoodPrice(price);
			gbDistributerFoodService.update(foodEntity);

			return R.ok();
		}
	}

	@RequestMapping(value = "/saveGbFood",  produces = "text/html;charset=UTF-8")
	@ResponseBody
	public R saveGbFood(@RequestParam("file") MultipartFile file,
						@RequestParam("foodName") String foodName,
						@RequestParam("fatherId") Integer fatherId,
						@RequestParam("disId") Integer disId,
						@RequestParam("price") String price,
						@RequestParam("method") String method) {
		logger.info("========== saveGbFood 开始 ==========");
		logger.info("参数: foodName={}, fatherId={}, disId={}, price={}, method={}", foodName, fatherId, disId, price, method);
		logger.info("文件信息: name={}, size={}, contentType={}", file.getName(), file.getSize(), file.getContentType());

		try {
			Map<String, Object> map = new HashMap<>();
			map.put("foodName", foodName);
			map.put("dayuFatherId", 0);
			List<GbDistributerFoodEntity> foodEntities = gbDistributerFoodService.queryFoodByParams(map);
			logger.info("查询同名食品数量: {}", foodEntities.size());

			if(foodEntities.size() > 0){
				logger.warn("食品名称重复，返回错误");
				return R.error();
			}

			// 上传图片
			String newUploadName = "foodImage";
			String headByString = com.nongxinle.utils.PinYin4jUtils.hanziToPinyin(foodName);
			logger.info("开始上传图片，pinyin={}", headByString);
			String filePath = UploadFile.uploadFileName(newUploadName, file, headByString);
			logger.info("图片上传成功，路径={}", filePath);

			GbDistributerFoodEntity foodEntity = new GbDistributerFoodEntity();
			foodEntity.setGbDfDistributerId(disId);
			foodEntity.setGbDfFoodImg(filePath);
			foodEntity.setGbDfFoodMethod(method);
			foodEntity.setGbDfFoodFatherId(fatherId);
			foodEntity.setGbDfFoodName(foodName);
			foodEntity.setGbDfFoodPrice(price);
			gbDistributerFoodService.save(foodEntity);
			logger.info("========== saveGbFood 成功 ==========");
			return R.ok();
		} catch (Exception e) {
			logger.error("========== saveGbFood 失败 ==========", e);
			return R.error(-1, "保存失败: " + e.getMessage());
		}
	}


	@RequestMapping(value = "/deleteGbSupplierFather/{id}")
	@ResponseBody
	public R deleteGbSupplierFather(@PathVariable Integer id) {
		Map<String, Object> map = new HashMap<>();
		map.put("fatherId", id);
		List<GbDistributerFoodEntity> supplierEntities = gbDistributerFoodService.queryFoodByParams(map);
		if(supplierEntities.size() > 0){
			return R.error(-1,"类别下有供货商，不能删除");
		}else{
			gbDistributerFoodService.delete(id);
			return R.ok();
		}
	}

	@RequestMapping(value = "/deleteGbSupplier/{id}")
	@ResponseBody
	public R deleteGbSupplier(@PathVariable Integer id) {
		gbDistributerFoodService.delete(id);
		return R.ok();
	}

	@RequestMapping(value = "/updateGbFood", method = RequestMethod.POST)
	@ResponseBody
	public R updateGbFood(@RequestBody GbDistributerFoodEntity suppler) {
		gbDistributerFoodService.update(suppler);
		return R.ok();
	}

	@RequestMapping(value = "/saveGbFoodFather", method = RequestMethod.POST)
	@ResponseBody
	public R saveGbFoodFather(@RequestBody GbDistributerFoodEntity foodEntity) {
		String gbDistributerFoodName = foodEntity.getGbDfFoodName();
		Map<String, Object> map = new HashMap<>();
		map.put("foodName", gbDistributerFoodName);
		map.put("fatherId", 0);
		System.out.println("whatisimap" + map);
		List<GbDistributerFoodEntity> foodEntities = gbDistributerFoodService.queryFoodByParams(map);
		if(foodEntities.size() > 0){
			return R.error(-1, "名称重复了");
		}else{
			foodEntity.setGbDfFoodFatherId(0);
			gbDistributerFoodService.save(foodEntity);
			return R.ok();
		}
	}

	@RequestMapping(value = "/disGetFood/{disId}")
	@ResponseBody
	public R disGetFood(@PathVariable Integer disId) {
		logger.info("========== disGetFood called with disId: {} ==========", disId);
		Map<String, Object> map = new HashMap<>();
		map.put("disId", disId);
		map.put("fatherId", 0);
		logger.info("========== query params: {} ==========", map);
		List<GbDistributerFoodEntity> supplierEntities = gbDistributerFoodService.queryFoodByParams(map);
		logger.info("========== query result size: {} ==========", supplierEntities.size());
		return R.ok().put("data", supplierEntities);
	}

}
