package com.nongxinle.controller;

import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDepartmentGoodsStockQueryService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.formatWhatDay;
import static com.nongxinle.utils.DateUtils.formatWhatDayString;

/**
 * 部门商品库存 Controller：入参校验与 {@link R} 封装；查询编排见 {@link GbDepartmentGoodsStockQueryService}。
 */
@RestController
@RequestMapping("gbdepartmentgoodsstock")
public class GbDepartmentGoodsStockController {

    @Autowired
    private GbDepartmentGoodsStockQueryService gbDepartmentGoodsStockQueryService;
    @Autowired
    private GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    @Autowired
    private GbDistributerPurchaseGoodsService gbDPGService;




    @RequestMapping(value = "/getGbStockPurGoods", method = RequestMethod.POST)
    @ResponseBody
    public R getGbStockPurGoods(

            @RequestParam Integer disId,
            @RequestParam Integer greatId,
            @RequestParam String startDate,
            @RequestParam String stopDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer limit) {

        try {
            // 构建查询参数
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("disId", disId);
            queryMap.put("startDate", startDate);
            queryMap.put("stopDate", stopDate);
            queryMap.put("offset", (page - 1) * limit);
            queryMap.put("limit", limit);
            if (greatId != -1) {
                queryMap.put("disGoodsGreatId", greatId);
            }

            System.out.println("lisisisiisisisnnnn00000000sssssss" + queryMap);
            // 获取商品总数
            queryMap.put("restWeight", 0);
            Integer stockCount = gbDepartmentGoodsStockService.queryDisStockGoodsCount(queryMap);
            Double aDouble = gbDepartmentGoodsStockService.queryDepStockRestSubtotal(queryMap);

            System.out.println("totalCount: " + stockCount); // 新增日志
            Integer totalPages = (int) Math.ceil((double) stockCount / limit);

            // 获取商品列表
            System.out.println("查询商品map" + queryMap);
            System.out.println("开始查询库存商品列表..."); // 新增日志
            queryMap.put("orderByGoodsStockTotal", 1);
            List<GbDistributerGoodsEntity> goodsList = gbDepartmentGoodsStockService.queryDisGoodsStockByParams(queryMap);
            gbDPGService.fillWastePurGoodsForDisTreeGoods(goodsList, queryMap);
            System.out.println("商品列表查询完成，数量: " + (goodsList != null ? goodsList.size() : "null")); // 新增日志


            // 构建返回数据s
            Map<String, Object> result = new HashMap<>();
            result.put("totalCount", stockCount);
            result.put("totalPages", totalPages);
            result.put("currentPage", page);
            result.put("goodsList", goodsList);
            result.put("restSubtotal", String.format("%.1f", aDouble));
            System.out.println("reuslt" + result);

            return R.ok().put("data", result);

        } catch (Exception e) {
            System.out.println("查询商品列表异常: " + e.getMessage());
            e.printStackTrace();
            return R.error("获取商品列表失败：" + e.getMessage());
        }
    }


    /**
     * 部门商品业务数据：指定部门商品下的库存批次，及每批次全部 reduce 记录。
     */
    @RequestMapping(value = "/getDepGoodsBusiness", method = RequestMethod.POST)
    @ResponseBody
    public R getDepGoodsBusiness(
            @RequestParam Integer depGoodsId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String stopDate) {
        List<GbDepartmentGoodsStockEntity> data =
                gbDepartmentGoodsStockQueryService.queryDepGoodsBusiness(depGoodsId, startDate, stopDate);
        return R.ok().put("data", data);
    }

    /**
     * 门店库存按时间段分类统计
     */
    @RequestMapping(value = "/getMendianStockTypePeriod", method = RequestMethod.POST)
    @ResponseBody
    public R getMendianStockTypePeriod(
            @RequestParam Integer disId,
            @RequestParam Integer whichDay,
            @RequestParam(required = false) String searchDepIds,
            @RequestParam(required = false) String searchDepId,
            @RequestParam(required = false, defaultValue = "0") Integer type) {
        Map<String, Object> mapResult = gbDepartmentGoodsStockQueryService.queryMendianStockTypePeriod(disId, whichDay, searchDepIds, searchDepId, type);
        return R.ok().put("data", mapResult);
    }

    /**
     * 根据大类ID查询商品库存按时间段分类统计。
     * <p>有有效 {@code greatId} 时按大类即可定位商品，不必再传 {@code disId}（与批发商 id 等价，可不参与筛选）。</p>
     * <p>部门范围：优先传 {@code depId}（部门 id，对应库存表部门主键）；也可传 {@code searchDepId}（部门父级 id，写入 {@code depFatherId}）。</p>
     */
    @RequestMapping(value = "/disGetDayStockByGreatId", method = RequestMethod.POST)
    @ResponseBody
    public R disGetDayStockByGreatId(
            @RequestParam(required = false) Integer disId,
            @RequestParam(required = false) String searchDepId,
            @RequestParam(required = false) Integer depId,
            @RequestParam String greatId,
            @RequestParam Integer whichDay,
            @RequestParam(required = false, defaultValue = "0") Integer type) {
        Map<String, Object> map = new HashMap<>();
        if (whichDay == 99 || whichDay == 0) {
            map.put("oneDay", gbDepartmentGoodsStockQueryService.queryDayStockByGreatId(disId, searchDepId, depId, greatId, whichDay, type));
        } else {
            map.put("oneDay", gbDepartmentGoodsStockQueryService.queryDayStockByGreatId(disId, searchDepId, depId, greatId, -whichDay, type));
        }
        return R.ok().put("data", map);
    }





    @RequestMapping(value = "/getDisGoodsBusiness", method = RequestMethod.POST)
    @ResponseBody
    public R getDisGoodsBusiness(Integer disGoodsId, String startDate, String stopDate) {
        try {
            Map<String, Object> mapDisGoods = new HashMap<>();
            mapDisGoods.put("disGoodsId", disGoodsId);
            mapDisGoods.put("restWeight", 0);
            mapDisGoods.put("startDate", startDate);
            mapDisGoods.put("stopDate", stopDate);
            System.out.println("mapdddffafdasnnnnnn333333333" + mapDisGoods);
            List<GbDepartmentGoodsStockEntity> stockEntities = gbDepartmentGoodsStockService.queryGoodsStockByParams(mapDisGoods);
            System.out.println("stoennenenene" + stockEntities.size());
            return R.ok().put("data", stockEntities);
        } catch (Exception e) {
            e.printStackTrace();
            return R.error("服务器异常: " + e.getMessage());
        }
    }







}
