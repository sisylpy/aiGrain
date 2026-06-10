package com.nongxinle.controller;

import com.nongxinle.entity.*;
import com.nongxinle.service.*;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

import static com.nongxinle.utils.DateUtils.*;
import static com.nongxinle.utils.GbTypeUtils.getGbDepartmentTypeMendian;
import static com.nongxinle.utils.PinYin4jUtils.*;

/**
 * 批发商商品Controller
 */
@RestController
@RequestMapping("gbdistributergoods")
public class GbDistributerGoodsController {

    @Autowired
    private GbDistributerGoodsService gbDgService;
    @Autowired
    private GbDepartmentOrdersService depOrdersService;
    @Autowired
    private GbDepartmentDisGoodsService gbDepDisGoodsService;
    @Autowired
    private GbDistributerFatherGoodsService dgfService;
    @Autowired
    private GbDistributerPurchaseGoodsService gbDisPurchaseGoodsService;
    @Autowired
    private GbDistributerStandardService gbDistributerStandardService;
    @Autowired
    private GbDepartmentGoodsStockService gbDepGoodsStockService;
    @Autowired
    private GbJjOrderPurchaseLinkService gbJjOrderPurchaseLinkService;
    @Autowired
    private GbDistributerAliasService gbDistributerAliasService;
    @Autowired
    private NxJrdhSupplierService nxJrdhSupplierService;



    @RequestMapping(value = "/changeGbGoodsFresh", method = RequestMethod.POST)
    @ResponseBody
    public R changeGbGoodsFresh(@RequestBody GbDistributerGoodsEntity gbGoods) {
        Integer gbDistributerGoodsId = gbGoods.getGbDistributerGoodsId();

        gbDgService.update(gbGoods);

        return R.ok();
    }




    @RequestMapping(value = "/addAutoOrderGoods", method = RequestMethod.POST)
    @ResponseBody
    public R addAutoOrderGoods(Integer supplierId, Integer goodsId) {
        GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDgService.queryObject(goodsId);

        gbDistributerGoodsEntity.setGbDgGbSupplierId(supplierId);
        if(supplierId == -1){
            gbDistributerGoodsEntity.setGbDgGoodsType(GbConstants.DistributorGoodsType.SELF_PURCHASE);
        }else{
            gbDistributerGoodsEntity.setGbDgGoodsType(GbConstants.DistributorGoodsType.AUTO_SUPPLIER);
        }
        gbDgService.update(gbDistributerGoodsEntity);
        return R.ok().put("data", gbDistributerGoodsEntity);
    }


    @ResponseBody
    @RequestMapping(value = "/saveLinshiGoodsGb", produces = "text/html;charset=UTF-8")
    public GbDistributerGoodsEntity saveFatherGb(@RequestParam(value = "file", required = false) MultipartFile file,
                                                 @RequestParam("goodsName") String goodsName,
                                                 @RequestParam("standard") String standard,
                                                 @RequestParam("detail") String detail,
                                                 @RequestParam("disId") Integer disId,
                                                 @RequestParam("toDepId") Integer toDepId,
                                                 @RequestParam("depId") Integer depId,
                                                 @RequestParam("depFatherId") Integer depFatherId,
                                                 @RequestParam(value = "standardWeight", required = false) String standardWeight,
                                                 @RequestParam(value = "cartonUnit", required = false) String cartonUnit,
                                                 @RequestParam(value = "itemsPerCarton", required = false) String itemsPerCarton) {
        return gbDgService.saveLinshiGoodsGb(file, goodsName, standard, detail, disId, toDepId, depId, depFatherId, standardWeight, cartonUnit, itemsPerCarton);
    }




    /**
     * 从农鑫导入批发商商品并保存部门订货单（含部门商品、采购行）。
     * <p>HTTP 路径仍为 {@code /saveOrdersGbJjAndSaveGoods}，与前端/老接口一致。
     */
    @ResponseBody
    @RequestMapping("/createDepartmentOrderFromNxGoodsImport")
    public R createDepartmentOrderFromNxGoodsImport(@RequestBody GbDepartmentOrdersEntity gbDepartmentOrders) {

        Integer gbDoDepartmentId = gbDepartmentOrders.getGbDoToDepartmentId();
        Integer gbDoDistributerId = gbDepartmentOrders.getGbDoDistributerId();
        Integer nxGoodsId = gbDepartmentOrders.getGbDoNxGoodsId();
        GbDistributerGoodsEntity gbNewGoods = gbDgService.createDistributerGoodsFromNxGoods(gbDoDistributerId, gbDoDepartmentId, nxGoodsId);
        gbDepartmentOrders.setGbDoDisGoodsId(gbNewGoods.getGbDistributerGoodsId());
        GbDepartmentDisGoodsEntity mendianDisGoodsEntity =
                gbDepDisGoodsService.createDepDisGoodsForJjOrder(gbDepartmentOrders, gbNewGoods);
        gbDepartmentOrders.setGbDoDepDisGoodsId(mendianDisGoodsEntity.getGbDepartmentDisGoodsId());
        gbJjOrderPurchaseLinkService.applyJjOrderTimestamps(gbDepartmentOrders);
        gbJjOrderPurchaseLinkService.applyDisGoodsCategoryHierarchyToOrder(
                gbDepartmentOrders, gbNewGoods.getGbDgDfgGoodsFatherId());
        gbDepartmentOrders.setGbDoGoodsType(gbNewGoods.getGbDgGoodsType());
        gbDepartmentOrders.setGbDoOrderType(gbNewGoods.getGbDgGoodsType());
        gbDepartmentOrders.setGbDoBuyStatus(GbConstants.OrderBuyStatus.NEW);
        gbDepartmentOrders.setGbDoStatus(GbConstants.DepartmentOrderStatus.NEW);
        depOrdersService.save(gbDepartmentOrders);
        gbJjOrderPurchaseLinkService.resolvePurchaseGoodsLineForJjOrder(
                gbDepartmentOrders,
                gbNewGoods,
                GbJjOrderPurchaseLinkService.PurchaseGoodsLinkMode.ALWAYS_NEW);
        Integer gbDistributerGoodsId = gbNewGoods.getGbDistributerGoodsId();
        List<GbDistributerStandardEntity> standardEntityList = gbDistributerStandardService.queryDisStandardByDisGoodsIdGb(gbDistributerGoodsId);
        gbNewGoods.setGbDistributerStandardEntities(standardEntityList);
        gbDepartmentOrders.setGbDistributerGoodsEntity(gbNewGoods);
        return R.ok().put("data", gbDepartmentOrders);
    }

    /**
     * 批发商商品快速搜索
     * 支持中文字名称、拼音、拼音首字母搜索
     * 路径: POST /api/gbdistributergoods/queryDisGoodsByQuickSearchGb
     */
    @RequestMapping(value = "/queryDisGoodsByQuickSearchGb", method = RequestMethod.POST)
    @ResponseBody
    public R queryDisGoodsByQuickSearchGb(String searchStr, String disId) {
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);

        // 判断搜索内容是否包含中文
        for (int i = 0; i < searchStr.length(); i++) {
            String str = searchStr.substring(i, i + 1);
            if (str.matches("[\\u4E00-\\u9FFF]")) {
                // 是中文，转换拼音
                String pinyin = hanziToPinyin(searchStr);
                map.put("searchStr", searchStr);
                map.put("searchPinyin", pinyin);
            } else {
                // 非中文，直接作为拼音搜索
                map.put("searchPinyin", searchStr);
            }
        }

        List<GbDistributerGoodsEntity> goodsEntities = gbDgService.queryGbDisGoodsQuickSearchStr(map);

        return R.ok().put("data", goodsEntities);
    }




	/**
	 * 批发商商品详细
	 * @param disGoodsId 批发商商品id
	 * @return 含有客户的商品
	 */
    @RequestMapping(value = "/gbDisGetGoodsDetail/{disGoodsId}")
    @ResponseBody
    public R gbDisGetGoodsDetail(@PathVariable Integer disGoodsId) {

        //商品信息
        GbDistributerGoodsEntity disGoods = gbDgService.getById(disGoodsId);

        List<GbDistributerStandardEntity> gbDistributerStandardEntities = gbDistributerStandardService.queryDisStandardByDisGoodsIdGb(disGoodsId);
        disGoods.setGbDistributerStandardEntities(gbDistributerStandardEntities);

        List<GbDistributerAliasEntity> list = gbDistributerAliasService.queryDisAliasByDisGoodsId(disGoodsId);
        disGoods.setGbDistributerAliasEntities(list);

        if(disGoods.getGbDgGbSupplierId() != -1){
            Integer gbDgGbSupplierId = disGoods.getGbDgGbSupplierId();
            NxJrdhSupplierEntity byId = nxJrdhSupplierService.getById(gbDgGbSupplierId);
            disGoods.setGbDistributerAppointSupplierEntity(byId);
        }


        //3ri订单
        List<Map<String, Object>> orderList = new ArrayList<>();
        Map<String, Object> map1 = new HashMap<>();
        map1.put("disId", disGoods.getGbDgDistributerId());
        map1.put("disGoodsId", disGoodsId);
        map1.put("orderType", disGoods.getGbDgGoodsType());
        map1.put("applyDate", formatWhatDay(0));
        System.out.println("abdddnnddmd111" + map1);
        List<GbDepartmentOrdersEntity> departmentOrdersEntities = depOrdersService.queryDisOrdersListByParams(map1);
        Map<String, Object> mapone = new HashMap<>();
        mapone.put("date", formatWhatDayString(0));
        mapone.put("order", departmentOrdersEntities);
        orderList.add(mapone);

        map1.put("applyDate", formatWhatDay(-1));
        System.out.println("abdddnnddmd222" + map1);
        List<GbDepartmentOrdersEntity> departmentOrdersEntities2 = depOrdersService.queryDisOrdersByParams(map1);
        Map<String, Object> maptwo = new HashMap<>();
        maptwo.put("date", formatWhatDayString(-1));
        maptwo.put("order", departmentOrdersEntities2);
        orderList.add(maptwo);

        map1.put("applyDate", formatWhatDay(-2));
        List<GbDepartmentOrdersEntity> departmentOrdersEntities3 = depOrdersService.queryDisOrdersByParams(map1);
        Map<String, Object> mapthree = new HashMap<>();
        mapthree.put("date", formatWhatDayString(-2));
        mapthree.put("order", departmentOrdersEntities3);
        orderList.add(mapthree);


        //进货
        Map<String, Object> map2 = new HashMap<>();
        map2.put("disGoodsId", disGoodsId);
        map2.put("startDate", formatWhatDay(-2));
//        map2.put("equalStatus", 3);
        System.out.println("purgooddd" + map2);
        List<GbDistributerPurchaseGoodsEntity> goodsEntities = gbDisPurchaseGoodsService.queryOnlyPurGoods(map2);

        //客户
        Map<String, Object> map41 = new HashMap<>();
        map41.put("disGoodsId", disGoodsId);
        map41.put("depType", getGbDepartmentTypeMendian());
        System.out.println("41141" + map41);
//        TreeSet<GbDepartmentEntity> departmentEntities = gbDepGoodsStockService.queryDepGoodsTreeDepartments(map41);
//        TreeSet<GbDepartmentEntity> departmentEntities = gbDepGoodsStockService.queryGoodsStockByParams(map41);
//        if (departmentEntities.size() > 0) {
//            for (GbDepartmentEntity department : departmentEntities) {
//                double depDoutbleRest = 0;
//                double depDoutbleRestV = 0;
//                Map<String, Object> mapDepStock = new HashMap<>();
//                mapDepStock.put("disGoodsId", disGoodsId);
//                mapDepStock.put("depId", department.getGbDepartmentId());
//                Integer integer = gbDepGoodsStockService.queryGoodsStockCount(mapDepStock);
//                if (integer > 0) {
//                    depDoutbleRest = gbDepGoodsStockService.queryDepStockRestWeightTotal(mapDepStock);
//                    depDoutbleRestV = gbDepGoodsStockService.queryDepGoodsRestTotal(mapDepStock);
//                }
//                department.setDepRestGoodsTotalString(new BigDecimal(depDoutbleRestV).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
//                department.setDepRestGoodsWeightTotalString(new BigDecimal(depDoutbleRest).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
//            }
//        }

        Map<String, Object> mapDep = new HashMap<>();
        mapDep.put("disGoodsId", disGoodsId);
        mapDep.put("depId", disGoods.getGbDgGbDepartmentId());
//        GbDepartmentDisGoodsEntity departmentDisGoodsEntity = gbDepDisGoodsService.queryDepGoodsItemByParams(mapDep);

        Map<String, Object> map = new HashMap<>();
        map.put("orderArr", orderList);
        map.put("purchaseArr", goodsEntities);
        map.put("goodsInfo", disGoods);
//        map.put("depGoodArr", departmentEntities);
//        System.out.println("depgppd" + departmentDisGoodsEntity);
//        map.put("depGoods", departmentDisGoodsEntity);
        return R.ok().put("data", map);
    }





    @RequestMapping(value = "/canclePostDgnGoodsGb", method = RequestMethod.POST)
    @ResponseBody
    public R canclePostDgnGoodsGb(Integer disGoodsId, Integer disGoodsFatherId, Integer disId) {
        Map<String, Object> map5 = new HashMap<>();
        map5.put("disGoodsId", disGoodsId);
        Integer orderAmount = depOrdersService.queryGbDepartmentOrderAmount(map5);
        Integer stockCount = gbDepGoodsStockService.queryGoodsStockCount(map5);
        if (orderAmount > 0 || stockCount > 0) {
            return R.error(-1, "此商品在使用中");
        } else {

            Map<String, Object> map1 = new HashMap<>();
            map1.put("disId", disId);
            map1.put("dgFatherId", disGoodsFatherId);
            List<GbDistributerGoodsEntity> totalDisGoods = gbDgService.queryDisGoodsByParams(map1);
            //如果disGoods的父类只有一个商品
            if (totalDisGoods.size() == 1 && totalDisGoods.get(0).getGbDgNxGoodsId() != null) {
                //父类Entity
                GbDistributerFatherGoodsEntity gbDistributerFatherGoodsEntity0 = dgfService.queryObject(disGoodsFatherId);
                //disGoods的grandId
                Integer grandId = gbDistributerFatherGoodsEntity0.getGbDfgFathersFatherId();
                Map<String, Object> mapGrand = new HashMap<>();
                mapGrand.put("fathersFatherId", grandId);
                //搜索grand有几个兄弟
                List<GbDistributerFatherGoodsEntity> fatherGoodsEntities = dgfService.queryDisFathersGoodsByParamsGb(mapGrand);
                if (fatherGoodsEntities.size() == 1) {
                    Integer gbDfgFathersFatherId = fatherGoodsEntities.get(0).getGbDfgFathersFatherId();
                    GbDistributerFatherGoodsEntity grandEntity = dgfService.queryObject(gbDfgFathersFatherId);
                    Integer greatGrandId = grandEntity.getGbDfgFathersFatherId();
                    Map<String, Object> map = new HashMap<>();
                    map.put("fathersFatherId", greatGrandId);
                    List<GbDistributerFatherGoodsEntity> grandGoodsEntities = dgfService.queryDisFathersGoodsByParamsGb(map);

                    //如果grandFather也是只有一个，则删除greatGrandFather
                    if (grandGoodsEntities.size() == 1) {
                        dgfService.delete(greatGrandId);
                    }
                    dgfService.delete(grandId);
                }
                dgfService.delete(disGoodsFatherId);
            } else {
                GbDistributerFatherGoodsEntity father = dgfService.queryObject(disGoodsFatherId);
                father.setGbDfgGoodsAmount(father.getGbDfgGoodsAmount() - 1);
                dgfService.update(father);
            }

            //删除订货单位
            List<GbDistributerStandardEntity> standardEntities = gbDistributerStandardService.queryDisStandardByDisGoodsIdGb(disGoodsId);
            if (standardEntities.size() > 0) {
                for (GbDistributerStandardEntity disStandard : standardEntities) {
                    gbDistributerStandardService.removeById(disStandard.getGbDistributerStandardId());
                }
            }


            Map<String, Object> map = new HashMap<>();
            map.put("disGoodsId", disGoodsId);
            List<GbDepartmentDisGoodsEntity> departmentDisGoodsEntities1 = gbDepDisGoodsService.queryGbDepDisGoodsByParams(map);
            if (departmentDisGoodsEntities1.size() > 0) {
                for (GbDepartmentDisGoodsEntity disGoodsEntity : departmentDisGoodsEntities1) {
                    gbDepDisGoodsService.removeById(disGoodsEntity.getGbDepartmentDisGoodsId());
                }
            }

            List<GbDistributerAliasEntity> aliasEntities = gbDistributerAliasService.queryDisAliasByDisGoodsId(disGoodsId);
            if (!aliasEntities.isEmpty()) {
                for (GbDistributerAliasEntity aliasEntity : aliasEntities) {
                    gbDistributerAliasService.delete(aliasEntity.getGbDistributerAliasId());
                }
            }

            gbDgService.delete(disGoodsId);

            return R.ok();
        }
    }



    @RequestMapping(value = "/disUpdateDisGoodsGb", method = RequestMethod.POST)
    @ResponseBody
    public R disUpdateDisGoodsGb(@RequestBody GbDistributerGoodsEntity gbGoods) {

        //old
        Integer gbDistributerGoodsId = gbGoods.getGbDistributerGoodsId();
        GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDgService.queryObject(gbDistributerGoodsId);
        Integer oldDepartmentId = gbDistributerGoodsEntity.getGbDgGbDepartmentId();

        Integer nowDepartmentId = gbGoods.getGbDgGbDepartmentId();
        GbDistributerGoodsEntity oldGoodsEntity = gbDgService.queryObject(gbDistributerGoodsId);
        Integer oldGoodsType = oldGoodsEntity.getGbDgGoodsType();
        Integer gbDgGoodsType = gbGoods.getGbDgGoodsType();

        // 修改商品采购方式（部门/类型可能为 null，避免 old*.equals 触发 NPE）
        if (!Objects.equals(oldDepartmentId, nowDepartmentId) || !Objects.equals(oldGoodsType, gbDgGoodsType)) {
            //查询是否有未完成订单
            Map<String, Object> map = new HashMap<>();
            map.put("disGoodsId", gbDistributerGoodsId);
            map.put("status", 3);
            System.out.println("mapmap有未完成订单" +map);
            List<GbDepartmentOrdersEntity> ordersEntities = depOrdersService.queryDisOrdersByParams(map);
            if (ordersEntities.size() > 0) {
                return R.error(-1, "有未完成订单");
            }

            //查询是否有库存
            Map<String, Object> map1 = new HashMap<>();
            map1.put("stockDepId", oldDepartmentId);
            map1.put("disGoodsId", gbDistributerGoodsId);
            map1.put("restWeight", 0);
            List<GbDepartmentGoodsStockEntity> stockEntities = gbDepGoodsStockService.queryGoodsStockByParams(map1);
            if (stockEntities.size() > 0) {
                return R.error(-1, "有库存,不能改为非库存商品.");
            }

            //删除原来部门的部门商品
            Map<String, Object> mapOld = new HashMap<>();
            mapOld.put("depFatherId", oldGoodsEntity.getGbDgGbDepartmentId());
            mapOld.put("disGoodsId", gbGoods.getGbDistributerGoodsId());
//            GbDepartmentDisGoodsEntity departmentDisGoodsEntity = gbDepDisGoodsService.queryDepGoodsItemByParams(mapOld);
//            if (departmentDisGoodsEntity != null) {
//                Map<String, Object> mapD = new HashMap<>();
//                mapD.put("depGoodsId", departmentDisGoodsEntity.getGbDepartmentDisGoodsId());
//                mapD.put("date", formatWhatDay(0));
//                System.out.println("dkakdkfkadjfdasf" + mapD);
//                GbDepartmentGoodsDailyEntity dailyEntity = gbDepGoodsDailyService.queryDepGoodsDailyItem(mapD);
//                if (dailyEntity != null) {
//                    dailyEntity.setGbDgdStatus(-1);
//                    gbDepGoodsDailyService.update(dailyEntity);
//                }
//
//                gbDepDisGoodsService.delete(departmentDisGoodsEntity.getGbDepartmentDisGoodsId());
//
//            }

//            Map<String, Object> map2 = new HashMap<>();
//            map2.put("stockDepId", oldGoodsEntity.getGbDgGbDepartmentId());
//            map2.put("disGoodsId", gbDistributerGoodsId);
//            List<GbDistributerGoodsShelfGoodsEntity> shelfGoodsEntities = gbDistributerGoodsShelfGoodsService.queryShelfGoodsByParams(map2);
//            System.out.println("deletDepdistoosssSehelff" + shelfGoodsEntities.size());
//            if (shelfGoodsEntities.size() > 0) {
//                for (GbDistributerGoodsShelfGoodsEntity shelfGoods : shelfGoodsEntities) {
//                    gbDistributerGoodsShelfGoodsService.delete(shelfGoods.getGbDistributerGoodsShelfGoodsId());
//                }
//            }

        }


//        if (!gbGoods.getGbDgGoodsType().equals(getGbDisGoodsTypeZicai())) {
//            //对比 old-ToDepId 和新 todepId 是否一样
//            if (!gbDistributerGoodsEntity.getGbDgGbDepartmentId().equals(gbGoods.getGbDgGbDepartmentId())) {
//                Map<String, Object> map1 = new HashMap<>();
//                map1.put("depFatherId", gbGoods.getGbDgGbDepartmentId());
//                map1.put("disGoodsId", gbGoods.getGbDistributerGoodsId());
//                List<GbDepartmentDisGoodsEntity> newDepartmentDisGoodsEntities = gbDepDisGoodsService.queryGbDepDisGoodsByParams(map1);
//                System.out.println("dkajsfkaslfjas;lfjsa" + newDepartmentDisGoodsEntities.size());
//                if (newDepartmentDisGoodsEntities.size() == 0) {
//                    //添加部门商品
//                    GbDepartmentDisGoodsEntity disGoodsEntity = new GbDepartmentDisGoodsEntity();
//                    disGoodsEntity.setGbDdgDepGoodsName(gbGoods.getGbDgGoodsName());
//                    disGoodsEntity.setGbDdgDisGoodsId(gbGoods.getGbDistributerGoodsId());
//                    disGoodsEntity.setGbDdgDisGoodsFatherId(gbGoods.getGbDgDfgGoodsFatherId());
//                    disGoodsEntity.setGbDdgDisGoodsGrandId(gbGoods.getGbDgDfgGoodsGrandId());
//                    disGoodsEntity.setGbDdgDisGoodsGreatId(gbGoods.getGbDgDfgGoodsGreatId());
//                    disGoodsEntity.setGbDdgDepGoodsPinyin(gbGoods.getGbDgGoodsPinyin());
//                    disGoodsEntity.setGbDdgDepGoodsPy(gbGoods.getGbDgGoodsPy());
//                    disGoodsEntity.setGbDdgDepGoodsStandardname(gbGoods.getGbDgGoodsStandardname());
//                    disGoodsEntity.setGbDdgDepartmentId(gbGoods.getGbDgGbDepartmentId());
//                    disGoodsEntity.setGbDdgDepartmentFatherId(gbGoods.getGbDgGbDepartmentId());
//                    disGoodsEntity.setGbDdgGbDepartmentId(gbGoods.getGbDgGbDepartmentId());
//                    disGoodsEntity.setGbDdgGbDisId(gbGoods.getGbDgDistributerId());
//                    disGoodsEntity.setGbDdgGoodsType(gbGoods.getGbDgGoodsType());
//                    disGoodsEntity.setGbDdgStockTotalWeight("0.0");
//                    disGoodsEntity.setGbDdgStockTotalSubtotal("0.0");
//                    disGoodsEntity.setGbDdgShowStandardId(-1);
//                    disGoodsEntity.setGbDdgShowStandardName(gbGoods.getGbDgGoodsStandardname());
//                    disGoodsEntity.setGbDdgShowStandardScale("-1");
//                    disGoodsEntity.setGbDdgShowStandardWeight(null);
//                    disGoodsEntity.setGbDdgPrintStandard(gbGoods.getGbDgGoodsStandardname());
//                    gbDepDisGoodsService.save(disGoodsEntity);
//
//                }
//            }
//        }


        String goodsName = gbGoods.getGbDgGoodsName();
        String pinyin = hanziToPinyin(goodsName);
        String headPinyin = getHeadStringByString(goodsName, false, null);
        gbGoods.setGbDgGoodsPinyin(pinyin);
        gbGoods.setGbDgGoodsPy(headPinyin);

        System.out.println("pdadafasfa" + gbGoods.getGbDgNxDistributerGoodsId());

        gbDgService.update(gbGoods);


        Map<String, Object> map = new HashMap<>();
        map.put("disGoodsId", gbDistributerGoodsId);
        List<GbDepartmentDisGoodsEntity> departmentDisGoodsEntities = gbDepDisGoodsService.queryGbDepDisGoodsByParams(map);
        System.out.println("changedepdisgooodss" + departmentDisGoodsEntities.size());
        if (departmentDisGoodsEntities.size() > 0) {
            for (GbDepartmentDisGoodsEntity disGoodsEntity : departmentDisGoodsEntities) {
                disGoodsEntity.setGbDdgDepGoodsName(gbGoods.getGbDgGoodsName());
                disGoodsEntity.setGbDdgDepGoodsPinyin(gbGoods.getGbDgGoodsPinyin());
                disGoodsEntity.setGbDdgDepGoodsPy(gbGoods.getGbDgGoodsPy());
                disGoodsEntity.setGbDdgDepGoodsStandardname(gbGoods.getGbDgGoodsStandardname());
                disGoodsEntity.setGbDdgGbDepartmentId(gbGoods.getGbDgGbDepartmentId());
                disGoodsEntity.setGbDdgGbDisId(gbGoods.getGbDgDistributerId());
                disGoodsEntity.setGbDdgGoodsType(gbGoods.getGbDgGoodsType());
                disGoodsEntity.setGbDdgShowStandardName(gbGoods.getGbDgGoodsStandardname());
                gbDepDisGoodsService.updateById(disGoodsEntity);
            }
        }

        return R.ok();
    }






}
