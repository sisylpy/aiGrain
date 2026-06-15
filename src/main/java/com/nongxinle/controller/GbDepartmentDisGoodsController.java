package com.nongxinle.controller;

import com.nongxinle.dto.GbDepGoodsStockAdjustRequest;
import com.nongxinle.dto.GbDepGoodsStockAdjustResult;
import com.nongxinle.entity.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.service.GbDepartmentDisGoodsService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDepartmentReorderReminderService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.service.GbJjOrderPurchaseLinkService;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    @Autowired
    private GbDepartmentOrdersService gbDepartmentOrdersService;
    @Autowired
    private GbJjOrderPurchaseLinkService gbJjOrderPurchaseLinkService;
    @Autowired
    private GbDistributerPurchaseGoodsService gbDistributerPurchaseGoodsService;
    @Autowired
    private GbDistributerPurchaseBatchService gbDistributerPurchaseBatchService;




    @RequestMapping(value = "/disGetDepGoodsGbPageWithSupplier")
    @ResponseBody
    public R disGetDepGoodsGbPageWithSupplier(Integer limit, Integer page, Integer disId, Integer supplierId) {
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("supplierId", supplierId);
        System.out.println("amappaaappa" + map);

        // 1. 获取总数
        List<Integer > disGoodsIds =   gbDepartmentDisGoodsService.queryOnlyDisGoodsIds(map);

        // 2. 获取当前页数据
        map.put("status", 4);
        map.put("date", formatWhatDay(0));
        map.put("limit", limit);
        map.put("offset", (page - 1) * limit);
        log.info("查询参数: limit={}, offset={}", limit, (page - 1) * limit);
        log.info("map查询: {}", map);
        List<GbDistributerGoodsEntity> currentPageList = gbDepartmentDisGoodsService.disQueryDisGoodsWithOrderForAiTree(map);
        log.info("当前页数据量: {}", currentPageList.size());

        // 4. 处理每个商品的提示文本
//        for(GbDistributerGoodsEntity distributerGoodsEntity: currentPageList){
//            gbDistributerGoodsService.getStockTotal(distributerGoodsEntity);
//        }
        log.info("最终返回数据量: {}", currentPageList.size());

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
     * 获取批发商商品分类和商品ID列表
     * 接口: /gbdepartmentdisgoods/disGetDepGoodsCataGb
     */
    @Operation(summary = "获取批发商商品分类", description = "获取批发商关联的商品分类树，以及商品ID列表")
    @RequestMapping(value = "/storeGetDepGoodsCataGb", method = RequestMethod.POST)
    public R storeGetDepGoodsCataGb(
            @RequestParam Integer depFatherId,
            @RequestParam(required = false) Integer goodsType) {
        Map<String, Object> map = new HashMap<>();
        map.put("depFatherId", depFatherId);

        if (goodsType != null && goodsType < 99) {
            map.put("goodsType", goodsType);
        } else {
            if (goodsType != null && goodsType == 101) {
                map.put("fresh", 1);
            } else if (goodsType != null && goodsType == 102) {
                map.put("pull", 1);
            }
        }

        log.info("【disGetDepGoodsCataGb】查询参数: disId={}, goodsType={}", depFatherId, goodsType);

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
        List<GbDistributerGoodsEntity> currentPageList = gbDepartmentDisGoodsService.disQueryDisGoodsWithOrderForAiTree(map);

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
     * 按批发商分页获取商品
     * 接口: /gbdepartmentdisgoods/disGetDepGoodsGbPage
     */
    @Operation(summary = "按批发商分页获取商品列表", description = "分页查询指定批发商关联的商品列表")
    @RequestMapping(value = "/storeGetDepGoodsGbPage", method = RequestMethod.POST)
    public R storeGetDepGoodsGbPage(
            @RequestParam Integer limit,
            @RequestParam Integer page,
            @RequestParam Integer depFatherId,
            @RequestParam(required = false) Integer goodsType) {
        
        Map<String, Object> map = new HashMap<>();
        map.put("depFatherId", depFatherId);
        if (goodsType != null) {
            map.put("goodsType", goodsType);
        }
        // 1. 获取总数
        int total = gbDepartmentDisGoodsService.queryDepGoodsCount(map);
        
        // 2. 获取当前页数据
        map.put("limit", limit);
        map.put("offset", (page - 1) * limit);
        List<GbDepartmentDisGoodsEntity> currentPageList = gbDepartmentDisGoodsService.disQueryDepGoodsWithOrderForAiTree(map);

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
     * 根据部门商品ID查询部门商品详情
     * 接口: /gbdepartmentdisgoods/getDepGoodsDetail
     */
    @Operation(summary = "查询部门商品详情", description = "根据部门商品主键ID查询单个部门商品的详细信息")
    @RequestMapping(value = "/getDepGoodsDetail", method = RequestMethod.POST)
    public R getDepGoodsDetail(
            @Parameter(description = "部门商品ID", required = true)
            @RequestParam Integer depGoodsId) {
        log.info("【getDepGoodsDetail】查询部门商品详情: depGoodsId={}", depGoodsId);

        GbDepartmentDisGoodsEntity entity = gbDepartmentDisGoodsService.queryDepGoodsDetailById(depGoodsId);
        if (entity == null) {
            return R.error(-1, "部门商品不存在");
        }
        return R.ok().put("data", entity);
    }

    /**
     * 修改部门商品
     * 接口: /gbdepartmentdisgoods/updateDepGoods
     *
     * <p>级联规则：
     * <ul>
     *   <li><b>gbDdgDepGoodsPullOff 变更</b>：直接保存，不触发任何级联逻辑。</li>
     *   <li><b>gbDdgGoodsType 或 gbDdgGbDepartmentId 变更</b>：查询该部门商品下所有 status=0（新建）的订单，
     *       删除旧订单并重新创建新订单，新订单的 gbDoToDepartmentId 改为 gbDdgGbDepartmentId，
     *       并按 {@code createDepartmentOrderWithNewDepDisGoods} 规则关联采购商品行。</li>
     * </ul>
     */
    @Operation(summary = "修改部门商品", description = "支持级联处理：下架直接保存；出货方式变更会重建status=0的订单并更新目标部门")
    @RequestMapping(value = "/updateDepGoods", method = RequestMethod.POST)
    public R updateDepGoods(@RequestBody GbDepartmentDisGoodsEntity entity) {
        Integer depGoodsId = entity.getGbDepartmentDisGoodsId();
        log.info("【updateDepGoods】修改部门商品: depGoodsId={}", depGoodsId);

        if (depGoodsId == null) {
            return R.error(-1, "部门商品ID不能为空");
        }

        GbDepartmentDisGoodsEntity existing = gbDepartmentDisGoodsService.getById(depGoodsId);
        if (existing == null) {
            return R.error(-1, "部门商品不存在");
        }

        boolean pullOffChanged = entity.getGbDdgDepGoodsPullOff() != null
                && !Objects.equals(entity.getGbDdgDepGoodsPullOff(), existing.getGbDdgDepGoodsPullOff());
        boolean goodsTypeChanged = entity.getGbDdgGoodsType() != null
                && !Objects.equals(entity.getGbDdgGoodsType(), existing.getGbDdgGoodsType());
        boolean gbDepartmentIdChanged = entity.getGbDdgGbDepartmentId() != null
                && !Objects.equals(entity.getGbDdgGbDepartmentId(), existing.getGbDdgGbDepartmentId());
        Integer targetGbDepartmentId = entity.getGbDdgGbDepartmentId() != null
                ? entity.getGbDdgGbDepartmentId() : existing.getGbDdgGbDepartmentId();
        Integer targetGoodsType = entity.getGbDdgGoodsType() != null
                ? entity.getGbDdgGoodsType() : existing.getGbDdgGoodsType();

        boolean orderProcurementMismatch = false;
        List<GbDepartmentOrdersEntity> pendingOrders = null;
        if (!pullOffChanged && (goodsTypeChanged || gbDepartmentIdChanged
                || entity.getGbDdgGbDepartmentId() != null || entity.getGbDdgGoodsType() != null)) {
            pendingOrders = gbDepartmentOrdersService.list(
                    new LambdaQueryWrapper<GbDepartmentOrdersEntity>()
                            .eq(GbDepartmentOrdersEntity::getGbDoDepDisGoodsId, depGoodsId)
                            .eq(GbDepartmentOrdersEntity::getGbDoStatus, GbConstants.DepartmentOrderStatus.NEW)
            );
            for (GbDepartmentOrdersEntity pendingOrder : pendingOrders) {
                if (!Objects.equals(pendingOrder.getGbDoToDepartmentId(), targetGbDepartmentId)
                        || !Objects.equals(pendingOrder.getGbDoGoodsType(), targetGoodsType)) {
                    orderProcurementMismatch = true;
                    break;
                }
                Integer purchaseGoodsId = pendingOrder.getGbDoPurchaseGoodsId();
                if (purchaseGoodsId != null && purchaseGoodsId != -1 && targetGbDepartmentId != null) {
                    GbDistributerPurchaseGoodsEntity purchaseGoods =
                            gbDistributerPurchaseGoodsService.getById(purchaseGoodsId);
                    if (purchaseGoods != null
                            && !Objects.equals(purchaseGoods.getGbDpgPurchaseDepartmentId(), targetGbDepartmentId)) {
                        orderProcurementMismatch = true;
                        break;
                    }
                }
            }
        }
        boolean pendingOrderRebuildNeeded = goodsTypeChanged || gbDepartmentIdChanged || orderProcurementMismatch;

        // 分支A：下架变更 → 直接保存，不级联
        if (pullOffChanged) {
            log.info("【updateDepGoods】下架变更, 直接保存: depGoodsId={}, pullOff {} -> {}",
                    depGoodsId, existing.getGbDdgDepGoodsPullOff(), entity.getGbDdgDepGoodsPullOff());
            gbDepartmentDisGoodsService.updateById(entity);
            GbDepartmentDisGoodsEntity result = gbDepartmentDisGoodsService.getById(depGoodsId);
            return R.ok().put("data", result);
        }

        // 分支B：出货方式或采购部门变更 → 需级联处理 status=0 的订单
        if (pendingOrderRebuildNeeded) {
            log.info("【updateDepGoods】出货方式/采购部门变更, 需级联处理订单: depGoodsId={}, goodsType {} -> {}, gbDepartmentId {} -> {}, orderMismatch={}",
                    depGoodsId, existing.getGbDdgGoodsType(), entity.getGbDdgGoodsType(),
                    existing.getGbDdgGbDepartmentId(), entity.getGbDdgGbDepartmentId(), orderProcurementMismatch);

            // 1. 先更新部门商品（MyBatis-Plus 只更新非null字段）
            gbDepartmentDisGoodsService.updateById(entity);

            // 2. 重新查询，获取最新的 gbDdgGbDepartmentId
            GbDepartmentDisGoodsEntity updatedDepGoods = gbDepartmentDisGoodsService.getById(depGoodsId);
            Integer newGbDepartmentId = updatedDepGoods.getGbDdgGbDepartmentId();
            Integer newGoodsType = updatedDepGoods.getGbDdgGoodsType();

            // 3. 校验 gbDdgGbDepartmentId 必传
            if (newGbDepartmentId == null) {
                return R.error(-1, "变更出货方式或采购部门时，gbDdgGbDepartmentId（部门ID）不能为空");
            }

            // 4. 查询该部门商品下所有 status=0（新建）的订单
            if (pendingOrders == null) {
                pendingOrders = gbDepartmentOrdersService.list(
                        new LambdaQueryWrapper<GbDepartmentOrdersEntity>()
                                .eq(GbDepartmentOrdersEntity::getGbDoDepDisGoodsId, depGoodsId)
                                .eq(GbDepartmentOrdersEntity::getGbDoStatus, GbConstants.DepartmentOrderStatus.NEW)
                );
            }
            log.info("【updateDepGoods】找到 status=0 的订单数: {}", pendingOrders.size());

            // 5. 遍历处理：解绑旧采购行、删除旧订单、创建新订单
            for (GbDepartmentOrdersEntity oldOrder : pendingOrders) {
                Integer oldOrderId = oldOrder.getGbDepartmentOrdersId();
                log.info("【updateDepGoods】处理订单: oldOrderId={}, oldToDepId={}, oldGoodsType={}, oldPurGoodsId={}",
                        oldOrderId, oldOrder.getGbDoToDepartmentId(), oldOrder.getGbDoGoodsType(),
                        oldOrder.getGbDoPurchaseGoodsId());

                detachPendingOrderFromPurchaseGoods(oldOrder);
                gbDepartmentOrdersService.removeById(oldOrderId);

                // 构造新订单，从旧订单复制字段
                GbDepartmentOrdersEntity newOrder = new GbDepartmentOrdersEntity();
                copyOrderFieldsForRebuild(oldOrder, newOrder);

                // 覆写关键字段
                newOrder.setGbDoToDepartmentId(newGbDepartmentId);
                newOrder.setGbDoGoodsType(newGoodsType);
                newOrder.setGbDoOrderType(newGoodsType);
                newOrder.setGbDoStatus(GbConstants.DepartmentOrderStatus.NEW);
                newOrder.setGbDoBuyStatus(GbConstants.OrderBuyStatus.NEW);
                newOrder.setGbDoPurchaseGoodsId(-1);

                GbDistributerGoodsEntity disGoods = gbDistributerGoodsService.getById(newOrder.getGbDoDisGoodsId());
                if (disGoods == null) {
                    log.warn("【updateDepGoods】分销商商品不存在, 跳过订单重建: disGoodsId={}, oldOrderId={}",
                            newOrder.getGbDoDisGoodsId(), oldOrderId);
                    continue;
                }
                gbJjOrderPurchaseLinkService.applyDisGoodsCategoryHierarchyToOrder(
                        newOrder, disGoods.getGbDgDfgGoodsFatherId());
                gbJjOrderPurchaseLinkService.applyJjOrderTimestamps(newOrder);

                gbDepartmentOrdersService.save(newOrder);

                gbJjOrderPurchaseLinkService.resolvePurchaseGoodsLineForJjOrder(
                        newOrder,
                        disGoods,
                        GbJjOrderPurchaseLinkService.PurchaseGoodsLinkMode.MERGE_BY_PUR_DEPARTMENT);

                if (disGoods.getGbDgGbSupplierId() != null && disGoods.getGbDgGbSupplierId() != -1) {
                    gbJjOrderPurchaseLinkService.ensureSupplierPurchaseBatchForJjOrder(newOrder, disGoods);
                }

                log.info("【updateDepGoods】新订单创建成功: newOrderId={}, gbDoToDepartmentId={}, gbDoPurchaseGoodsId={}",
                        newOrder.getGbDepartmentOrdersId(), newGbDepartmentId, newOrder.getGbDoPurchaseGoodsId());
            }
        } else {
            // 普通字段变更，直接保存
            gbDepartmentDisGoodsService.updateById(entity);
        }

        GbDepartmentDisGoodsEntity result = gbDepartmentDisGoodsService.getById(depGoodsId);
        return R.ok().put("data", result);
    }

    /**
     * 删除待处理订单前，从原采购商品行解绑（与 deleteOrderGb 一致，不含供货商通知）。
     */
    private void detachPendingOrderFromPurchaseGoods(GbDepartmentOrdersEntity order) {
        if (order == null || order.getGbDoPurchaseGoodsId() == null || order.getGbDoPurchaseGoodsId() == -1) {
            return;
        }
        GbDistributerPurchaseGoodsEntity purchaseGoods =
                gbDistributerPurchaseGoodsService.getById(order.getGbDoPurchaseGoodsId());
        if (purchaseGoods == null) {
            return;
        }
        Integer ordersAmount = purchaseGoods.getGbDpgOrdersAmount();
        if (ordersAmount != null && ordersAmount > 1) {
            purchaseGoods.setGbDpgOrdersAmount(ordersAmount - 1);
            BigDecimal subtract = new BigDecimal(purchaseGoods.getGbDpgQuantity())
                    .subtract(new BigDecimal(order.getGbDoQuantity()));
            purchaseGoods.setGbDpgQuantity(subtract.toString());
            gbDistributerPurchaseGoodsService.updateById(purchaseGoods);
            return;
        }
        Integer batchId = purchaseGoods.getGbDpgBatchId();
        if (batchId != null) {
            Map<String, Object> mapBatch = new HashMap<>();
            mapBatch.put("batchId", batchId);
            List<GbDistributerPurchaseGoodsEntity> batchGoods =
                    gbDistributerPurchaseGoodsService.queryOnlyPurGoods(mapBatch);
            if (batchGoods.size() == 1) {
                gbDistributerPurchaseBatchService.removeById(batchId);
            }
        }
        gbDistributerPurchaseGoodsService.removeById(purchaseGoods.getGbDistributerPurchaseGoodsId());
    }

    /**
     * 从旧订单复制字段到新订单（订单重建用，不复制主键和采购/状态相关字段）。
     */
    private void copyOrderFieldsForRebuild(GbDepartmentOrdersEntity source, GbDepartmentOrdersEntity target) {
        target.setGbDoNxGoodsId(source.getGbDoNxGoodsId());
        target.setGbDoNxGoodsFatherId(source.getGbDoNxGoodsFatherId());
        target.setGbDoDisGoodsId(source.getGbDoDisGoodsId());
        target.setGbDoDisGoodsFatherId(source.getGbDoDisGoodsFatherId());
        target.setGbDoDisGoodsGrandId(source.getGbDoDisGoodsGrandId());
        target.setGbDoDisGoodsGreatId(source.getGbDoDisGoodsGreatId());
        target.setGbDoDepDisGoodsId(source.getGbDoDepDisGoodsId());
        target.setGbDoQuantity(source.getGbDoQuantity());
        target.setGbDoStandard(source.getGbDoStandard());
        target.setGbDoRemark(source.getGbDoRemark());
        target.setGbDoWeight(source.getGbDoWeight());
        target.setGbDoPrice(source.getGbDoPrice());
        target.setGbDoSubtotal(source.getGbDoSubtotal());
        target.setGbDoDepartmentId(source.getGbDoDepartmentId());
        target.setGbDoDepartmentFatherId(source.getGbDoDepartmentFatherId());
        target.setGbDoDistributerId(source.getGbDoDistributerId());
        target.setGbDoOrderUserId(source.getGbDoOrderUserId());
        target.setGbDoSellingPrice(source.getGbDoSellingPrice());
        target.setGbDoSellingSubtotal(source.getGbDoSellingSubtotal());
        target.setGbDoIsAgent(source.getGbDoIsAgent());
        target.setGbDoNxGoodsGrandId(source.getGbDoNxGoodsGrandId());
        target.setGbDoNxGoodsGreatId(source.getGbDoNxGoodsGreatId());
        target.setGbDoPrintStandard(source.getGbDoPrintStandard());
        target.setGbDoNxDistributerId(source.getGbDoNxDistributerId());
        target.setGbDoNxDistributerGoodsId(source.getGbDoNxDistributerGoodsId());
        target.setGbDoNxDepartmentOrderId(source.getGbDoNxDepartmentOrderId());
        target.setGbDoDsStandardId(source.getGbDoDsStandardId());
        target.setGbDoDsStandardScale(source.getGbDoDsStandardScale());
        target.setGbDoGoodsName(source.getGbDoGoodsName());
    }

    /**
     * 订货端部门库存调整（制作 / 损耗 / 退货 / 废弃 / 员工餐），替代原四个独立接口。
     * <p>请求体：{@code { "kind": "produce|loss|return|waste|employee_meal", "stock": { ...GbDepartmentGoodsStockEntity 字段 } }}</p>
     * <p>员工餐须传 {@code stock.gbDgsMyEmployeeMealWeight}（本次使用数量，基础单位）。</p>
     * <p>成功时 {@code data} 统一为 Map：{@code disGoods} 必有；{@code stock} 为更新后的批次（含
     * {@code goodsStockReduceEntityList}，type=6 员工餐含 {@code gbDgsrEmployeeMealWeight}）；
     * {@code id}（库存减少记录主键）在 loss、return、employee_meal 时返回。</p>
     */
    @Operation(summary = "部门库存调整（统一）", description = "kind：produce 制作、loss 损耗、return 退货、waste 废弃、employee_meal 员工餐；stock 为部门库存实体。")
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
