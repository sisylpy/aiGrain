package com.nongxinle.controller;

import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.entity.GbDepartmentGoodsDailyEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockReduceEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDepartmentDisGoodsService;
import com.nongxinle.service.GbDepartmentGoodsDailyService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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
    @Autowired
    private GbDepartmentGoodsDailyService gbDepGoodsDailyService;
    @Autowired
    private GbDistributerGoodsService disGoodsService;
    @Autowired
    private GbDepartmentGoodsStockReduceService gbDepartmentStockReduceService;

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
     * 订货端修改制作成本
     */
    @RequestMapping(value = "/saveDepProduceGoodsStock", method = RequestMethod.POST)
    public R saveDepProduceGoodsStock(@RequestBody GbDepartmentGoodsStockEntity stock) {
        Integer gbDepartmentGoodsStockId = stock.getGbDepartmentGoodsStockId();
        GbDepartmentGoodsStockEntity gbDepartmentGoodsStockEntity = gbDepGoodsStockService.getById(gbDepartmentGoodsStockId);
        System.out.println("sotireeeeee" + gbDepartmentGoodsStockEntity.getGbDgsRestWeight());
        if (new BigDecimal(gbDepartmentGoodsStockEntity.getGbDgsRestWeight()).compareTo(BigDecimal.ZERO) == 0) {
            return R.error(-1, "请刷新数据");
        } else {
            changeDepartmentStock(stock, "produce");
            Integer departmentId = stock.getGbDgsGbDepartmentId();
            Integer disGoodsId = stock.getGbDgsGbDisGoodsId();
            Map<String, Object> mapD = new HashMap<>();
            mapD.put("depId", departmentId);
            mapD.put("disGoodsId", disGoodsId);
            mapD.put("orderStatus", 3);
            mapD.put("restWeight", 0);
            GbDepartmentDisGoodsEntity gbDepartmentDisGoodsEntity = gbDepartmentDisGoodsService.queryDepartmentGoodsForAi(mapD);
            return R.ok().put("data", gbDepartmentDisGoodsEntity);
        }
    }

    @RequestMapping(value = "/saveDepLossGoodsStock", method = RequestMethod.POST)
    public R saveDepLossGoodsStock(@RequestBody GbDepartmentGoodsStockEntity stock) {

        Integer gbDepartmentGoodsStockId = stock.getGbDepartmentGoodsStockId();
        GbDepartmentGoodsStockEntity gbDepartmentGoodsStockEntity = gbDepGoodsStockService.getById(gbDepartmentGoodsStockId);
        if (new BigDecimal(gbDepartmentGoodsStockEntity.getGbDgsRestWeight()).compareTo(BigDecimal.ZERO) == 0) {
            return R.error(-1, "请刷新数据");
        } else {
            GbDepartmentGoodsStockReduceEntity loss = changeDepartmentStock(stock, "loss");
            Map<String, Object> mapD = new HashMap<>();
            mapD.put("depId", stock.getGbDgsGbDepartmentId());
            mapD.put("disGoodsId", stock.getGbDgsGbDisGoodsId());
            mapD.put("orderStatus", 3);
            mapD.put("restWeight", 0);
            GbDepartmentDisGoodsEntity gbDepartmentDisGoodsEntity = gbDepartmentDisGoodsService.queryDepartmentGoodsForAi(mapD);
            Map<String, Object> map = new HashMap<>();
            map.put("disGoods", gbDepartmentDisGoodsEntity);
            map.put("id", loss.getGbDepartmentGoodsStockReduceId());
            return R.ok().put("data", map);
        }
    }

    @RequestMapping(value = "/saveDepReturnGoodsStock", method = RequestMethod.POST)
    public R saveDepReturnGoodsStock(@RequestBody GbDepartmentGoodsStockEntity stock) {

        Integer gbDepartmentGoodsStockId = stock.getGbDepartmentGoodsStockId();
        GbDepartmentGoodsStockEntity gbDepartmentGoodsStockEntity = gbDepGoodsStockService.getById(gbDepartmentGoodsStockId);
        if (new BigDecimal(gbDepartmentGoodsStockEntity.getGbDgsRestWeight()).compareTo(BigDecimal.ZERO) == 0) {
            return R.error(-1, "请刷新数据");
        } else {

            Integer gbDgsGbFromDepartmentId = stock.getGbDgsGbFromDepartmentId();
            Integer gbDgsGbDisGoodsId = stock.getGbDgsGbDisGoodsId();
            GbDistributerGoodsEntity gbDistributerGoodsEntity = disGoodsService.getById(gbDgsGbDisGoodsId);
            Integer departmentId = gbDistributerGoodsEntity.getGbDgGbDepartmentId();
            System.out.println("gbDgsGbFromDepartmentId" + gbDgsGbFromDepartmentId);
            System.out.println("departmentId" + departmentId);
            if (gbDgsGbFromDepartmentId != departmentId) {
                return R.error(-1, "这个批次已修改出货部门，不能退货");
            } else {
                GbDepartmentGoodsStockReduceEntity reduceEntity = changeDepartmentStock(stock, "return");

                Map<String, Object> mapD = new HashMap<>();
                mapD.put("depId", stock.getGbDgsGbDepartmentId());
                mapD.put("disGoodsId", reduceEntity.getGbDgsrDisGoodsId());
                mapD.put("orderStatus", 3);
                mapD.put("restWeight", 0);
                GbDepartmentDisGoodsEntity gbDepartmentDisGoodsEntity = gbDepartmentDisGoodsService.queryDepartmentGoodsForAi(mapD);

                Map<String, Object> map = new HashMap<>();
                map.put("disGoods", gbDepartmentDisGoodsEntity);
                map.put("id", reduceEntity.getGbDepartmentGoodsStockReduceId());
                return R.ok().put("data", map);
            }
        }
    }

    /**
     * 保存部门废弃商品库存
     */
    @RequestMapping(value = "/saveDepWasteGoodsStock", method = RequestMethod.POST)
    public R saveDepWasteGoodsStock(@RequestBody GbDepartmentGoodsStockEntity stock) {
        Integer gbDepartmentGoodsStockId = stock.getGbDepartmentGoodsStockId();
        GbDepartmentGoodsStockEntity gbDepartmentGoodsStockEntity = gbDepGoodsStockService.getById(gbDepartmentGoodsStockId);
        if (new BigDecimal(gbDepartmentGoodsStockEntity.getGbDgsRestWeight()).compareTo(BigDecimal.ZERO) == 0) {
            return R.error(-1, "请刷新数据");
        } else {
            changeDepartmentStock(stock, "waste");
            Map<String, Object> mapD = new HashMap<>();
            mapD.put("depId", stock.getGbDgsGbDepartmentId());
            mapD.put("disGoodsId", stock.getGbDgsGbDisGoodsId());
            mapD.put("orderStatus", 3);
            mapD.put("restWeight", 0);
            GbDepartmentDisGoodsEntity gbDepartmentDisGoodsEntity = gbDepartmentDisGoodsService.queryDepartmentGoodsForAi(mapD);
            return R.ok().put("data", gbDepartmentDisGoodsEntity);
        }
    }

    /**
     * 安全地将String转换为BigDecimal，null或空字符串返回默认值
     */
    private BigDecimal toBigDecimal(String value, String defaultVal) {
        if (value == null || value.trim().isEmpty()) {
            return new BigDecimal(defaultVal);
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return new BigDecimal(defaultVal);
        }
    }

    /**
     * 修改部门库存（公共方法）
     */
    private GbDepartmentGoodsStockReduceEntity changeDepartmentStock(GbDepartmentGoodsStockEntity stock, String what) {
        GbDepartmentGoodsStockReduceEntity reduceEntity = new GbDepartmentGoodsStockReduceEntity();
        System.out.println("whastttt" + what);
        BigDecimal myChangeWeight = new BigDecimal("0");
        BigDecimal myChangeSubtotal = new BigDecimal(0);

        BigDecimal newAfterProfitSubtotal = new BigDecimal(0);
        BigDecimal salesSubtotal = new BigDecimal(0);
        BigDecimal profitSubtotal = new BigDecimal((0));

        Integer gbDgsGbDisGoodsId = stock.getGbDgsGbDisGoodsId();
        GbDistributerGoodsEntity distributerGoodsEntity = disGoodsService.getById(gbDgsGbDisGoodsId);
        Integer gbDgGoodsInventoryType = distributerGoodsEntity.getGbDgGoodsInventoryType();

        // 利润单价
        String priceStr = stock.getGbDgsPrice();
        BigDecimal costPrice = (priceStr != null && !priceStr.trim().isEmpty()) ? new BigDecimal(priceStr) : BigDecimal.ZERO; // 成本单价

        // 1.1 如果是损耗接口
        if (what.equals("loss")) {
            // 转换数据
            myChangeWeight = toBigDecimal(stock.getGbDgsMyLossWeight(), "0").setScale(1, BigDecimal.ROUND_HALF_UP); // 最新提交待损耗数量
            myChangeSubtotal = myChangeWeight.multiply(costPrice).setScale(2, BigDecimal.ROUND_HALF_UP); // 总损耗成本

            // 销售利润=利润-成本
            if (stock.getGbDgsSellingPrice() != null && !stock.getGbDgsSellingPrice().trim().isEmpty() && !stock.getGbDgsSellingPrice().equals("-1")) {
                BigDecimal stockAfterProfitSubtotal = toBigDecimal(stock.getGbDgsAfterProfitSubtotal(), "0"); // 总的销售利润
                newAfterProfitSubtotal = stockAfterProfitSubtotal.subtract(myChangeSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP);
                stock.setGbDgsAfterProfitSubtotal(newAfterProfitSubtotal.toString());
            }

            // update
            BigDecimal allWeight = toBigDecimal(stock.getGbDgsLossWeight(), "0").add(myChangeWeight).setScale(1, BigDecimal.ROUND_HALF_UP); // 总损耗数量
            BigDecimal allSubtotal = toBigDecimal(stock.getGbDgsLossSubtotal(), "0").add(myChangeSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP); // 总损耗数量
            stock.setGbDgsLossWeight(allWeight.toString());
            stock.setGbDgsLossSubtotal(allSubtotal.toString());

            reduceEntity = addDepGoodsStockReduceEntity(stock, "loss", gbDgGoodsInventoryType, myChangeWeight, myChangeSubtotal);
            subscribeDepDisGoodsTotal(myChangeWeight, myChangeSubtotal, stock.getGbDgsGbDepDisGoodsId());
            updateDepGoodsDailyEntity(stock, "loss", myChangeWeight, myChangeSubtotal);

        }

        // 1.2 如果是制作接口
        if (what.equals("produce")) {
            // 转换数据
            myChangeWeight = toBigDecimal(stock.getGbDgsMyProduceWeight(), "0").setScale(1, BigDecimal.ROUND_HALF_UP); // 最新提交待损耗数量
            myChangeSubtotal = myChangeWeight.multiply(costPrice).setScale(2, BigDecimal.ROUND_HALF_UP); // 总制作成本

            // update
            BigDecimal allWeight = toBigDecimal(stock.getGbDgsProduceWeight(), "0").add(myChangeWeight).setScale(1, BigDecimal.ROUND_HALF_UP); // 总损耗数量
            BigDecimal allSubtotal = toBigDecimal(stock.getGbDgsProduceSubtotal(), "0").add(myChangeSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP); // 总损耗数量
            stock.setGbDgsProduceWeight(allWeight.toString());
            stock.setGbDgsProduceSubtotal(allSubtotal.toString());

            if (!"-1".equals(stock.getGbDgsSellingPrice())) {
                // 利润
                BigDecimal gbDgsBetweenPrice = toBigDecimal(stock.getGbDgsBetweenPrice(), "0"); // 生产利润单价
                BigDecimal newProfitSubtotal = gbDgsBetweenPrice.multiply(myChangeWeight).setScale(1, BigDecimal.ROUND_HALF_UP);
                profitSubtotal = toBigDecimal(stock.getGbDgsProfitSubtotal(), "0").add(newProfitSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP);
                stock.setGbDgsProfitSubtotal(profitSubtotal.toString()); //
                // 销售利润=总利润+利润
                BigDecimal stockAfterProfitSubtotal = toBigDecimal(stock.getGbDgsAfterProfitSubtotal(), "0"); // 总的销售利润
                newAfterProfitSubtotal = stockAfterProfitSubtotal.add(newProfitSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP);
                stock.setGbDgsAfterProfitSubtotal(newAfterProfitSubtotal.toString());

                BigDecimal newSellingSubtotal = toBigDecimal(stock.getGbDgsSellingPrice(), "0").multiply(myChangeWeight);
                salesSubtotal = newSellingSubtotal.add(toBigDecimal(stock.getGbDgsProduceSellingSubtotal(), "0"));
                stock.setGbDgsProduceSellingSubtotal(salesSubtotal.toString());
                // 产生利润的数量
                BigDecimal add = toBigDecimal(stock.getGbDgsProfitWeight(), "0").add(myChangeWeight);
                stock.setGbDgsProfitWeight(add.toString());

            }

            reduceEntity = addDepGoodsStockReduceEntity(stock, "produce", gbDgGoodsInventoryType, myChangeWeight, myChangeSubtotal);
            subscribeDepDisGoodsTotal(myChangeWeight, myChangeSubtotal, stock.getGbDgsGbDepDisGoodsId());
            updateDepGoodsDailyEntity(stock, "produce", myChangeWeight, myChangeSubtotal);

        }

        // 1.3 如果是退货接口
        if (what.equals("return")) {
            // 转换数据
            myChangeWeight = toBigDecimal(stock.getGbDgsMyReturnWeight(), "0").setScale(1, BigDecimal.ROUND_HALF_UP); // 最新提交待损耗数量
            myChangeSubtotal = myChangeWeight.multiply(costPrice).setScale(2, BigDecimal.ROUND_HALF_UP); // 总损耗成本

            if (!"-1".equals(stock.getGbDgsSellingPrice())) {
                BigDecimal stockAfterProfitSubtotal = toBigDecimal(stock.getGbDgsAfterProfitSubtotal(), "0"); // 总的销售利润
                // 销售利润=利润-成本
                profitSubtotal = stockAfterProfitSubtotal.subtract(myChangeSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP);
                stock.setGbDgsAfterProfitSubtotal(profitSubtotal.toString());

            }

            // update
            BigDecimal allWeight = toBigDecimal(stock.getGbDgsReturnWeight(), "0").add(myChangeWeight).setScale(1, BigDecimal.ROUND_HALF_UP); // 总损耗数量
            BigDecimal allSubtotal = toBigDecimal(stock.getGbDgsReturnSubtotal(), "0").add(myChangeSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP); // 总损耗数量
            stock.setGbDgsReturnWeight(allWeight.toString());
            stock.setGbDgsReturnSubtotal(allSubtotal.toString());

            reduceEntity = addDepGoodsStockReduceEntity(stock, what, gbDgGoodsInventoryType, myChangeWeight, myChangeSubtotal);
            subscribeDepDisGoodsTotal(myChangeWeight, myChangeSubtotal, stock.getGbDgsGbDepDisGoodsId());
            updateDepGoodsDailyEntity(stock, what, myChangeWeight, myChangeSubtotal);

        }

        String restWeightStr = stock.getGbDgsRestWeight();
        BigDecimal restWeight = (restWeightStr != null && !restWeightStr.trim().isEmpty()) ? new BigDecimal(restWeightStr) : BigDecimal.ZERO; // 剩余数量
        BigDecimal newRestWeight = restWeight.subtract(myChangeWeight).setScale(1, BigDecimal.ROUND_HALF_UP); // 最新剩余数量
        BigDecimal newRestSubtotal = newRestWeight.multiply(costPrice).setScale(1, BigDecimal.ROUND_HALF_UP); // 最新剩余成本
        stock.setGbDgsRestWeight(newRestWeight.toString());
        stock.setGbDgsRestSubtotal(newRestSubtotal.toString());

        // 1.4 如果是废弃接口
        if (what.equals("waste")) {
            // 转换数据
            BigDecimal wasteWeight = toBigDecimal(stock.getGbDgsMyWasteWeight(), "0"); // 最新提交待损耗数量
            BigDecimal wasteSubtotal = wasteWeight.multiply(costPrice).setScale(1, BigDecimal.ROUND_HALF_UP); // 最新剩余成本

            BigDecimal produceWeight = toBigDecimal(stock.getGbDgsMyProduceWeight(), "0").setScale(1, BigDecimal.ROUND_HALF_UP); // 最新提交待损耗数量
            BigDecimal produceSubtotal = produceWeight.multiply(costPrice).setScale(2, BigDecimal.ROUND_HALF_UP); // 总制作成本
            BigDecimal allWeightProduce = toBigDecimal(stock.getGbDgsProduceWeight(), "0").add(produceWeight).setScale(1, BigDecimal.ROUND_HALF_UP); // 总损耗数量
            BigDecimal allSubtotalProduce = toBigDecimal(stock.getGbDgsProduceSubtotal(), "0").add(produceSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP); // 总损耗数量
            myChangeWeight = wasteWeight.add(produceWeight);
            myChangeSubtotal = wasteSubtotal.add(produceSubtotal);
            if (!"-1".equals(stock.getGbDgsSellingPrice())) {

                // 利润
                BigDecimal gbDgsBetweenPrice = toBigDecimal(stock.getGbDgsBetweenPrice(), "0"); // 生产利润单价
                BigDecimal newProfitSubtotal = gbDgsBetweenPrice.multiply(produceWeight).setScale(1, BigDecimal.ROUND_HALF_UP);
                profitSubtotal = toBigDecimal(stock.getGbDgsProfitSubtotal(), "0").add(newProfitSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP);
                stock.setGbDgsProfitSubtotal(profitSubtotal.toString()); //
                // 销售利润=总利润+利润
                BigDecimal stockAfterProfitSubtotal = toBigDecimal(stock.getGbDgsAfterProfitSubtotal(), "0"); // 总的销售利润
                newAfterProfitSubtotal = stockAfterProfitSubtotal.add(newProfitSubtotal).subtract(wasteSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP);
                stock.setGbDgsAfterProfitSubtotal(newAfterProfitSubtotal.toString());

                BigDecimal newSellingSubtotal = toBigDecimal(stock.getGbDgsSellingPrice(), "0").multiply(produceWeight);
                salesSubtotal = newSellingSubtotal.add(toBigDecimal(stock.getGbDgsProduceSellingSubtotal(), "0"));
                stock.setGbDgsProduceSellingSubtotal(salesSubtotal.toString());
                // 产生利润的数量
                BigDecimal add = toBigDecimal(stock.getGbDgsProfitWeight(), "0").add(produceWeight);
                stock.setGbDgsProfitWeight(add.toString());
            }

            // update
            stock.setGbDgsWasteWeight(wasteWeight.toString());
            stock.setGbDgsWasteSubtotal(wasteSubtotal.toString());
            stock.setGbDgsProduceWeight(allWeightProduce.toString());
            stock.setGbDgsProduceSubtotal(allSubtotalProduce.toString());
            stock.setGbDgsRestWeight("0");
            stock.setGbDgsRestSubtotal("0.0");

            // 获取当前时间戳（毫秒）
            long nowTimestamp = System.currentTimeMillis();
            String nowStr = String.valueOf(nowTimestamp);
            stock.setGbDgsDoWasteFullTime(nowStr);

            subscribeDepDisGoodsTotal(myChangeWeight, myChangeSubtotal, stock.getGbDgsGbDepDisGoodsId());

            updateDepGoodsDailyEntity(stock, what, wasteWeight, wasteSubtotal);
            addDepGoodsStockReduceEntity(stock, what, gbDgGoodsInventoryType, wasteWeight, wasteSubtotal);

            if (produceWeight.compareTo(BigDecimal.ZERO) == 1) {
                addDepGoodsStockReduceEntity(stock, "produce", gbDgGoodsInventoryType, produceWeight, produceSubtotal);
                updateDepGoodsDailyEntity(stock, "produce", produceWeight, produceSubtotal);
            }
        }

        stock.setGbDgsInventoryFullTime(formatWhatFullTime(0));
        stock.setGbDgsInventoryDate(formatWhatDay(0));
        stock.setGbDgsInventoryWeek(getWeekOfYear(0).toString());
        stock.setGbDgsInventoryMonth(formatWhatMonth(0));
        stock.setGbDgsInventoryYear(formatWhatYear(0));

        // 转换showStandardWeight
        if (stock.getGbDgsRestWeightShowStandard() != null && !stock.getGbDgsRestWeightShowStandard().trim().isEmpty()) {
            if (toBigDecimal(stock.getGbDgsRestWeightShowStandard(), "0").compareTo(BigDecimal.ZERO) > 0) {
                Integer gbDgsGbDepDisGoodsId = stock.getGbDgsGbDepDisGoodsId();
                GbDepartmentDisGoodsEntity departmentDisGoodsEntity = gbDepartmentDisGoodsService.getById(gbDgsGbDepDisGoodsId);
                BigDecimal decimal = toBigDecimal(departmentDisGoodsEntity.getGbDdgShowStandardScale(), "1");
                BigDecimal myChangeWeightScale = myChangeWeight.divide(decimal, 1, BigDecimal.ROUND_HALF_UP);
                BigDecimal decimal1 = toBigDecimal(stock.getGbDgsRestWeightShowStandard(), "0").subtract(myChangeWeightScale).setScale(1, BigDecimal.ROUND_HALF_UP);
                stock.setGbDgsRestWeightShowStandard(decimal1.toString());
                stock.setGbDgsRestWeightShowStandardName(departmentDisGoodsEntity.getGbDdgShowStandardName());
            }
        }

        gbDepGoodsStockService.updateById(stock);

        if (stock.getGbDgsWeightGoodsId() != null && !what.equals("produce")) { // 更新出库制作商品业务数据
            updateWeightGoodsData(stock, what, myChangeWeight);
        }

        return reduceEntity;
    }

    /**
     * 更新重量商品数据
     */
    private void updateWeightGoodsData(GbDepartmentGoodsStockEntity stock, String what, BigDecimal myChangeWeight) {
        // TODO: 实现重量商品数据更新
    }

    /**
     * 更新部门商品每日数据
     */
    private void updateDepGoodsDailyEntity(GbDepartmentGoodsStockEntity stock, String what, BigDecimal myChangeWeight,
                                          BigDecimal myChangeSubtotal) {
        Map<String, Object> map = new HashMap<>();
        map.put("depGoodsId", stock.getGbDgsGbDepDisGoodsId());
        map.put("date", formatWhatDay(0));
        System.out.println("updateDepDaily" + what);
        GbDepartmentGoodsDailyEntity depGoodsDailyEntity = gbDepGoodsDailyService.queryDepGoodsDailyItem(map);
        if (depGoodsDailyEntity != null) {
            BigDecimal weight = new BigDecimal(0);
            BigDecimal subtotal = new BigDecimal(0);

            if (what.equals("loss")) {
                weight = myChangeWeight.add(new BigDecimal(depGoodsDailyEntity.getGbDgdLossWeight()));
                subtotal = myChangeSubtotal.add(new BigDecimal(depGoodsDailyEntity.getGbDgdLossSubtotal()));
                depGoodsDailyEntity.setGbDgdLossWeight(weight.toString());
                depGoodsDailyEntity.setGbDgdLossSubtotal(subtotal.toString());
            }
            if (what.equals("produce")) {
                weight = myChangeWeight.add(new BigDecimal(depGoodsDailyEntity.getGbDgdProduceWeight()));
                subtotal = myChangeSubtotal.add(new BigDecimal(depGoodsDailyEntity.getGbDgdProduceSubtotal()));
                depGoodsDailyEntity.setGbDgdProduceWeight(weight.toString());
                depGoodsDailyEntity.setGbDgdProduceSubtotal(subtotal.toString());
                BigDecimal newSalesProfitSubtotal = new BigDecimal(depGoodsDailyEntity.getGbDgdAfterProfitSubtotal()).add(myChangeSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP);
                depGoodsDailyEntity.setGbDgdAfterProfitSubtotal(newSalesProfitSubtotal.toString());
            }
            if (what.equals("waste")) {
                weight = myChangeWeight.add(new BigDecimal(depGoodsDailyEntity.getGbDgdWasteWeight()));
                subtotal = myChangeSubtotal.add(new BigDecimal(depGoodsDailyEntity.getGbDgdWasteSubtotal()));
                depGoodsDailyEntity.setGbDgdWasteWeight(weight.toString());
                depGoodsDailyEntity.setGbDgdWasteSubtotal(subtotal.toString());
                if (!stock.getGbDgsSellingPrice().equals("-1")) {
                    BigDecimal wasteProfitSubtotal = new BigDecimal(depGoodsDailyEntity.getGbDgdAfterProfitSubtotal()).subtract(myChangeSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP);
                    depGoodsDailyEntity.setGbDgdAfterProfitSubtotal(wasteProfitSubtotal.toString());
                }
            }
            if (what.equals("return")) {
                weight = myChangeWeight.add(new BigDecimal(depGoodsDailyEntity.getGbDgdReturnWeight()));
                subtotal = myChangeSubtotal.add(new BigDecimal(depGoodsDailyEntity.getGbDgdReturnSubtotal()));
                depGoodsDailyEntity.setGbDgdReturnWeight(weight.toString());
                depGoodsDailyEntity.setGbDgdReturnSubtotal(subtotal.toString());
            }

            // update restWeight
            BigDecimal newRestWeight = new BigDecimal(depGoodsDailyEntity.getGbDgdRestWeight()).subtract(myChangeWeight).setScale(1, BigDecimal.ROUND_HALF_UP);
            BigDecimal newRestSubtotal = new BigDecimal(depGoodsDailyEntity.getGbDgdRestSubtotal()).subtract(myChangeSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP);
            depGoodsDailyEntity.setGbDgdRestWeight(newRestWeight.toString());
            depGoodsDailyEntity.setGbDgdRestSubtotal(newRestSubtotal.toString());
            depGoodsDailyEntity.setGbDgdFullTime(formatFullTime());
            if (newRestWeight.compareTo(BigDecimal.ZERO) == 0) {
                Calendar calendar = Calendar.getInstance();
                int hours = calendar.get(Calendar.HOUR_OF_DAY);
                int minutes = calendar.get(Calendar.MINUTE);
                depGoodsDailyEntity.setGbDgdSellClearHour(Integer.toString(hours));
                depGoodsDailyEntity.setGbDgdSellClearMinute(Integer.toString(minutes));
            }

            gbDepGoodsDailyService.updateById(depGoodsDailyEntity);
        }
    }

    /**
     * 添加部门商品库存减少记录
     */
    private GbDepartmentGoodsStockReduceEntity addDepGoodsStockReduceEntity(GbDepartmentGoodsStockEntity stock, String what, Integer inventoryType, BigDecimal myChangeWeight,
                                                                             BigDecimal myChangeSubtotal) {

        GbDepartmentGoodsStockReduceEntity reduceEntity = new GbDepartmentGoodsStockReduceEntity();
        reduceEntity.setGbDgsrDistributerId(stock.getGbDgsGbDistributerId());
        reduceEntity.setGbDgsrDepartmentId(stock.getGbDgsGbDepartmentId());
        reduceEntity.setGbDgsrDepartmentFatherId(stock.getGbDgsGbDepartmentFatherId());
        reduceEntity.setGbDgsrDisGoodsId(stock.getGbDgsGbDisGoodsId());
        reduceEntity.setGbDgsrGoodsStockId(stock.getGbDepartmentGoodsStockId());
        reduceEntity.setGbDgsrFullTime(formatFullTime());
        reduceEntity.setGbDgsrDate(formatWhatDay(0));
        reduceEntity.setGbDgsrWeek(getWeekOfYear(0).toString());
        reduceEntity.setGbDgsrMonth(formatWhatMonth(0));
        reduceEntity.setGbDgsrUserId(stock.getGbDgsReduceWeightUserId());

        // 使用简化的 weight 和 subtotal 字段
        reduceEntity.setGbDgsrWeight(myChangeWeight.toString());
        reduceEntity.setGbDgsrSubtotal(myChangeSubtotal.toString());

        if (what.equals("loss")) {
            reduceEntity.setGbDgsrType(getGbDepartGoodsStockReduceTypeLoss());
        } else if (what.equals("produce")) {
            reduceEntity.setGbDgsrType(getGbDepartGoodsStockReduceTypeProduce());
        } else if (what.equals("return")) {
            reduceEntity.setGbDgsrType(getGbDepartGoodsStockReduceTypeReturn());
            reduceEntity.setGbDgsrUserId(stock.getGbDgsReturnUserId());
        } else if (what.equals("waste")) {
            reduceEntity.setGbDgsrType(getGbDepartGoodsStockReduceTypeWaste());
            reduceEntity.setGbDgsrUserId(stock.getGbDgsReturnUserId());
        }

        gbDepartmentStockReduceService.save(reduceEntity);
        return reduceEntity;
    }

    /**
     * 更新部门分销商品总计
     */
    private GbDepartmentDisGoodsEntity subscribeDepDisGoodsTotal(BigDecimal weight, BigDecimal subtotal, Integer depDisGoodsId) {
        GbDepartmentDisGoodsEntity depDisGoodsEntity = gbDepartmentDisGoodsService.getById(depDisGoodsId);
        BigDecimal weightB = new BigDecimal(depDisGoodsEntity.getGbDdgStockTotalWeight()).subtract(weight);
        BigDecimal subtotalB = new BigDecimal(depDisGoodsEntity.getGbDdgStockTotalSubtotal()).subtract(subtotal);
        if (new BigDecimal(depDisGoodsEntity.getGbDdgShowStandardScale()).compareTo(new BigDecimal(0)) == 1) {
            BigDecimal showScale = new BigDecimal(depDisGoodsEntity.getGbDdgShowStandardScale());
            BigDecimal showWeight = weightB.divide(showScale, 1, BigDecimal.ROUND_HALF_UP);
            depDisGoodsEntity.setGbDdgShowStandardWeight(showWeight.toString());
        }
        depDisGoodsEntity.setGbDdgStockTotalSubtotal(subtotalB.setScale(1, BigDecimal.ROUND_HALF_UP).toString());
        depDisGoodsEntity.setGbDdgStockTotalWeight(weightB.setScale(1, BigDecimal.ROUND_HALF_UP).toString());
        if (weightB.compareTo(new BigDecimal(0)) == 0) {
            depDisGoodsEntity.setGbDdgStockTotalSubtotal("0.0");
        }

        gbDepartmentDisGoodsService.updateById(depDisGoodsEntity);
        return depDisGoodsEntity;
    }

    // ==================== 辅助方法 ====================

    private String formatWhatFullTime(int day) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, day);
        return sdf.format(calendar.getTime());
    }

    private String formatFullTime() {
        return formatWhatFullTime(0);
    }

    private String formatWhatDay(int what) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, what);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(cal.getTime());
    }

    private String formatWhatMonth(int day) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, day);
        return sdf.format(calendar.getTime());
    }

    private String formatWhatYear(int day) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, day);
        return sdf.format(calendar.getTime());
    }

    private Integer getWeekOfYear(int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, day);
        return calendar.get(Calendar.WEEK_OF_YEAR);
    }

    private Integer getGbDepartGoodsStockReduceTypeLoss() {
        return 1;
    }

    private Integer getGbDepartGoodsStockReduceTypeProduce() {
        return 2;
    }

    private Integer getGbDepartGoodsStockReduceTypeReturn() {
        return 3;
    }

    private Integer getGbDepartGoodsStockReduceTypeWaste() {
        return 4;
    }
}
