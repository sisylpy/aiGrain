package com.nongxinle.controller;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nongxinle.entity.*;
import com.nongxinle.service.GbDepFoodSalesService;
import com.nongxinle.service.GbDepFoodService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.UploadFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.ManagedMap;
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
	private GbDepFoodService gbDepFoodService;
	@Autowired
	private GbDepartmentService gbDepartmentService;
	@Autowired
	private GbDepFoodSalesService gbDepFoodSalesService;





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
		foodEntities.sort(Comparator.comparing(e -> {
			Integer s = e.getGbDfStatus();
			if (s != null && s.equals(GbConstants.DistributerFoodStatus.DISABLED_WITH_DEP_FOOD_SALES)) {
				return 1;
			}
			return 0;
		}));
		return R.ok().put("data", foodEntities);
	}


	@RequestMapping(value = "/reStartFood/{foodId}")
	@ResponseBody
	public R reStartFood(@PathVariable Integer foodId) {

		GbDistributerFoodEntity gbDistributerFoodEntity = gbDistributerFoodService.queryObject(foodId);
		gbDistributerFoodEntity.setGbDfStatus(0);
		gbDistributerFoodService.update(gbDistributerFoodEntity);

		Map<String, Object> map = new HashMap<>();
		map.put("foodId", foodId);
		List<GbDepFoodEntity> depFoodEntities = gbDepFoodService.queryDepFoodByParams(map);
		if (depFoodEntities.size() > 0) {
			for (GbDepFoodEntity depFood : depFoodEntities) {
				depFood.setGbDfStatus(0);
				gbDepFoodService.update(depFood);
			}
		}

		return R.ok();
	}



	@RequestMapping(value = "/deleteFood",  method = RequestMethod.POST)
	@ResponseBody
	public R deleteFood(Integer id) {
		GbDistributerFoodEntity foodEntity = gbDistributerFoodService.queryObject(id);
		if (foodEntity == null) {
			return R.error(-1, "菜品不存在");
		}
		Map<String, Object> map = new HashMap<>();
		map.put("foodId", id);
		List<GbDepFoodEntity> depFoodEntities = gbDepFoodService.queryDepFoodByParams(map);
		long salesCount = gbDepFoodSalesService.countSalesByDistributerFoodId(id);
		System.out.println("coutnntnt" + salesCount);
		if (salesCount > 0) {
			foodEntity.setGbDfStatus(GbConstants.DistributerFoodStatus.DISABLED_WITH_DEP_FOOD_SALES);
			gbDistributerFoodService.update(foodEntity);

			if (depFoodEntities.size() > 0) {
				for (GbDepFoodEntity depFood : depFoodEntities) {
					depFood.setGbDfStatus(1);
					gbDepFoodService.update(depFood);
				}
			}

			return R.ok().put("disabledOnly", true);
		}else {

			if (depFoodEntities.size() > 0) {
				for (GbDepFoodEntity depFood : depFoodEntities) {
					gbDepFoodService.delete(depFood.getGbDepFoodId());
				}
			}
		}

		String oldPath = foodEntity.getGbDfFoodImg();
		if (oldPath != null && !oldPath.trim().isEmpty()) {
			UploadFile.deleteFile(oldPath);
		}
		gbDistributerFoodService.delete(id);
		return R.ok();
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
	public R updateFoodWithFile (@RequestParam(value = "file", required = false) MultipartFile file,
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
			if (file != null && !file.isEmpty()) {
				String oldPath = foodEntity.getGbDfFoodImg();
				if (oldPath != null && !oldPath.trim().isEmpty()) {
					UploadFile.deleteFile(oldPath);
				}
				String newUploadName = "foodImage";
				String headByString = com.nongxinle.utils.PinYin4jUtils.hanziToPinyin(foodName);
				String filePath = UploadFile.uploadFileName(newUploadName, file, headByString);
				foodEntity.setGbDfFoodImg(filePath);
			}

			foodEntity.setGbDfFoodMethod(method);
			foodEntity.setGbDfFoodName(foodName);
			foodEntity.setGbDfFoodPrice(price);
			gbDistributerFoodService.update(foodEntity);

			return R.ok();
		}
	}

	@RequestMapping(value = "/saveGbFood",  produces = "text/html;charset=UTF-8")
	@ResponseBody
	public R saveGbFood(@RequestParam(value = "file", required = false) MultipartFile file,
						@RequestParam("foodName") String foodName,
						@RequestParam("fatherId") Integer fatherId,
						@RequestParam("disId") Integer disId,
						@RequestParam("price") String price,
						@RequestParam("method") String method) {
		logger.info("========== saveGbFood 开始 ==========");
		logger.info("参数: foodName={}, fatherId={}, disId={}, price={}, method={}", foodName, fatherId, disId, price, method);
		if (file != null && !file.isEmpty()) {
			logger.info("文件信息: originalFilename={}, size={}, contentType={}",
					file.getOriginalFilename(), file.getSize(), file.getContentType());
		} else {
			logger.info("未上传图片");
		}

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

			String filePath = null;
			if (file != null && !file.isEmpty()) {
				String newUploadName = "foodImage";
				String headByString = com.nongxinle.utils.PinYin4jUtils.hanziToPinyin(foodName);
				logger.info("开始上传图片，pinyin={}", headByString);
				filePath = UploadFile.uploadFileName(newUploadName, file, headByString);
				logger.info("图片上传成功，路径={}", filePath);
			}

			GbDistributerFoodEntity foodEntity = new GbDistributerFoodEntity();
			foodEntity.setGbDfDistributerId(disId);
			foodEntity.setGbDfFoodImg(filePath);
			foodEntity.setGbDfFoodMethod(method);
			foodEntity.setGbDfFoodFatherId(fatherId);
			foodEntity.setGbDfFoodName(foodName);
			foodEntity.setGbDfFoodPrice(price);
			foodEntity.setGbDfStatus(0);
			gbDistributerFoodService.save(foodEntity);

			//如果只有一个门店并且只有一个部分，则自动给部门添加菜品。
			Map<String, Object> mapDep = new HashMap<>();
			map.put("disId", disId);
			map.put("depType", GbConstants.DepartmentType.STORE);
			List<GbDepartmentEntity> gbDepartmentEntities = gbDepartmentService.queryGroupDepsByDisId(map);
			String depFatherId = gbDepartmentEntities.get(0).getGbDepartmentId().toString();
			List<GbDepartmentEntity> subDepartments = gbDepartmentService.querySubDepartments(Integer.valueOf(depFatherId));
			System.out.println("subnamgbDepartmentEntitiesi" + gbDepartmentEntities.size());
			System.out.println("subnami" + subDepartments.size());
			if(gbDepartmentEntities.size() == 1  && subDepartments.size() == 1){
				GbDepFoodEntity gbDepFoodEntity = new GbDepFoodEntity();
				gbDepFoodEntity.setGbDfFoodId(foodEntity.getGbDistributerFoodId());
				gbDepFoodEntity.setGbDfDepId(subDepartments.get(0).getGbDepartmentId());
				gbDepFoodEntity.setGbDfDepFatherId(gbDepartmentEntities.get(0).getGbDepartmentId().toString());
				gbDepFoodEntity.setGbDfFoodName(foodName);
				gbDepFoodEntity.setGbDfFoodPrice(price);
				gbDepFoodEntity.setGbDfFoodMethod(method);
				gbDepFoodEntity.setGbDfFoodFatherId(fatherId);
				gbDepFoodEntity.setGbDfStatus(0);
				gbDepFoodEntity.setGbDfDistributerId(disId);
				gbDepFoodService.save(gbDepFoodEntity);
			}
			logger.info("========== saveGbFood 成功 ==========");
			return R.ok();
		} catch (Exception e) {
			logger.error("========== saveGbFood 失败 ==========", e);
			return R.error(-1, "保存失败: " + e.getMessage());
		}
	}


	@RequestMapping(value = "/deleteGbFoodFather/{id}")
	@ResponseBody
	public R deleteGbFoodFather(@PathVariable Integer id) {
		Map<String, Object> map = new HashMap<>();
		map.put("fatherId", id);
		List<GbDistributerFoodEntity> supplierEntities = gbDistributerFoodService.queryFoodByParams(map);
		if(supplierEntities.size() > 0){
			return R.error(-1,"类别下有菜品，不能删除");
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
		String marginErr = validateGrossMarginStandard(suppler);
		if (marginErr != null) {
			return R.error(-1, marginErr);
		}
		gbDistributerFoodService.update(suppler);
		return R.ok();
	}

	@RequestMapping(value = "/saveGbFoodFather", method = RequestMethod.POST)
	@ResponseBody
	public R saveGbFoodFather(@RequestBody GbDistributerFoodEntity foodEntity) {
		if (foodEntity.getGbDfDistributerId() == null) {
			return R.error(-1, "缺少批发商id");
		}
		String name = foodEntity.getGbDfFoodName();
		if (name == null || name.trim().isEmpty()) {
			return R.error(-1, "名称不能为空");
		}
		String marginErr = validateGrossMarginStandard(foodEntity);
		if (marginErr != null) {
			return R.error(-1, marginErr);
		}
		Map<String, Object> map = new HashMap<>();
		map.put("foodName", name);
		map.put("fatherId", 0);
		map.put("disId", foodEntity.getGbDfDistributerId());
		List<GbDistributerFoodEntity> foodEntities = gbDistributerFoodService.queryFoodByParams(map);
		if (foodEntities.size() > 0) {
			return R.error(-1, "名称重复了");
		}
		foodEntity.setGbDfFoodFatherId(0);
		gbDistributerFoodService.save(foodEntity);
		return R.ok();
	}

	/**
	 * 父级/分类 毛利率标尺：目标与上下浮动全空或全有；若有值则 0–100。返回 null 表示可保存。
	 */
	private static String validateGrossMarginStandard(GbDistributerFoodEntity e) {
		BigDecimal t = e.getGbDfTargetGrossMarginRate();
		BigDecimal f = e.getGbDfGrossMarginFloatAbs();
		if (t == null && f == null) {
			return null;
		}
		if (t == null || f == null) {
			return "目标毛利率与上下浮动需同时设置或同时留空";
		}
		if (t.signum() < 0 || t.compareTo(new BigDecimal("100")) > 0) {
			return "目标毛利率须在 0～100 之间";
		}
		if (f.signum() < 0 || f.compareTo(new BigDecimal("100")) > 0) {
			return "上下浮动须在 0～100 个绝对百分点内";
		}
		return null;
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
