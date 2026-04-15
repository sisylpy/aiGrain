package com.nongxinle.controller;

import com.nongxinle.entity.*;
import com.nongxinle.service.*;
import com.nongxinle.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

import static com.nongxinle.utils.DateUtils.*;
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
    private NxGoodsService nxGoodsService;
    @Autowired
    private NxAliasService nxAliasService;
    @Autowired
    private NxStandardService nxStandardService;
    @Autowired
    private GbDepartmentDisGoodsService gbDepDisGoodsService;
    @Autowired
    private GbDistributerFatherGoodsService dgfService;
    @Autowired
    private GbDistributerAliasService gbDistributerAliasService;
    @Autowired
    private GbDistributerPurchaseGoodsService gbDisPurchaseGoodsService;
    @Autowired
    private GbDistributerStandardService gbDistributerStandardService;


    /**
     * 保存订单并创建商品
     * 从农鑫商品库添加到批发商商品库，同时创建采购单
     */
    @ResponseBody
    @RequestMapping("/saveOrdersGbJjAndSaveGoods")
    public R saveOrdersGbJjAndSaveGoods(@RequestBody GbDepartmentOrdersEntity gbDepartmentOrders) {

        Integer gbDoDepartmentId1 = gbDepartmentOrders.getGbDoToDepartmentId();
        Integer gbDoDistributerId1 = gbDepartmentOrders.getGbDoDistributerId();
        Integer nxGoodsId1 = gbDepartmentOrders.getGbDoNxGoodsId();
        GbDistributerGoodsEntity gbNewGoods = postDgnGbGoods(gbDoDistributerId1, gbDoDepartmentId1, nxGoodsId1);
        GbDistributerFatherGoodsEntity grandGoods = dgfService.queryObject(gbNewGoods.getGbDgDfgGoodsGrandId());
        Integer greatFatherGoodsId = grandGoods.getGbDfgFathersFatherId();


        //添加部门商品
        GbDepartmentDisGoodsEntity mendianDisGoodsEntity = new GbDepartmentDisGoodsEntity();

        String gbDoGoodsName = gbDepartmentOrders.getGbDoGoodsName();
        mendianDisGoodsEntity.setGbDdgDepGoodsName(gbDoGoodsName);
        mendianDisGoodsEntity.setGbDdgDisGoodsId(gbNewGoods.getGbDistributerGoodsId());
        mendianDisGoodsEntity.setGbDdgDisGoodsFatherId(gbNewGoods.getGbDgDfgGoodsFatherId());
        mendianDisGoodsEntity.setGbDdgDisGoodsGrandId(gbNewGoods.getGbDgDfgGoodsGrandId());
        mendianDisGoodsEntity.setGbDdgDisGoodsGreatId(greatFatherGoodsId);

        String pinyin = hanziToPinyin(gbDoGoodsName);
        String headPinyin = getHeadStringByString(gbDoGoodsName, false, null);
        mendianDisGoodsEntity.setGbDdgDepGoodsPinyin(pinyin);
        mendianDisGoodsEntity.setGbDdgDepGoodsPy(headPinyin);
        mendianDisGoodsEntity.setGbDdgDepGoodsStandardname(gbNewGoods.getGbDgGoodsStandardname());
        mendianDisGoodsEntity.setGbDdgDepartmentId(gbDepartmentOrders.getGbDoDepartmentId());
        mendianDisGoodsEntity.setGbDdgDepartmentFatherId(gbDepartmentOrders.getGbDoDepartmentFatherId());

        mendianDisGoodsEntity.setGbDdgGbDepartmentId(gbNewGoods.getGbDgGbDepartmentId());
        mendianDisGoodsEntity.setGbDdgGbDisId(gbNewGoods.getGbDgDistributerId());
        mendianDisGoodsEntity.setGbDdgGoodsType(gbNewGoods.getGbDgGoodsType());
        mendianDisGoodsEntity.setGbDdgStockTotalWeight("0.0");
        mendianDisGoodsEntity.setGbDdgStockTotalSubtotal("0.0");
        mendianDisGoodsEntity.setGbDdgShowStandardId(-1);
        mendianDisGoodsEntity.setGbDdgShowStandardName(gbNewGoods.getGbDgGoodsStandardname());
        mendianDisGoodsEntity.setGbDdgOrderStandard(gbDepartmentOrders.getGbDoStandard());
        mendianDisGoodsEntity.setGbDdgShowStandardScale("-1");
        mendianDisGoodsEntity.setGbDdgShowStandardWeight(null);
        mendianDisGoodsEntity.setGbDdgNxDistributerGoodsId(gbNewGoods.getGbDgNxDistributerGoodsId());
        mendianDisGoodsEntity.setGbDdgNxDistributerId(-1);
        mendianDisGoodsEntity.setGbDdgPrintStandard(gbNewGoods.getGbDgGoodsStandardname());
        gbDepDisGoodsService.save(mendianDisGoodsEntity);
        System.out.println("=== DEBUG === mendianDisGoodsEntity ID: " + mendianDisGoodsEntity.getGbDepartmentDisGoodsId());


        // add purchaseGoods
        gbDepartmentOrders.setGbDoPurchaseGoodsId(-1);
        gbDepartmentOrders.setGbDoApplyDate(formatWhatDay(0));
        gbDepartmentOrders.setGbDoApplyFullTime(formatWhatYearDayTime(0));
        gbDepartmentOrders.setGbDoApplyOnlyTime(formatWhatTime(0));
        gbDepartmentOrders.setGbDoArriveOnlyDate(formatWhatDate(0));
        gbDepartmentOrders.setGbDoArriveWeeksYear(getWeekOfYear(0));
        gbDepartmentOrders.setGbDoGoodsType(1);
        gbDepartmentOrders.setGbDoOrderType(1);
        gbDepartmentOrders.setGbDoBuyStatus(0);
        gbDepartmentOrders.setGbDoStatus(0);
        gbDepartmentOrders.setGbDoDisGoodsId(gbNewGoods.getGbDistributerGoodsId());
        Integer gbDgDfgGoodsFatherId = gbNewGoods.getGbDgDfgGoodsFatherId();
        GbDistributerFatherGoodsEntity fatherGoodsEntity = dgfService.queryObject(gbDgDfgGoodsFatherId);
        Integer gbDfgFathersFatherId = fatherGoodsEntity.getGbDfgFathersFatherId();
        GbDistributerFatherGoodsEntity grandFather = dgfService.queryObject(gbDfgFathersFatherId);
        Integer greatFatherId = grandFather.getGbDfgFathersFatherId();
        GbDistributerFatherGoodsEntity greatFather = dgfService.queryObject(greatFatherId);

        gbDepartmentOrders.setGbDoDisGoodsFatherId(fatherGoodsEntity.getGbDistributerFatherGoodsId());
        gbDepartmentOrders.setGbDoDisGoodsGrandId(fatherGoodsEntity.getGbDfgFathersFatherId());
        gbDepartmentOrders.setGbDoDisGoodsGreatId(grandFather.getGbDfgFathersFatherId());
        gbDepartmentOrders.setGbDoNxDistributerGoodsId(-1);
        gbDepartmentOrders.setGbDoNxDistributerId(-1);
        gbDepartmentOrders.setGbDoDepDisGoodsId(mendianDisGoodsEntity.getGbDepartmentDisGoodsId());

        gbDepartmentOrders.setGbDoNxGoodsGrandId(grandFather.getGbDfgNxGoodsId());
        gbDepartmentOrders.setGbDoNxGoodsGreatId(greatFather.getGbDfgNxGoodsId());

        depOrdersService.save(gbDepartmentOrders);

        //是个新采购商品
        GbDistributerPurchaseGoodsEntity gbPurchaseGoodsEntity = new GbDistributerPurchaseGoodsEntity();
        gbPurchaseGoodsEntity.setGbDpgDisGoodsFatherId(gbDepartmentOrders.getGbDoDisGoodsFatherId());
        gbPurchaseGoodsEntity.setGbDpgDisGoodsGrandId(gbDepartmentOrders.getGbDoDisGoodsGrandId());
        gbPurchaseGoodsEntity.setGbDpgDisGoodsGreatId(gbDepartmentOrders.getGbDoDisGoodsGreatId());
        gbPurchaseGoodsEntity.setGbDpgDisGoodsId(gbDepartmentOrders.getGbDoDisGoodsId());
        gbPurchaseGoodsEntity.setGbDpgDistributerId(gbDepartmentOrders.getGbDoDistributerId());
        gbPurchaseGoodsEntity.setGbDpgApplyDate(formatWhatDay(0));
        gbPurchaseGoodsEntity.setGbDpgStatus(0);
        gbPurchaseGoodsEntity.setGbDpgOrdersAmount(1);
        gbPurchaseGoodsEntity.setGbDpgOrdersFinishAmount(0);
        gbPurchaseGoodsEntity.setGbDpgOrdersWeightAmount(0);
        gbPurchaseGoodsEntity.setGbDpgOrdersBillAmount(0);
        gbPurchaseGoodsEntity.setGbDpgStandard(gbDepartmentOrders.getGbDoStandard());
        gbPurchaseGoodsEntity.setGbDpgQuantity(gbDepartmentOrders.getGbDoQuantity());
        gbPurchaseGoodsEntity.setGbDpgBuyScale(gbDepartmentOrders.getGbDoDsStandardScale());
        gbPurchaseGoodsEntity.setGbDpgPurchaseDepartmentId(gbDepartmentOrders.getGbDoToDepartmentId());
        gbPurchaseGoodsEntity.setGbDpgPurchaseType(0);
        gbPurchaseGoodsEntity.setGbDpgPurchaseNxSupplierId(-1);
        //standard Same
        if(gbNewGoods.getGbDgGoodsStandardname().equals(gbDepartmentOrders.getGbDoStandard())){
            gbDepartmentOrders.setGbDoWeight(gbDepartmentOrders.getGbDoQuantity());
            gbPurchaseGoodsEntity.setGbDpgBuyQuantity(gbDepartmentOrders.getGbDoQuantity());
        }
        gbDisPurchaseGoodsService.save(gbPurchaseGoodsEntity);
        System.out.println("=== DEBUG === gbPurchaseGoodsEntity ID: " + gbPurchaseGoodsEntity.getGbDistributerPurchaseGoodsId());
        Integer gbDistributerPurchaseGoodsId = gbPurchaseGoodsEntity.getGbDistributerPurchaseGoodsId();
        gbDepartmentOrders.setGbDoPurchaseGoodsId(gbDistributerPurchaseGoodsId);
        depOrdersService.update(gbDepartmentOrders);
        Integer gbDistributerGoodsId = gbNewGoods.getGbDistributerGoodsId();
        List<GbDistributerStandardEntity> standardEntityList = gbDistributerStandardService.queryDisStandardByDisGoodsIdGb(gbDistributerGoodsId);
        gbNewGoods.setGbDistributerStandardEntities(standardEntityList);
        gbDepartmentOrders.setGbDistributerGoodsEntity(gbNewGoods);

        return R.ok().put("data", gbDepartmentOrders);
    }


    /**
     * 将农鑫商品添加到批发商商品库（完全按照老项目）
     */
    public GbDistributerGoodsEntity postDgnGbGoods(Integer gbDisId, Integer depId, Integer nxGoodsId) {

        NxGoodsEntity nxGoodsEntity = nxGoodsService.queryObject(nxGoodsId);
        GbDistributerGoodsEntity cgnGoods = new GbDistributerGoodsEntity();
        cgnGoods.setGbDgGoodsName(nxGoodsEntity.getNxGoodsName());
        cgnGoods.setGbDgGoodsStandardname(nxGoodsEntity.getNxGoodsStandardname());
        cgnGoods.setGbDgGoodsPy(nxGoodsEntity.getNxGoodsPy());
        cgnGoods.setGbDgGoodsPinyin(nxGoodsEntity.getNxGoodsPinyin());
        cgnGoods.setGbDgGoodsStandardWeight(nxGoodsEntity.getNxGoodsStandardWeight());
        cgnGoods.setGbDgGoodsDetail(nxGoodsEntity.getNxGoodsDetail());
        cgnGoods.setGbDgGoodsBrand(nxGoodsEntity.getNxGoodsBrand());
        cgnGoods.setGbDgGoodsPlace(nxGoodsEntity.getNxGoodsPlace());
        cgnGoods.setGbDgGoodsSort(nxGoodsEntity.getNxGoodsSort());
        cgnGoods.setGbDgGoodsSonsSort(nxGoodsEntity.getNxGoodsSonsSort());
        cgnGoods.setGbDgNxFatherImgLarge(nxGoodsEntity.getNxGoodsFileBig());
        cgnGoods.setGbDgNxFatherImg(nxGoodsEntity.getNxGoodsFile());
        cgnGoods.setGbDgNxGoodsFatherColor(nxGoodsEntity.getColor());
        cgnGoods.setGbDgDistributerId(gbDisId);
        cgnGoods.setGbDgGoodsStatus(0);
        cgnGoods.setGbDgGoodsIsWeight(0);
        cgnGoods.setGbDgGoodsIsHidden(0);
        cgnGoods.setGbDgNxGoodsId(nxGoodsEntity.getNxGoodsId());
        cgnGoods.setGbDgNxFatherId(nxGoodsEntity.getNxGoodsFatherId());
        cgnGoods.setGbDgNxFatherName(nxGoodsEntity.getFatherGoods().getNxGoodsName());
        cgnGoods.setGbDgNxFatherImg(nxGoodsEntity.getFatherGoods().getNxGoodsFile());
        cgnGoods.setGbDgNxGrandName(nxGoodsEntity.getGrandGoods().getNxGoodsName());
        cgnGoods.setGbDgNxGrandId(nxGoodsEntity.getGrandGoods().getNxGoodsId());
        cgnGoods.setGbDgNxGreatGrandName(nxGoodsEntity.getGreatGrandGoods().getNxGoodsName());
        cgnGoods.setGbDgNxGreatGrandId(nxGoodsEntity.getGreatGrandGoods().getNxGoodsId());
        cgnGoods.setGbDgPullOff(0);
        cgnGoods.setGbDgGoodsType(2);
        cgnGoods.setGbDgGbSupplierId(-1);
        cgnGoods.setGbDgNxDistributerId(-1);
        cgnGoods.setGbDgNxDistributerGoodsId(-1);
        cgnGoods.setGbDgNxDistributerGoodsPrice("0.1");
        cgnGoods.setGbDgGbDepartmentId(depId);
        cgnGoods.setGbDgControlFresh(0);
        cgnGoods.setGbDgControlPrice(0);
        cgnGoods.setGbDgGoodsInventoryType(1);
        cgnGoods.setGbDgIsFranchisePrice(0);
        cgnGoods.setGbDgIsSelfControl(0);

        GbDistributerGoodsEntity disGoods = saveDisGoods(cgnGoods);

        //保存别名
        Map<String, Object> map = new HashMap<>();
        map.put("goodsId", nxGoodsId);
        List<NxAliasEntity> aliasEntities = nxAliasService.queryNxAliasList(map);
        if (aliasEntities.size() > 0) {
            for (NxAliasEntity aliasEntity : aliasEntities) {
                GbDistributerAliasEntity disAlias = new GbDistributerAliasEntity();
                disAlias.setGbDaDisGoodsId(disGoods.getGbDistributerGoodsId());
                disAlias.setGbDaAliasName(aliasEntity.getNxAliasName());
                gbDistributerAliasService.save(disAlias);
            }
        }

        //保存规格
        List<NxStandardEntity> nxStandardEntities = nxStandardService.queryGoodsStandardListByGoodId(nxGoodsId);
        if (nxStandardEntities.size() > 0) {
            for (NxStandardEntity standardEntity : nxStandardEntities) {
                GbDistributerStandardEntity distributerStandardEntity = new GbDistributerStandardEntity();
                distributerStandardEntity.setGbDsDisGoodsId(disGoods.getGbDistributerGoodsId());
                distributerStandardEntity.setGbDsStandardName(standardEntity.getNxStandardName());
                gbDistributerStandardService.save(distributerStandardEntity);
            }
        }

        return disGoods;
    }


    /**
     * 保存批发商商品（完全按照老项目逻辑）
     */
    private GbDistributerGoodsEntity saveDisGoods(GbDistributerGoodsEntity goods) {
        // 1. 查询 NxGoods 获取基础信息
        Integer nxGoodsId = goods.getGbDgNxGoodsId();
        NxGoodsEntity nxGoodsEntity = nxGoodsService.queryObject(nxGoodsId);

        goods.setGbDgGoodsName(nxGoodsEntity.getNxGoodsName());
        goods.setGbDgNxFatherImg(nxGoodsEntity.getNxGoodsFile());
        goods.setGbDgGoodsStandardname(nxGoodsEntity.getNxGoodsStandardname());
        goods.setGbDgGoodsDetail(nxGoodsEntity.getNxGoodsDetail());
        goods.setGbDgGoodsPlace(nxGoodsEntity.getNxGoodsPlace());
        goods.setGbDgGoodsBrand(nxGoodsEntity.getNxGoodsBrand());
        goods.setGbDgGoodsStandardWeight(nxGoodsEntity.getNxGoodsStandardWeight());
        goods.setGbDgGoodsPinyin(nxGoodsEntity.getNxGoodsPinyin());
        goods.setGbDgGoodsPy(nxGoodsEntity.getNxGoodsPy());
        goods.setGbDgNxFatherId(nxGoodsEntity.getNxGoodsFatherId());
        goods.setGbDgGoodsSort(nxGoodsEntity.getNxGoodsSort());
        goods.setGbDgGoodsSonsSort(nxGoodsEntity.getNxGoodsSonsSort());
        goods.setGbDgQuantityDays(nxGoodsEntity.getNxGoodsQuantityDays());

        goods.setGbDgGoodsIsHidden(0);
        goods.setGbDgGoodsIsWeight(0);
        goods.setGbDgControlPrice(0);
        goods.setGbDgControlFresh(0);
        goods.setGbDgPullOff(0);
        goods.setGbDgGoodsStatus(1);
        goods.setGbDgGoodsType(2);
        goods.setGbDgIsFranchisePrice(0);
        goods.setGbDgIsSelfControl(0);
        goods.setGbDgGoodsInventoryType(1);
        goods.setGbDgGbSupplierId(-1);
        goods.setGbDgNxDistributerId(-1);
        goods.setGbDgNxDistributerGoodsId(-1);
        goods.setGbDgNxDistributerGoodsPrice("0.1");

        Integer GbDgDistributerId = goods.getGbDgDistributerId();
        Integer GbDgNxFatherId = goods.getGbDgNxFatherId();

        // 2. 查询该 NxFatherId 下是否已有商品（完全按照老项目）
        Map<String, Object> map11 = new HashMap<>();
        map11.put("disId", GbDgDistributerId);
        map11.put("nxFatherId", GbDgNxFatherId);
        List<GbDistributerGoodsEntity> nxDistributerGoodsEntities = gbDgService.queryDisGoodsByParams(map11);

        if (nxDistributerGoodsEntities.size() > 0) {
            // 直接复用已有的 fatherId, grandId, greatId
            GbDistributerGoodsEntity disGoodsEntity = nxDistributerGoodsEntities.get(0);
            Integer nxDgDfgGoodsFatherId1 = disGoodsEntity.getGbDgDfgGoodsFatherId();
            Integer nxDgDfgGoodsGrandId = disGoodsEntity.getGbDgDfgGoodsGrandId();
            Integer nxDgDfgGoodsGreatId = disGoodsEntity.getGbDgDfgGoodsGreatId();

            // 给父类商品的字段商品数量加1
            GbDistributerFatherGoodsEntity nxDistributerFatherGoodsEntity = dgfService.queryObject(nxDgDfgGoodsFatherId1);
            Integer nxDfgGoodsAmount = nxDistributerFatherGoodsEntity.getGbDfgGoodsAmount();
            nxDistributerFatherGoodsEntity.setGbDfgGoodsAmount(nxDfgGoodsAmount + 1);
            dgfService.update(nxDistributerFatherGoodsEntity);

            // 设置商品的 fatherId, grandId, greatId
            goods.setGbDgDfgGoodsFatherId(nxDgDfgGoodsFatherId1);
            goods.setGbDgDfgGoodsGrandId(nxDgDfgGoodsGrandId);
            goods.setGbDgDfgGoodsGreatId(nxDgDfgGoodsGreatId);

            // 保存商品
            gbDgService.save(goods);

        } else {
            // 添加fatherGoods的第一个级别（完全按照老项目）
            GbDistributerFatherGoodsEntity dgf = new GbDistributerFatherGoodsEntity();
            dgf.setGbDfgDistributerId(goods.getGbDgDistributerId());
            dgf.setGbDfgFatherGoodsName(goods.getGbDgNxFatherName());
            dgf.setGbDfgFatherGoodsLevel(2);
            dgf.setGbDfgGoodsAmount(1);
            dgf.setGbDfgPriceAmount(0);
            dgf.setGbDfgPriceTwoAmount(0);
            dgf.setGbDfgPriceThreeAmount(0);
            dgf.setGbDfgFatherGoodsColor(goods.getGbDgNxGoodsFatherColor());
            dgf.setGbDfgNxGoodsId(goods.getGbDgNxFatherId());
            dgf.setGbDfgFatherGoodsImg(goods.getGbDgNxFatherImg());
            dgf.setGbDfgFatherGoodsImgLarge(goods.getGbDgNxFatherImgLarge());
            dgf.setGbDfgFatherGoodsSort(nxGoodsEntity.getFatherGoods().getNxGoodsSort());
            dgfService.save(dgf);

            // 更新商品的 fatherGoodsId
            Integer distributerFatherGoodsId = dgf.getGbDistributerFatherGoodsId();
            goods.setGbDgDfgGoodsFatherId(distributerFatherGoodsId);
            // 注意：这里不设置 grandId 和 greatId，老项目注释掉了

            System.out.println("zizin" + dgf.getGbDfgFathersFatherId());
            gbDgService.save(goods);

            // 继续查询是否有 GrandFather（按名字查）
            String grandName = goods.getGbDgNxGrandName();
            Map<String, Object> map2 = new HashMap<>();
            map2.put("disId", GbDgDistributerId);
            map2.put("fathersFatherName", grandName);
            List<GbDistributerFatherGoodsEntity> grandGoodsFather = dgfService.queryHasDisFathersFather(map2);

            if (grandGoodsFather.size() > 0) {
                // 已有 GrandFather，更新 father 的 fathersFatherId
                GbDistributerFatherGoodsEntity gbDistributerFatherGoodsEntity = grandGoodsFather.get(0);
                Integer nxDfgGoodsAmount = dgf.getGbDfgGoodsAmount();
                dgf.setGbDfgGoodsAmount(nxDfgGoodsAmount + 1);
                dgf.setGbDfgFathersFatherId(gbDistributerFatherGoodsEntity.getGbDistributerFatherGoodsId());
                dgfService.update(dgf);

                Integer nxDfgFathersFatherId = gbDistributerFatherGoodsEntity.getGbDistributerFatherGoodsId();
                goods.setGbDgDfgGoodsGrandId(nxDfgFathersFatherId);
                GbDistributerFatherGoodsEntity great = dgfService.queryObject(nxDfgFathersFatherId);
                goods.setGbDgDfgGoodsGreatId(great.getGbDfgFathersFatherId());
                gbDgService.update(goods);

            } else {
                // 创建 Grand
                GbDistributerFatherGoodsEntity grand = new GbDistributerFatherGoodsEntity();
                String nxCgGrandFatherName = goods.getGbDgNxGrandName();
                grand.setGbDfgFatherGoodsName(nxCgGrandFatherName);
                grand.setGbDfgDistributerId(goods.getGbDgDistributerId());
                grand.setGbDfgFatherGoodsLevel(1);
                grand.setGbDfgGoodsAmount(1);
                grand.setGbDfgFatherGoodsColor(goods.getGbDgNxGoodsFatherColor());
                grand.setGbDfgNxGoodsId(goods.getGbDgNxGrandId());
                NxGoodsEntity nxGrand = nxGoodsService.queryObject(goods.getGbDgNxGrandId());
                grand.setGbDfgFatherGoodsImg(nxGrand.getNxGoodsFile());
                grand.setGbDfgFatherGoodsImgLarge(nxGrand.getNxGoodsFileBig());
                grand.setGbDfgFatherGoodsSort(nxGrand.getNxGoodsSort());
                dgfService.save(grand);

                dgf.setGbDfgFathersFatherId(grand.getGbDistributerFatherGoodsId());
                dgfService.update(dgf);

                goods.setGbDgDfgGoodsGrandId(grand.getGbDistributerFatherGoodsId());
                gbDgService.update(goods);

                // 查询是否有 GreatGrand
                Map<String, Object> map3 = new HashMap<>();
                map3.put("disId", GbDgDistributerId);
                String greatGrandName = goods.getGbDgNxGreatGrandName();
                map3.put("fathersFatherName", greatGrandName);
                List<GbDistributerFatherGoodsEntity> greatGrandGoodsFather = dgfService.queryHasDisFathersFather(map3);

                if (greatGrandGoodsFather.size() > 0) {
                    // 已有 GreatGrand
                    GbDistributerFatherGoodsEntity gbDistributerFatherGoodsEntity = greatGrandGoodsFather.get(0);
                    Integer disFatherId = gbDistributerFatherGoodsEntity.getGbDistributerFatherGoodsId();
                    grand.setGbDfgFathersFatherId(disFatherId);
                    Integer gbDfgGoodsAmount = grand.getGbDfgGoodsAmount();
                    grand.setGbDfgGoodsAmount(gbDfgGoodsAmount + 1);
                    dgfService.update(grand);

                    goods.setGbDgDfgGoodsGreatId(disFatherId);
                    gbDgService.update(goods);

                } else {
                    // 创建 GreatGrand
                    GbDistributerFatherGoodsEntity greatGrand = new GbDistributerFatherGoodsEntity();
                    NxGoodsEntity greatGrandEntity = nxGoodsService.queryObject(goods.getGbDgNxGreatGrandId());
                    String greatGrandName1 = goods.getGbDgNxGreatGrandName();
                    greatGrand.setGbDfgFatherGoodsName(greatGrandName1);
                    greatGrand.setGbDfgDistributerId(goods.getGbDgDistributerId());
                    greatGrand.setGbDfgFatherGoodsImg(greatGrandEntity.getNxGoodsFile());
                    greatGrand.setGbDfgFatherGoodsImgLarge(greatGrandEntity.getNxGoodsFileBig());
                    greatGrand.setGbDfgFatherGoodsLevel(0);
                    greatGrand.setGbDfgFathersFatherId(0);
                    greatGrand.setGbDfgFatherGoodsColor(goods.getGbDgNxGoodsFatherColor());
                    greatGrand.setGbDfgNxGoodsId(goods.getGbDgNxGreatGrandId());
                    greatGrand.setGbDfgFatherGoodsSort(greatGrandEntity.getNxGoodsSort());
                    greatGrand.setGbDfgGoodsAmount(1);
                    dgfService.save(greatGrand);

                    grand.setGbDfgFathersFatherId(greatGrand.getGbDistributerFatherGoodsId());
                    dgfService.update(grand);

                    goods.setGbDgDfgGoodsGreatId(greatGrand.getGbDistributerFatherGoodsId());
                    gbDgService.update(goods);
                }
            }
        }

        return goods;
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
}
