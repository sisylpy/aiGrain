package com.nongxinle.controller;

import com.nongxinle.dto.GbDepGoodsStockAdjustRequest;
import com.nongxinle.dto.GbDepGoodsStockAdjustResult;
import com.nongxinle.entity.*;
import com.nongxinle.service.GbDepartmentDisGoodsService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDepartmentReorderReminderService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static com.nongxinle.utils.DateUtils.formatWhatDay;

/**
 * 部门商品关联Controller
 */
@RestController
@RequestMapping("gbdepartmentdisgoods")
@Tag(name = "部门商品管理", description = "批发商部门商品分类和列表查询")
public class GbDepartmentDisGoodsController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GbDepartmentDisGoodsController.class);

    @Autowired
    private GbDepartmentDisGoodsService gbDepartmentDisGoodsService;
    @Autowired
    private GbDepartmentGoodsStockService gbDepGoodsStockService;
    @Autowired
    private GbDistributerGoodsService gbDistributerGoodsService;
    @Autowired
    private GbDepartmentReorderReminderService gbDepartmentReorderReminderService;




    @RequestMapping(value = "/disGetDepGoodsGbPageWithSupplier")
    @ResponseBody
    public R disGetDepGoodsGbPageWithSupplier(Integer limit, Integer page, Integer disId, Integer supplierId) {
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("supplierId", supplierId);

        // 1. 获取总数
        List<Integer > disGoodsIds =   gbDepartmentDisGoodsService.queryOnlyDisGoodsIds(map);

        // 2. 获取当前页数据
        map.put("status", 4);
        map.put("date", formatWhatDay(0));
        map.put("limit", limit);
        map.put("offset", (page - 1) * limit);
        log.info("查询参数: limit={}, offset={}", limit, (page - 1) * limit);
        log.info("map查询: {}", map);
        TreeSet<GbDistributerGoodsEntity> currentPageSet = gbDepartmentDisGoodsService.disQueryDisGoodsWithOrderForAiTree(map);
        log.info("当前页数据量Tree: {}", currentPageSet.size());

        // 4. 处理每个商品的提示文本
//        for(GbDistributerGoodsEntity distributerGoodsEntity: currentPageSet){
//            gbDistributerGoodsService.getStockTotal(distributerGoodsEntity);
//        }
        log.info("最终返回数据量: {}", currentPageSet.size());
        // 5. 返回分页数据
        List<GbDistributerGoodsEntity> currentPageList = new ArrayList<>(currentPageSet);

        // 3. 返回分页数据
       Map<String, Object> pageMap = new HashMap<>();
        pageMap.put("totalCount", disGoodsIds.size());
        pageMap.put("pageSize", limit);
        pageMap.put("currPage", page);
        pageMap.put("list", currentPageList);
        return R.ok().put("page", pageMap);
    }

    @RequestMapping(value = "/disGetDepGoodsCataGbWithSupplier")
    @ResponseBody
    public R disGetDepGoodsCataGbWithSupplier(Integer disId, Integer supplierId) {
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("supplierId", supplierId);

        System.out.println("cattaktktktkktk");
        List<GbDistributerFatherGoodsEntity> disGoodsEntities = gbDepartmentDisGoodsService.disGetDepDisGoodsCataGb(map);

        System.out.println("iddmdpdpddpdpd" + map);
        List<Integer > disGoodsIds =   gbDepartmentDisGoodsService.queryOnlyDisGoodsIds(map);
        Map<String, Object> mapR = new HashMap<>();
        mapR.put("cataArr",disGoodsEntities);
        mapR.put("disGoodsArr", disGoodsIds);

        return R.ok().put("data", mapR);
    }



    @RequestMapping(value = "/deleteDepGoods/{depGoodsId}")
    @ResponseBody
    public R deleteDepGoods(@PathVariable Integer depGoodsId) {
        Map<String, Object> map = new HashMap<>();
        map.put("depGoodsId", depGoodsId);
        map.put("restWeight", 0);
        List<GbDepartmentGoodsStockEntity> stockEntities = gbDepGoodsStockService.queryGoodsStockByParams(map);
        if (stockEntities.size() > 0) {
            return R.error(-1, "有库存，不能删除");
        } else {

            gbDepartmentDisGoodsService.removeById(depGoodsId);
            return R.ok();
        }
    }



    /**
     * 获取部门商品分类和商品ID列表
     * 接口: /gbdepartmentdisgoods/depGetDepGoodsCataGb
     */
    @Operation(summary = "获取部门商品分类", description = "获取指定部门关联的批发商商品分类树，以及该部门已选择的商品ID列表")
    @RequestMapping(value = "/depGetDepGoodsCataGb", method = RequestMethod.POST)
    public R depGetDepGoodsCataGb(
            @RequestParam Integer depId,
            @RequestParam Integer disId) {
        Map<String, Object> map = new HashMap<>();
        map.put("depId", depId);
        map.put("disId", disId);
        map.put("pull", 0);

        log.info("【depGetDepGoodsCataGb】查询参数: depId={}, disId={}, pull=0", depId, disId);

        // 获取分类
        List<GbDistributerFatherGoodsEntity> disGoodsEntities = gbDepartmentDisGoodsService.disGetDepDisGoodsCataGb(map);
        log.info("【depGetDepGoodsCataGb】一级分类数量: {}", disGoodsEntities != null ? disGoodsEntities.size() : 0);

        // 打印一级分类的二级分类数量
//        if (disGoodsEntities != null) {
//            for (int i = 0; i < disGoodsEntities.size() && i < 3; i++) {
//                GbDistributerFatherGoodsEntity gg = disGoodsEntities.get(i);
//                log.info("【depGetDepGoodsCataGb】一级分类[{}]: id={}, name={}, 二级分类数量={}",
//                        i, gg.getGbDistributerFatherGoodsId(), gg.getGbDfgFatherGoodsName(),
//                        gg.getFatherGoodsEntities() != null ? gg.getFatherGoodsEntities().size() : 0);
//
//                // 打印二级分类的商品数量
//                if (gg.getFatherGoodsEntities() != null) {
//                    for (int j = 0; j < gg.getFatherGoodsEntities().size() && j < 3; j++) {
//                        GbDistributerFatherGoodsEntity g = gg.getFatherGoodsEntities().get(j);
//                        log.info("【depGetDepGoodsCataGb】  二级分类[{}]: id={}, name={}, 商品数量={}",
//                                j, g.getGbDistributerFatherGoodsId(), g.getGbDfgFatherGoodsName(),
//                                g.getGbDepartmentDisGoodsEntities() != null ? g.getGbDepartmentDisGoodsEntities().size() : 0);
//                    }
//                }
//            }
//        }

        // 获取商品ID列表
        List<Integer> departmentDisGoodsEntities = gbDepartmentDisGoodsService.queryOnlyDepGoodsIds(map);
        log.info("【depGetDepGoodsCataGb】商品ID数量: {}", departmentDisGoodsEntities != null ? departmentDisGoodsEntities.size() : 0);

        Map<String, Object> mapR = new HashMap<>();
        mapR.put("cataArr", disGoodsEntities);
        mapR.put("depGoodsArr", departmentDisGoodsEntities);

        return R.ok().put("data", mapR);
    }

    /**
     * 分页获取部门商品
     * 接口: /gbdepartmentdisgoods/depGetDepGoodsGbPage
     */
    @Operation(summary = "分页获取部门商品列表", description = "分页查询指定部门关联的商品列表，返回商品详情和分页信息")
    @RequestMapping(value = "/depGetDepGoodsGbPage", method = RequestMethod.POST)
    public R depGetDepGoodsGbPage(
            @RequestParam Integer limit,
            @RequestParam Integer page,
            @RequestParam Integer depId) {
        Map<String, Object> map = new HashMap<>();
        map.put("depId", depId);
        map.put("pull", 0);

        // 1. 获取总数
        int total = gbDepartmentDisGoodsService.queryDepGoodsCount(map);

        // 2. 获取当前页数据
        map.put("status", 4);
        map.put("limit", limit);
        map.put("offset", (page - 1) * limit);
        List<GbDepartmentDisGoodsEntity> currentPageList = gbDepartmentDisGoodsService.depQueryDepGoodsWithOrderForAi(map);

        // 3. 返回分页数据
        Map<String, Object> pageMap = new HashMap<>();
        pageMap.put("totalCount", total);
        pageMap.put("pageSize", limit);
        pageMap.put("totalPage", (total + limit - 1) / limit);
        pageMap.put("currPage", page);
        pageMap.put("list", currentPageList);

        return R.ok().put("page", pageMap);
    }

    /**
     * 获取批发商商品分类和商品ID列表
     * 接口: /gbdepartmentdisgoods/disGetDepGoodsCataGb
     */
    @Operation(summary = "获取批发商商品分类", description = "获取批发商关联的商品分类树，以及商品ID列表")
    @RequestMapping(value = "/disGetDepGoodsCataGb", method = RequestMethod.POST)
    public R disGetDepGoodsCataGb(
            @RequestParam Integer disId,
            @RequestParam(required = false) Integer goodsType) {
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);

        if (goodsType != null && goodsType < 99) {
            map.put("goodsType", goodsType);
        } else {
            if (goodsType != null && goodsType == 101) {
                map.put("fresh", 1);
            } else if (goodsType != null && goodsType == 102) {
                map.put("pull", 1);
            }
        }

        log.info("【disGetDepGoodsCataGb】查询参数: disId={}, goodsType={}", disId, goodsType);

        // 获取分类
        List<GbDistributerFatherGoodsEntity> disGoodsEntities = gbDepartmentDisGoodsService.disGetDepDisGoodsCataGb(map);
        log.info("【disGetDepGoodsCataGb】一级分类数量: {}", disGoodsEntities != null ? disGoodsEntities.size() : 0);

        // 获取商品ID列表
        List<Integer> disGoodsIds = gbDepartmentDisGoodsService.queryOnlyDisGoodsIds(map);
        log.info("【disGetDepGoodsCataGb】商品ID数量: {}", disGoodsIds != null ? disGoodsIds.size() : 0);

        Map<String, Object> mapR = new HashMap<>();
        mapR.put("cataArr", disGoodsEntities);
        mapR.put("disGoodsArr", disGoodsIds);

        return R.ok().put("data", mapR);
    }

    /**
     * 按批发商分页获取商品
     * 接口: /gbdepartmentdisgoods/disGetDepGoodsGbPage
     */
    @Operation(summary = "按批发商分页获取商品列表", description = "分页查询指定批发商关联的商品列表")
    @RequestMapping(value = "/disGetDepGoodsGbPage", method = RequestMethod.POST)
    public R disGetDepGoodsGbPage(
            @RequestParam Integer limit,
            @RequestParam Integer page,
            @RequestParam Integer disId,
            @RequestParam(required = false) Integer goodsType) {
        
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        if (goodsType != null) {
            map.put("goodsType", goodsType);
        }
        
        // 1. 获取总数
        int total = gbDepartmentDisGoodsService.queryDisGoodsCount(map);
        
        // 2. 获取当前页数据
        map.put("limit", limit);
        map.put("offset", (page - 1) * limit);
        TreeSet<GbDistributerGoodsEntity> currentPageSet = gbDepartmentDisGoodsService.disQueryDisGoodsWithOrderForAiTree(map);
        
        // 3. 返回分页数据
        Map<String, Object> pageMap = new HashMap<>();
        pageMap.put("totalCount", total);
        pageMap.put("pageSize", limit);
        pageMap.put("totalPage", (total + limit - 1) / limit);
        pageMap.put("currPage", page);
        pageMap.put("list", currentPageSet);
        
        return R.ok().put("page", pageMap);
    }

    /**
     * 订货端部门库存调整（制作 / 损耗 / 退货 / 废弃），替代原四个独立接口。
     * <p>请求体：{@code { "kind": "produce|loss|return|waste", "stock": { ...GbDepartmentGoodsStockEntity 字段 } }}</p>
     * <p>成功时 {@code data} 统一为 Map：{@code disGoods} 必有，且与 {@link #depGetDepGoodsGbPage} 返回的 {@code page.list} 中单条
     * 部门商品结构一致（同一套 depQueryDepGoodsWithOrderForAi）；{@code id}（库存减少记录主键）仅在 loss、return 时返回。</p>
     */
    @Operation(summary = "部门库存调整（统一）", description = "kind：produce 制作、loss 损耗、return 退货、waste 废弃；stock 为部门库存实体。")
    @RequestMapping(value = "/saveDepGoodsStockAdjust", method = RequestMethod.POST)
    public R saveDepGoodsStockAdjust(@RequestBody GbDepGoodsStockAdjustRequest request) {
        GbDepGoodsStockAdjustResult result = gbDepGoodsStockService.adjustDepGoodsStock(request);
        if (!result.isOk()) {
            return R.error(result.getCode(), result.getMessage());
        }
        return R.ok().put("data", result.getData());
    }

    /**
     * 订货习惯提醒分页（以历史到货订单推断间隔与习惯订货量；辅以库存偏多、损耗与废弃偏多提示）。
     */
    @Operation(summary = "订货习惯提醒分页", description = "与 depGetDepGoodsGbPage 相同返回顶层 page；list 中单条含 aiHabitIntervalDays、aiNextHabitOrderDate、aiShouldRemindToday、aiAuxHints")
    @RequestMapping(value = "/depReorderReminderPage", method = RequestMethod.POST)
    public R depReorderReminderPage(
            @RequestParam Integer depId,
            @RequestParam Integer page,
            @RequestParam Integer limit,
            @RequestParam(required = false) Integer windowDays,
            @RequestParam(required = false) Integer minTimes) {
        Map<String, Object> payload =
                gbDepartmentReorderReminderService.depReorderReminderPage(depId, page, limit, windowDays, minTimes);
        return R.ok().put("page", payload != null ? payload.get("page") : null);
    }

}
