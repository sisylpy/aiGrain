package com.nongxinle.controller;

import com.nongxinle.dto.GbDepGoodsStockAdjustRequest;
import com.nongxinle.dto.GbDepGoodsStockAdjustResult;
import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDepartmentDisGoodsService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

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
     * <p>成功时 {@code data} 统一为 Map：{@code disGoods} 必有；{@code id}（库存减少记录主键）仅在 loss、return 时返回。</p>
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




}
