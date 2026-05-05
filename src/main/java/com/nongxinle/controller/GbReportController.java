package com.nongxinle.controller;

import com.nongxinle.entity.*;
import com.nongxinle.service.*;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.util.*;

import static com.nongxinle.utils.DateUtils.getHowManyDaysInPeriod;
import static com.nongxinle.utils.GbTypeUtils.*;


/**
 * 报表Controller
 */
@RestController
@RequestMapping("gbreport")
public class GbReportController {

    @Autowired
    private GbReportService gbReportService;
    @Autowired
    private GbDistributerService gbDistributerService;
    @Autowired
    private GbDepartmentService gbDepartmentService;
    @Autowired
    private GbDepartmentGoodsStockService gbDepGoodsStockService;
    @Autowired
    private GbDistributerPurchaseGoodsService gbDistributerPurchaseGoodsService;
    @Autowired
    private GbDistributerPurchaseBatchService gbDistributerPurchaseBatchService;
    @Autowired
    private GbDepartmentGoodsStockReduceService gbDepartmentStockReduceService;
    @Autowired
    private NxJrdhSupplierService jrdhSupplierService;
    @Autowired
    private GbDepartmentUserService gbDepartmentUserService;




    @RequestMapping(value = "/getDisUserReportsCost/{userId}")
    @ResponseBody
    public R getDisUserReportsCost(@PathVariable Integer userId) {

        Map<String, Object> map1 = new HashMap<>();
        map1.put("userId", userId);
        String typesStr = "disCost,subDepCost";
        map1.put("types", Arrays.asList(typesStr.split(",")));
        System.out.println("mapapapappa" + map1);
        List<GbReportEntity> reportEntities = gbReportService.queryReportList(map1);
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (GbReportEntity report : reportEntities) {
            String gbRepType = report.getGbRepType();
            if (gbRepType.equals("disCost")) {
                //获取表数据
                Map<String, Object> map = new HashMap<>();
                map.put("disId", report.getGbRepIds());
                map.put("startDate", report.getGbRepStartDate());
                map.put("stopDate", report.getGbRepStopDate());
                Map<String, Object> stringObjectMap = bbbDisCost(map);
                GbDistributerEntity gbDistributerEntity = gbDistributerService.getById(Integer.valueOf(report.getGbRepIds()));
                stringObjectMap.put("name", gbDistributerEntity.getGbDistributerName());
                stringObjectMap.put("report", report);
                stringObjectMap.put("type", "门店成本统计");
                resultList.add(stringObjectMap);
            }
            if (gbRepType.equals("subDepCost")) {
                //获取表数据
                Map<String, Object> map = new HashMap<>();
                map.put("depId", report.getGbRepIds());
                map.put("startDate", report.getGbRepStartDate());
                map.put("stopDate", report.getGbRepStopDate());
                Map<String, Object> stringObjectMap = bbbSubDepCost(map);
                GbDepartmentEntity departmentEntity = gbDepartmentService.getById(Integer.valueOf(report.getGbRepIds()));
                stringObjectMap.put("name", departmentEntity.getGbDepartmentName());
                stringObjectMap.put("report", report);
                stringObjectMap.put("type", "部门成本统计");
                resultList.add(stringObjectMap);
            }
        }
        return R.ok().put("data", resultList);

    }

    private Map<String, Object> bbbSubDepCost(Map<String, Object> map) {

        Map<String, Object> mapResult = new HashMap<>();
        map.put("dayuStatus", -1);
        Integer stockCount = gbDepartmentStockReduceService.queryReduceTypeCount(map);
        if (stockCount > 0) {
            System.out.println("depcosoostot" + map);
            double doutbleSubtotal = 0;
            double doutbleLossV = 0;
            double doutbleWasteV = 0;
            double doutbleProduceV = 0;
            double doutbleReturnV = 0;

            map.put("equalType", GbConstants.StockReduceType.PRODUCTION);
            Integer integerProduce = gbDepartmentStockReduceService.queryReduceTypeCount(map);
            if (integerProduce > 0) {
                doutbleProduceV = gbDepartmentStockReduceService.queryReduceProduceTotal(map);
                doutbleSubtotal = doutbleSubtotal + doutbleProduceV;
            } else {
                doutbleProduceV = 0;
            }
            map.put("equalType", GbConstants.StockReduceType.LOSS);
            Integer integerLoss = gbDepartmentStockReduceService.queryReduceTypeCount(map);
            if (integerLoss > 0) {
                doutbleLossV = gbDepartmentStockReduceService.queryReduceLossTotal(map);
                doutbleSubtotal = doutbleSubtotal + doutbleLossV;
            } else {
                doutbleLossV = 0;
            }
            map.put("equalType", GbConstants.StockReduceType.WASTE);
            Integer integerWaste = gbDepartmentStockReduceService.queryReduceTypeCount(map);
            if (integerWaste > 0) {
                doutbleWasteV = gbDepartmentStockReduceService.queryReduceWasteTotal(map);
                doutbleSubtotal = doutbleSubtotal + doutbleWasteV;
            } else {
                doutbleWasteV = 0;
            }
            map.put("equalType", GbConstants.StockReduceType.RETURN);
            Integer integerReturn = gbDepartmentStockReduceService.queryReduceTypeCount(map);
            if (integerReturn > 0) {
                doutbleReturnV = gbDepartmentStockReduceService.queryReduceReturnTotal(map);
            } else {
                doutbleReturnV = 0;
            }
            double costTotal = doutbleProduceV + doutbleLossV + doutbleWasteV;
            double doutbleRestV = gbDepGoodsStockService.queryDepGoodsRestTotal(map);


            Object startDdate = map.get("startDate");
            Object stopDate = map.get("stopDate");
            Integer howManyDaysInPeriod = getHowManyDaysInPeriod((String) stopDate, (String) startDdate);

            double v = costTotal / (howManyDaysInPeriod + 1);
            mapResult.put("perCost", new BigDecimal(v).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalSubtotal", new BigDecimal(doutbleSubtotal).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalCost", new BigDecimal(costTotal).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalProduceSubtotal", new BigDecimal(doutbleProduceV).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalReturnSubtotal", new BigDecimal(doutbleReturnV).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalLossSubtotal", new BigDecimal(doutbleLossV).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalWasteSubtotal", new BigDecimal(doutbleWasteV).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalRestSubtotal", new BigDecimal(doutbleRestV).setScale(1, RoundingMode.HALF_UP).toString());

            List<GbDistributerFatherGoodsEntity> greatGrandFatherGoods = new ArrayList<>();
            map.put("equalType", null);
            System.out.println("44444depdididiidsub" + map);
            Integer integer = gbDepartmentStockReduceService.queryReduceTypeCount(map);

            if (integer > 0) {

                greatGrandFatherGoods = gbDepGoodsStockService.queryDepStockTreeFatherGoodsByParams(map);
                for (GbDistributerFatherGoodsEntity greatGrandFather : greatGrandFatherGoods) {
                    double greatGrandTotalCost = 0;
                    double greatGrandTotalCostV = 0;
                    map.put("disGoodsGreatId", greatGrandFather.getGbDistributerFatherGoodsId());
                    Double doutbleProduceWeightDep = 0.0;
                    Double doutbleProduceVDep = 0.0;
                    Double doutbleLossWeightDep = 0.0;
                    Double doutbleLossVDep = 0.0;
                    Double doutbleWasteWeightDep = 0.0;
                    Double doutbleWasteVDep = 0.0;

                    map.put("equalType", GbConstants.StockReduceType.PRODUCTION);
                    System.out.println("coprororo" + map);
                    Integer integerProduceDep = gbDepartmentStockReduceService.queryReduceTypeCount(map);
                    if (integerProduceDep > 0) {
                        doutbleProduceVDep = gbDepartmentStockReduceService.queryReduceProduceTotal(map);
                        doutbleProduceWeightDep = gbDepartmentStockReduceService.queryReduceProduceWeightTotal(map);
                        System.out.println("prooodododdododo" + doutbleProduceVDep);
                    }

                    map.put("equalType", GbConstants.StockReduceType.LOSS);
                    Integer integerLossDep = gbDepartmentStockReduceService.queryReduceTypeCount(map);
                    if (integerLossDep > 0) {
                        doutbleLossVDep = gbDepartmentStockReduceService.queryReduceLossTotal(map);
                        doutbleLossWeightDep = gbDepartmentStockReduceService.queryReduceLossWeightTotal(map);
                    }
                    map.put("equalType", GbConstants.StockReduceType.WASTE);
                    Integer integerWasteDep = gbDepartmentStockReduceService.queryReduceTypeCount(map);
                    if (integerWasteDep > 0) {
                        doutbleWasteVDep = gbDepartmentStockReduceService.queryReduceWasteTotal(map);
                        doutbleWasteWeightDep = gbDepartmentStockReduceService.queryReduceWasteWeightTotal(map);
                    }

                    greatGrandTotalCostV = doutbleProduceWeightDep + doutbleLossWeightDep + doutbleWasteWeightDep;
                    greatGrandTotalCost = doutbleProduceVDep + doutbleLossVDep + doutbleWasteVDep;

                    greatGrandFather.setFatherCostWeightString(new BigDecimal(greatGrandTotalCostV).setScale(2, RoundingMode.HALF_UP).toString());
                    greatGrandFather.setFatherCostSubtotalString(new BigDecimal(greatGrandTotalCost).setScale(2, RoundingMode.HALF_UP).toString());
                    BigDecimal decimal = new BigDecimal(0);
                    if (costTotal > 0) {
                        decimal = new BigDecimal(greatGrandTotalCost).divide(new BigDecimal(costTotal), 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100)).setScale(2, RoundingMode.HALF_UP);
                    }
                    greatGrandFather.setFatherCostSubtotalPercentString(decimal.toString());
                }
                mapResult.put("arr", greatGrandFatherGoods);
                mapResult.put("code", 0);
            } else {
                mapResult.put("code", -1);
            }

        } else {
            mapResult.put("code", -1);
        }

        return mapResult;
    }

    @RequestMapping(value = "/getDisUserReportsStock/{userId}")
    @ResponseBody
    public R getDisUserReportsStock(@PathVariable Integer userId) {

        Map<String, Object> map1 = new HashMap<>();
        map1.put("userId", userId);
        String typesStr = "disStockNow,subDepStockNow";
        map1.put("types", Arrays.asList(typesStr.split(",")));
        System.out.println("mapapapappa" + map1);
        List<GbReportEntity> reportEntities = gbReportService.queryReportList(map1);
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (GbReportEntity report : reportEntities) {
            String gbRepType = report.getGbRepType();
            if (gbRepType.equals("disStockNow")) {
                //获取表数据
                Map<String, Object> map = new HashMap<>();
                map.put("disId", report.getGbRepIds());
                Map<String, Object> stringObjectMap = aaaDepStockTotalNow(map);
                GbDistributerEntity gbDistributerEntity = gbDistributerService.getById(Integer.valueOf(report.getGbRepIds()));
                stringObjectMap.put("name", gbDistributerEntity.getGbDistributerName());
                stringObjectMap.put("report", report);
                stringObjectMap.put("type", "库存商品统计");
                resultList.add(stringObjectMap);
            }
            if (gbRepType.equals("subDepStockNow")) {
                //获取表数据
                Map<String, Object> map = new HashMap<>();
                map.put("depId", report.getGbRepIds());
                Map<String, Object> stringObjectMap = aaaSubDepStockTotalNow(map);
                GbDepartmentEntity departmentEntity = gbDepartmentService.getById(Integer.valueOf(report.getGbRepIds()));
                stringObjectMap.put("name", departmentEntity.getGbDepartmentName());
                stringObjectMap.put("report", report);
                stringObjectMap.put("type", "库存商品统计");
                resultList.add(stringObjectMap);
            }
        }
        return R.ok().put("data", resultList);

    }


    private Map<String, Object> bbbDisCost(Map<String, Object> map) {

        map.put("isGroup", 0);
        System.out.println("niamamamammamamCCCCC" + map);
        List<GbDepartmentEntity> gbDepartmentEntities = gbDepartmentService.queryGroupDepsByDisId(map);

        Map<String, Object> mapResult = new HashMap<>();

        map.put("dayuStatus", -1);

        Integer stockCount = gbDepartmentStockReduceService.queryReduceTypeCount(map);
        if (stockCount > 0) {

            System.out.println("depcosoostot" + map);
            double doutbleSubtotal = 0;
            double doutbleLossV = 0;
            double doutbleWasteV = 0;
            double doutbleProduceV = 0;
            double doutbleReturnV = 0;

            map.put("equalType", GbConstants.StockReduceType.PRODUCTION);
            Integer integerProduce = gbDepartmentStockReduceService.queryReduceTypeCount(map);
            if (integerProduce > 0) {
                doutbleProduceV = gbDepartmentStockReduceService.queryReduceProduceTotal(map);
                doutbleSubtotal = doutbleSubtotal + doutbleProduceV;
            } else {
                doutbleProduceV = 0;
            }
            map.put("equalType", GbConstants.StockReduceType.LOSS);
            Integer integerLoss = gbDepartmentStockReduceService.queryReduceTypeCount(map);
            if (integerLoss > 0) {
                doutbleLossV = gbDepartmentStockReduceService.queryReduceLossTotal(map);
                doutbleSubtotal = doutbleSubtotal + doutbleLossV;
            } else {
                doutbleLossV = 0;
            }
            map.put("equalType", GbConstants.StockReduceType.WASTE);
            Integer integerWaste = gbDepartmentStockReduceService.queryReduceTypeCount(map);
            if (integerWaste > 0) {
                doutbleWasteV = gbDepartmentStockReduceService.queryReduceWasteTotal(map);
                doutbleSubtotal = doutbleSubtotal + doutbleWasteV;
            } else {
                doutbleWasteV = 0;
            }
            map.put("equalType", GbConstants.StockReduceType.RETURN);
            Integer integerReturn = gbDepartmentStockReduceService.queryReduceTypeCount(map);
            if (integerReturn > 0) {
                doutbleReturnV = gbDepartmentStockReduceService.queryReduceReturnTotal(map);
            } else {
                doutbleReturnV = 0;
            }
            double costTotal = doutbleProduceV + doutbleLossV + doutbleWasteV;
            double doutbleRestV = gbDepGoodsStockService.queryDepGoodsRestTotal(map);


            Object startDdate = map.get("startDate");
            Object stopDate = map.get("stopDate");
            Integer howManyDaysInPeriod = getHowManyDaysInPeriod((String) stopDate, (String) startDdate);

            double v = costTotal / (howManyDaysInPeriod + 1);
            mapResult.put("perCost", new BigDecimal(v).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalSubtotal", new BigDecimal(doutbleSubtotal).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalCost", new BigDecimal(costTotal).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalProduceSubtotal", new BigDecimal(doutbleProduceV).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalReturnSubtotal", new BigDecimal(doutbleReturnV).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalLossSubtotal", new BigDecimal(doutbleLossV).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalWasteSubtotal", new BigDecimal(doutbleWasteV).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalRestSubtotal", new BigDecimal(doutbleRestV).setScale(1, RoundingMode.HALF_UP).toString());

            List<GbDistributerFatherGoodsEntity> greatGrandFatherGoods = new ArrayList<>();
            map.put("equalType", null);
            System.out.println("44444depdididiid5555" + map);

            Integer integer = gbDepartmentStockReduceService.queryReduceTypeCount(map);

            if (integer > 0) {

                greatGrandFatherGoods = gbDepGoodsStockService.queryDepStockTreeFatherGoodsByParams(map);
                for (GbDistributerFatherGoodsEntity greatGrandFather : greatGrandFatherGoods) {
                    double greatGrandTotalCost = 0;
                    double greatGrandTotalCostV = 0;
                    map.put("disGoodsGreatId", greatGrandFather.getGbDistributerFatherGoodsId());
                    Double doutbleProduceWeightDep = 0.0;
                    Double doutbleProduceVDep = 0.0;
                    Double doutbleLossWeightDep = 0.0;
                    Double doutbleLossVDep = 0.0;
                    Double doutbleWasteWeightDep = 0.0;
                    Double doutbleWasteVDep = 0.0;

                    map.put("equalType", GbConstants.StockReduceType.PRODUCTION);
                    System.out.println("coprororo" + map);
                    Integer integerProduceDep = gbDepartmentStockReduceService.queryReduceTypeCount(map);
                    if (integerProduceDep > 0) {
                        doutbleProduceVDep = gbDepartmentStockReduceService.queryReduceProduceTotal(map);
                        doutbleProduceWeightDep = gbDepartmentStockReduceService.queryReduceProduceWeightTotal(map);
                        System.out.println("prooodododdododo" + doutbleProduceVDep);
                    }

                    map.put("equalType", GbConstants.StockReduceType.LOSS);
                    Integer integerLossDep = gbDepartmentStockReduceService.queryReduceTypeCount(map);
                    if (integerLossDep > 0) {
                        doutbleLossVDep = gbDepartmentStockReduceService.queryReduceLossTotal(map);
                        doutbleLossWeightDep = gbDepartmentStockReduceService.queryReduceLossWeightTotal(map);
                    }
                    map.put("equalType", GbConstants.StockReduceType.WASTE);
                    Integer integerWasteDep = gbDepartmentStockReduceService.queryReduceTypeCount(map);
                    if (integerWasteDep > 0) {
                        doutbleWasteVDep = gbDepartmentStockReduceService.queryReduceWasteTotal(map);
                        doutbleWasteWeightDep = gbDepartmentStockReduceService.queryReduceWasteWeightTotal(map);
                    }

                    greatGrandTotalCostV = doutbleProduceWeightDep + doutbleLossWeightDep + doutbleWasteWeightDep;
                    greatGrandTotalCost = doutbleProduceVDep + doutbleLossVDep + doutbleWasteVDep;

                    greatGrandFather.setFatherCostWeightString(new BigDecimal(greatGrandTotalCostV).setScale(2, RoundingMode.HALF_UP).toString());
                    greatGrandFather.setFatherCostSubtotalString(new BigDecimal(greatGrandTotalCost).setScale(2, RoundingMode.HALF_UP).toString());
                    BigDecimal decimal = new BigDecimal(0);
                    if (costTotal > 0) {
                        decimal = new BigDecimal(greatGrandTotalCost).divide(new BigDecimal(costTotal), 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100)).setScale(2, RoundingMode.HALF_UP);
                    }
                    greatGrandFather.setFatherCostSubtotalPercentString(decimal.toString());

                }

                if (gbDepartmentEntities.size() > 1) {
                    Double doutbleCostDis = 0.0;
                    for (GbDepartmentEntity gbDepartmentEntity : gbDepartmentEntities) {
                        Map<String, Object> mapDep = new HashMap<>();
                        mapDep.put("depId", gbDepartmentEntity.getGbDepartmentId());

                        Double doutbleProduceVDep = 0.0;
                        Double doutbleLossVDep = 0.0;
                        Double doutbleWasteVDep = 0.0;
                        Double doutbleCostDep = 0.0;
                        mapDep.put("equalType", GbConstants.StockReduceType.PRODUCTION);
                        System.out.println("coprororo" + map);
                        Integer integerProduceDep = gbDepartmentStockReduceService.queryReduceTypeCount(mapDep);
                        if (integerProduceDep > 0) {
                            doutbleProduceVDep = gbDepartmentStockReduceService.queryReduceProduceTotal(mapDep);
                        }

                        mapDep.put("equalType", GbConstants.StockReduceType.LOSS);
                        System.out.println("coprororo" + map);
                        Integer integerLossDep = gbDepartmentStockReduceService.queryReduceTypeCount(mapDep);
                        if (integerLossDep > 0) {
                            doutbleLossVDep = gbDepartmentStockReduceService.queryReduceLossTotal(mapDep);
                        }
                        mapDep.put("equalType", GbConstants.StockReduceType.WASTE);
                        System.out.println("coprororo" + map);
                        Integer integerWasteDep = gbDepartmentStockReduceService.queryReduceTypeCount(mapDep);
                        if (integerWasteDep > 0) {
                            doutbleWasteVDep = gbDepartmentStockReduceService.queryReduceWasteTotal(mapDep);
                        }
                        doutbleCostDep = doutbleProduceVDep + doutbleLossVDep + doutbleWasteVDep;
                        doutbleCostDis = doutbleCostDis + doutbleCostDep;
                        gbDepartmentEntity.setDepProduceGoodsTotalString(new BigDecimal(doutbleProduceVDep).setScale(1, RoundingMode.HALF_UP).toString());
                        gbDepartmentEntity.setDepLossGoodsTotalString(new BigDecimal(doutbleLossVDep).setScale(1, RoundingMode.HALF_UP).toString());
                        gbDepartmentEntity.setDepWasteGoodsTotalString(new BigDecimal(doutbleWasteVDep).setScale(1, RoundingMode.HALF_UP).toString());
                        gbDepartmentEntity.setDepCostGoodsTotalString(new BigDecimal(doutbleCostDep).setScale(1, RoundingMode.HALF_UP).toString());

                    }
                }

                mapResult.put("arr", greatGrandFatherGoods);
                mapResult.put("depArr", gbDepartmentEntities);
                mapResult.put("code", 0);
            }
//            else {
//                mapResult.put("code", -1);
//            }

        }
        else {
            mapResult.put("code", -1);
        }

        return mapResult;
    }


    private Map<String, Object> aaaSubDepStockTotalNow(Map<String, Object> map) {
        Map<String, Object> mapResult = new HashMap<>();
        map.put("dayuStatus", -1);
        map.put("restWeight", 0);
        System.out.println("sotodiididnaoodosoaaaSuubsubDepStockTotalNow" + map);
        List<GbDistributerFatherGoodsEntity> greatGrandFatherGoods = new ArrayList<>();
        double doutbleRest = 0;
        double doutbleRestV = 0;
        Integer integer = gbDepGoodsStockService.queryGoodsStockCount(map);
        if (integer > 0) {
            greatGrandFatherGoods = gbDepGoodsStockService.queryDepStockTreeFatherGoodsByParams(map);
            for (GbDistributerFatherGoodsEntity greatGrandFather : greatGrandFatherGoods) {
                map.put("disGoodsGreatId", greatGrandFather.getGbDistributerFatherGoodsId());
//                double greatGrandTotalRest = gbDepGoodsStockService.queryDepStockRestWeightTotal(map);
                double greatGrandTotalRest = gbDepGoodsStockService.queryDepGoodsRestTotal(map);
                double greatGrandTotalRestV = gbDepGoodsStockService.queryDepGoodsRestTotal(map);
                greatGrandFather.setFatherRestWeightTotalString(new BigDecimal(greatGrandTotalRest).setScale(2, RoundingMode.HALF_UP).toString());
                greatGrandFather.setFatherRestTotalString(new BigDecimal(greatGrandTotalRestV).setScale(2, RoundingMode.HALF_UP).toString());

                doutbleRest = doutbleRest + greatGrandTotalRest;
                doutbleRestV = doutbleRestV + greatGrandTotalRestV;

            }


            //分店总成本
            mapResult.put("arr", greatGrandFatherGoods);
            mapResult.put("totalRest", new BigDecimal(doutbleRestV).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalRestWeight", new BigDecimal(doutbleRest).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("code", 0);
        } else {
            mapResult.put("code", -1);
        }

        return mapResult;
    }

    @RequestMapping(value = "/getDisUserReportsPurchase/{userId}")
    @ResponseBody
    public R getDisUserReportsPurchase(@PathVariable Integer userId) {

        Map<String, Object> map1 = new HashMap<>();
        map1.put("userId", userId);
        String typesStr = "purSupplier,purDepUser";
        map1.put("types", Arrays.asList(typesStr.split(",")));
        System.out.println("mapapapappa" + map1);
        List<GbReportEntity> reportEntities = gbReportService.queryReportList(map1);
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (GbReportEntity report : reportEntities) {
            String gbRepType = report.getGbRepType();
            if (gbRepType.equals("purSupplier")) {
                //获取表数据
                Map<String, Object> map = new HashMap<>();
                map.put("supplierId", report.getGbRepIds());
                map.put("startDate", report.getGbRepStartDate());
                map.put("stopDate", report.getGbRepStopDate());
                Map<String, Object> stringObjectMap = bbbDisPurSupplier(map);
                NxJrdhSupplierEntity supplierEntity = jrdhSupplierService.getById(Integer.valueOf(report.getGbRepIds()));
                stringObjectMap.put("name", supplierEntity.getNxJrdhsSupplierName());
                stringObjectMap.put("report", report);
                stringObjectMap.put("type", "供货商采购统计");
                resultList.add(stringObjectMap);
            }
            if (gbRepType.equals("purDepUser")) {
                //获取表数据
                Map<String, Object> map = new HashMap<>();
                map.put("purUserId", report.getGbRepIds());
                map.put("startDate", report.getGbRepStartDate());
                map.put("stopDate", report.getGbRepStopDate());
                Map<String, Object> stringObjectMap = bbbDisPurUser(map);
                GbDepartmentUserEntity departmentUserEntity = gbDepartmentUserService.getById(Integer.valueOf(report.getGbRepIds()));
                stringObjectMap.put("name", departmentUserEntity.getGbDuWxNickName());
                stringObjectMap.put("report", report);
                stringObjectMap.put("type", "采购员统计");
                resultList.add(stringObjectMap);
            }
        }
        return R.ok().put("data", resultList);

    }

    private Map<String, Object> bbbDisPurUser(Map<String, Object> map) {
        Map<String, Object> mapResult = new HashMap<>();

        map.put("dayuStatus", 1);
        Integer stockCount = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(map);
        if (stockCount > 0) {
            System.out.println("depcosoostotsuppppooliiieieiriri" + map);
            System.out.println("suplieriirpurrr" + map);
            double supplierSubtotal = gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(map);

            Object startDdate = map.get("startDate");
            Object stopDate = map.get("stopDate");
            Integer howManyDaysInPeriod = getHowManyDaysInPeriod((String) stopDate, (String) startDdate);

            double v = supplierSubtotal / (howManyDaysInPeriod + 1);
            mapResult.put("perCost", new BigDecimal(v).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalSubtotal", new BigDecimal(supplierSubtotal).setScale(1, RoundingMode.HALF_UP).toString());
            map.put("typeNotEqual", 9);
            map.put("supplierBuy", -1);
            map.put("dayuStatus", 2);
//            map.put("offset", 0);
//            map.put("limit", 100);
            List<GbDistributerGoodsEntity> gbDistributerGoodsEntities = gbDistributerPurchaseGoodsService.queryDisTreeGoodsWithPurList(map);
            mapResult.put("arr", gbDistributerGoodsEntities);
            mapResult.put("code", 0);
        } else {
            mapResult.put("code", -1);
        }


        return mapResult;
    }



    private Map<String, Object> bbbDisPurSupplier(Map<String, Object> map) {
        Map<String, Object> mapResult = new HashMap<>();

        map.put("dayuStatus", 1);
        Integer stockCount = gbDistributerPurchaseBatchService.queryDisPurchaseBatchCount(map);
        if (stockCount > 0) {
            double supplierSubtotal = 0;
            double tuihuoSubtotal = 0;
            map.put("notEqualPurchaseType", 9);
            System.out.println("suplieriirpurrr22222" + map);
            int count = gbDistributerPurchaseBatchService.queryDisPurchaseBatchCount(map);
            if (count > 0) {
                supplierSubtotal = gbDistributerPurchaseBatchService.querySupplierUnSettleSubtotal(map);
            }

            map.put("notEqualPurchaseType", null);
            map.put("purchaseType", 9);
            System.out.println("suplieriirpurrr333333" + map);
            int tuihuoCount = gbDistributerPurchaseBatchService.queryDisPurchaseBatchCount(map);
            if (tuihuoCount > 0) {
                tuihuoSubtotal = gbDistributerPurchaseBatchService.querySupplierUnSettleSubtotal(map);
            }
            double v1 = supplierSubtotal - tuihuoSubtotal;

            Object startDdate = map.get("startDate");
            Object stopDate = map.get("stopDate");
            Integer howManyDaysInPeriod = getHowManyDaysInPeriod((String) stopDate, (String) startDdate);

            double v = v1 / (howManyDaysInPeriod + 1);
            mapResult.put("perCost", new BigDecimal(v).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("allTotalSubtotal", new BigDecimal(supplierSubtotal).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("tuihuoSubtotal", new BigDecimal(tuihuoSubtotal).setScale(1, RoundingMode.HALF_UP).toString());

            double unPaySupplierTotal = 0;
            double unPayTuihuoTotal = 0;
            double havePaySupplierTotalPay = 0;
            double havePayTuihuoTotalPay = 0;
            map.put("status", 4);
            map.put("purchaseType", null);
            map.put("notEqualPurchaseType", 9);
            Integer integer = gbDistributerPurchaseBatchService.queryDisPurchaseBatchCount(map);
            if (integer > 0) {
                unPaySupplierTotal = gbDistributerPurchaseBatchService.querySupplierUnSettleSubtotal(map);
            }
            map.put("purchaseType", 9);
            map.put("notEqualPurchaseType", null);
            Integer integerTui = gbDistributerPurchaseBatchService.queryDisPurchaseBatchCount(map);
            if (integerTui > 0) {
                unPayTuihuoTotal = gbDistributerPurchaseBatchService.querySupplierUnSettleSubtotal(map);
            }

            map.put("status", null);
            map.put("equalStatus", 4);
            Integer integer2 = gbDistributerPurchaseBatchService.queryDisPurchaseBatchCount(map);
            if (integer2 > 0) {
                havePaySupplierTotalPay = gbDistributerPurchaseBatchService.querySupplierUnSettleSubtotal(map);
            }

            double v2 = unPaySupplierTotal - unPayTuihuoTotal;

            mapResult.put("unPaySubtotal", new BigDecimal(v2).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("havePaySubtotal", new BigDecimal(havePaySupplierTotalPay).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("code", 0);
        } else {
            mapResult.put("code", -1);
        }


        return mapResult;
    }


    private Map<String, Object> aaaDepStockTotalNow(Map<String, Object> map) {
        Map<String, Object> mapResult = new HashMap<>();
        map.put("isGroup", 0);
        System.out.println("niamamamammamam" + map);
        List<GbDepartmentEntity> gbDepartmentEntities = gbDepartmentService.queryGroupDepsByDisId(map);

        map.put("dayuStatus", -1);
        map.put("restWeight", 0);
        System.out.println("sotodiididnaoodosoaaaDepStockTotalNow" + map);
        List<GbDistributerFatherGoodsEntity> greatGrandFatherGoods = new ArrayList<>();
        List<GbDistributerFatherGoodsEntity> resultFatherGoodsList = new ArrayList<>();

        double doutbleRest = 0;
        double doutbleRestV = 0;
        Integer integer = gbDepGoodsStockService.queryGoodsStockCount(map);

        if (integer > 0) {

            greatGrandFatherGoods = gbDepGoodsStockService.queryDepStockTreeFatherGoodsByParams(map);
            for (GbDistributerFatherGoodsEntity greatGrandFather : greatGrandFatherGoods) {
                double greatGrandTotalRest = 0;
                double greatGrandTotalRestV = 0;
                List<GbDistributerFatherGoodsEntity> grandGoodsEntities = greatGrandFather.getFatherGoodsEntities();
                for (GbDistributerFatherGoodsEntity grandFather : grandGoodsEntities) {
                    double grandDoubleRest = 0;
                    double grandDoubleRestV = 0;
                    Integer gbDistributerFatherGoodsId = grandFather.getGbDistributerFatherGoodsId();
                    map.put("disGoodsGrandId", gbDistributerFatherGoodsId);
                    Double fatherDoubleRest = gbDepGoodsStockService.queryDepGoodsRestWeightTotal(map);
                    Double fatherDoubleRestV = gbDepGoodsStockService.queryDepGoodsRestTotal(map);
                    grandDoubleRestV = grandDoubleRestV + fatherDoubleRestV;
                    greatGrandTotalRestV = greatGrandTotalRestV + fatherDoubleRestV;

                    grandDoubleRest = grandDoubleRest + fatherDoubleRest;
                    greatGrandTotalRest = greatGrandTotalRest + fatherDoubleRest;
                    grandFather.setFatherRestWeightTotalString(new BigDecimal(grandDoubleRest).setScale(1, RoundingMode.HALF_UP).toString());
                    grandFather.setFatherRestTotalString(new BigDecimal(grandDoubleRestV).setScale(1, RoundingMode.HALF_UP).toString());
                    resultFatherGoodsList.add(grandFather);
                }
                greatGrandFather.setFatherRestWeightTotalString(new BigDecimal(greatGrandTotalRestV).setScale(2, RoundingMode.HALF_UP).toString());
                greatGrandFather.setFatherRestTotalString(new BigDecimal(greatGrandTotalRest).setScale(2, RoundingMode.HALF_UP).toString());


                doutbleRest = doutbleRest + greatGrandTotalRest;
                doutbleRestV = doutbleRestV + greatGrandTotalRestV;

            }

            if (gbDepartmentEntities.size() > 1) {
                for (GbDepartmentEntity gbDepartmentEntity : gbDepartmentEntities) {
                    map.put("depId", gbDepartmentEntity.getGbDepartmentId());
                    map.put("disGoodsGrandId", null);
                    System.out.println("couanmapa[pa" + map);
                    int count = gbDepGoodsStockService.queryGoodsStockCount(map);
                    Double fatherDoubleRest = 0.0;
                    Double fatherDoubleRestV = 0.0;
                    if (count > 0) {
//                        fatherDoubleRest = gbDepGoodsStockService.queryDepStockRestWeightTotal(map);
                        fatherDoubleRest = gbDepGoodsStockService.queryDepStockRestSubtotal(map);
                        fatherDoubleRestV = gbDepGoodsStockService.queryDepGoodsRestTotal(map);
                    }

                    gbDepartmentEntity.setDepStockSubtotalString(new BigDecimal(fatherDoubleRestV).setScale(1, RoundingMode.HALF_UP).toString());
                    gbDepartmentEntity.setDepStockWeightTotalString(new BigDecimal(fatherDoubleRest).setScale(1, RoundingMode.HALF_UP).toString());
                }
            }
            //分店总成本
            mapResult.put("depArr", gbDepartmentEntities);
            mapResult.put("arr", resultFatherGoodsList);
            mapResult.put("totalRest", new BigDecimal(doutbleRestV).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalRestWeight", new BigDecimal(doutbleRest).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("code", 0);
        } else {
            mapResult.put("code", -1);
        }

        return mapResult;
    }

    /**
     * 获取用户营业报表
     */
    @RequestMapping(value = "/getDisUserReportsBusiness/{userId}")
    @ResponseBody
    public R getDisUserReportsBusiness(@PathVariable Integer userId) {

        Map<String, Object> map1 = new java.util.HashMap<>();
        map1.put("userId", userId);
        String typesStr = "disBusiness,subDepBusiness";
        map1.put("types", Arrays.asList(typesStr.split(",")));
        System.out.println("mapapapappaBUsinesss" + map1);

        List<GbReportEntity> reportEntities = gbReportService.queryReportList(map1);
        List<Map<String, Object>> resultList = new ArrayList<>();

        for (GbReportEntity report : reportEntities) {
            String gbRepType = report.getGbRepType();

            Map<String, Object> map = new java.util.HashMap<>();
            map.put("startDate", report.getGbRepStartDate());
            map.put("stopDate", report.getGbRepStopDate());

            if ("disBusiness".equals(gbRepType)) {
                map.put("disId", report.getGbRepIds());
                Map<String, Object> stringObjectMap = bbbDisBusiness(map);
                GbDistributerEntity gbDistributerEntity = gbDistributerService.getById(report.getGbRepIds());
                stringObjectMap.put("name", gbDistributerEntity != null ? gbDistributerEntity.getGbDistributerName() : "");
                stringObjectMap.put("report", report);
                stringObjectMap.put("type", "门店营业统计");
                resultList.add(stringObjectMap);
            }
            if ("subDepBusiness".equals(gbRepType)) {
                map.put("depId", report.getGbRepIds());
                Map<String, Object> stringObjectMap = bbbSubDepBusiness(map);
                GbDepartmentEntity departmentEntity = gbDepartmentService.getById(report.getGbRepIds());
                stringObjectMap.put("name", departmentEntity != null ? departmentEntity.getGbDepartmentName() : "");
                stringObjectMap.put("report", report);
                stringObjectMap.put("type", "部门营业统计");
                resultList.add(stringObjectMap);
            }
        }
        return R.ok().put("data", resultList);
    }

    /**
     * 门店营业统计（简化版）
     */
    private Map<String, Object> bbbDisBusiness(Map<String, Object> map) {

        Map<String, Object> mapResult = new HashMap<>();
        // TODO: 实现完整的门店营业统计逻辑
        Object startDate = map.get("startDate");
        Object stopDate = map.get("stopDate");

        map.put("dayuStatus", -1);

        Integer integer = gbDepGoodsStockService.queryDisStockGoodsCount(map);
        System.out.println("depBusinessssssss");

        if (integer > 0 ) {

            //dis采购
            double purchaseTotal = 0.0;
            Integer stockCount = gbDepGoodsStockService.queryGoodsStockCount(map);
            if (stockCount > 0) {
                purchaseTotal = gbDepGoodsStockService.queryDepGoodsSubtotal(map);
            }

            //dis 支出
            double doutbleCost = 0;
            double doutbleProduce = 0;
            double doutbleWaste = 0;
            double doutbleLoss = 0;
            //dis 退货
            double doutbleReturn = 0;
            // 本期退货
            double doutbleReturnPur = 0;
            //本期库存
            double aDoubleStockPur = 0.0;
            //总库存
            double aDoubleStock = 0.0;

            // 本期支出
            double costTotalPur = 0.0;

            // 本期支出：制作(1) / 损失(3) / 废弃(2) 从库存扣减表汇总，不再使用部门商品日报
            doutbleProduce = gbDepartmentStockReduceService.queryReduceProduceTotal(map);
            doutbleLoss = gbDepartmentStockReduceService.queryReduceLossTotal(map);
            doutbleWaste = gbDepartmentStockReduceService.queryReduceWasteTotal(map);
            doutbleCost = doutbleProduce + doutbleLoss + doutbleWaste;

            double producePercent = doutbleProduce / doutbleCost * 100;
            double lossPercent = doutbleLoss / doutbleCost * 100;
            double wastePercent = doutbleWaste / doutbleCost * 100;

            //dis总退货
            map.put("equalType", 4);
            System.out.println("dis总退货dis总退货" + map);
            Integer integer2 = gbDepartmentStockReduceService.queryReduceTypeCount(map);
            if(integer2 > 0){
                doutbleReturn = gbDepartmentStockReduceService.queryReduceReturnTotal(map);
            }

            // 本期退货
            map.put("startDate", null);
            map.put("startPurchaseDate", startDate);
            System.out.println("本期退货本期退货" + map);
            Integer integerPur = gbDepartmentStockReduceService.queryReduceTypeCount(map);
            if(integerPur > 0){
                doutbleReturnPur = gbDepartmentStockReduceService.queryReduceReturnTotal(map);
            }
            double lastReturn = doutbleReturn - doutbleReturnPur;

            //dis 本期库存

            System.out.println("本期库存本期库存" + map);
            map.put("equalType",null);
            map.put("startPurchaseDate",null);
            map.put("startDate",startDate);
            Integer stockCount1 = gbDepGoodsStockService.queryGoodsStockCount(map);
            if(stockCount1 > 0){
                aDoubleStockPur = gbDepGoodsStockService.queryDepGoodsRestTotal(map);
            }

            map.put("startDate", null);
            map.put("stopDate", null);
            // dis 总库存
            Integer stockCountall = gbDepGoodsStockService.queryGoodsStockCount(map);
            if(stockCountall > 0){
                aDoubleStock = gbDepGoodsStockService.queryDepGoodsRestTotal(map);
            }
            double lastRest = aDoubleStock - aDoubleStockPur;

            map.put("startPurchaseDate", startDate);
            map.put("stopDate", stopDate);
            System.out.println("mappouuuruu333" + map);
            Integer integer1 = gbDepartmentStockReduceService.queryReduceTypeCount(map);
            if(integer1 > 0){
                costTotalPur =  gbDepartmentStockReduceService.queryReduceCostSubtotal(map);
            }

            double lastCost = doutbleCost - costTotalPur;

            mapResult.put("purchaseTotal", new BigDecimal(purchaseTotal).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("costTotalPur", new BigDecimal(costTotalPur).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalCost", new BigDecimal(doutbleCost).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("lastCost", new BigDecimal(lastCost).setScale(1, RoundingMode.HALF_UP).toString());

            mapResult.put("totalProduce", new BigDecimal(doutbleProduce).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("producePercent", new BigDecimal(producePercent).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalWaste", new BigDecimal(doutbleWaste).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("wastePercent", new BigDecimal(wastePercent).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalLoss", new BigDecimal(doutbleLoss).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("lossPercent", new BigDecimal(lossPercent).setScale(1, RoundingMode.HALF_UP).toString());

            mapResult.put("totalReturn", new BigDecimal(doutbleReturn).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalReturnPur", new BigDecimal(doutbleReturnPur).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("lastReturn", new BigDecimal(lastReturn).setScale(1, RoundingMode.HALF_UP).toString());

            mapResult.put("totalRest", new BigDecimal(aDoubleStockPur).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("totalRestAll", new BigDecimal(aDoubleStock).setScale(1, RoundingMode.HALF_UP).toString());
            mapResult.put("lastRest", new BigDecimal(lastRest).setScale(1, RoundingMode.HALF_UP).toString());

            mapResult.put("code", 0);

        } else {
            mapResult.put("code", -1);
        }

        return mapResult;
    }

    /**
     * 部门营业统计（简化版）
     */
    private Map<String, Object> bbbSubDepBusiness(Map<String, Object> map) {
        Map<String, Object> result = new java.util.HashMap<>();
        // TODO: 实现完整的部门营业统计逻辑
        result.put("depId", map.get("depId"));
        result.put("startDate", map.get("startDate"));
        result.put("stopDate", map.get("stopDate"));
        return result;
    }


    @RequestMapping(value = "/delteReport/{id}")
    @ResponseBody
    public R delteReport(@PathVariable Integer id) {
        gbReportService.removeById(id);
        return R.ok();
    }

    @RequestMapping(value = "/saveReportCost", method = RequestMethod.POST)
    @ResponseBody
    public R saveReportCost(@RequestBody GbReportEntity reportEntity) {
        gbReportService.save(reportEntity);
        return R.ok();
    }




    @RequestMapping("/downloadReportExcelGb")
    @ResponseBody
    public void downloadReportExcelGb(HttpServletResponse response, HttpServletRequest request) {
        System.out.println("=== 开始Excel下载流程 ===");
        String id = request.getParameter("id");
        System.out.println("请求时间: " + new java.util.Date());

        HSSFWorkbook wb = null;
        try {
            System.out.println("下载报表ID: " + id);

            GbReportEntity reportEntity = gbReportService.getById(Integer.valueOf(id));

            // 初始化workbook
            wb = new HSSFWorkbook();

            if (reportEntity.getGbRepType().equals("subDepStockNow")) {
                System.out.println("生成类型: subDepStockNow");
                wb = toCreatSubDepStockNowForm(reportEntity);
            }
            if (reportEntity.getGbRepType().equals("disStockNow")) {
                System.out.println("生成类型: disStockNow");
                wb = toCreatDisStockNowForm(reportEntity);
            }

            if (reportEntity.getGbRepType().equals("depCost")) {
                System.out.println("生成类型: depCost");
                wb = toCreatDepCostForm(reportEntity);
            }
            if (reportEntity.getGbRepType().equals("disCost")) {
                System.out.println("生成类型: disCost");
                wb = toCreatDisCostForm(reportEntity);
            }

            if (reportEntity.getGbRepType().equals("disBusiness")) {
                System.out.println("生成类型: disBusiness");
                wb = toCreatDisBusinessForm(reportEntity);
            }
            if (reportEntity.getGbRepType().equals("subDepBusiness")) {
                System.out.println("生成类型: subDepBusiness");
                wb = toCreatSubDepBusinessForm(reportEntity);
            }

            if (reportEntity.getGbRepType().equals("purSupplier")) {
                System.out.println("生成类型: purSupplier");
                wb = toCreatSupplierPurGoodsForm(reportEntity);
            }
            if (reportEntity.getGbRepType().equals("subDepCost")) {
                System.out.println("生成类型: subDepCost");
                wb = toCreatSubDepCostForm(reportEntity);
            }
            if (reportEntity.getGbRepType().equals("purDepUser")) {
                System.out.println("生成类型: purDepUser");
                wb = toCreatPurDepUserForm(reportEntity);
            }

            // 设置响应头
            String fileName = URLEncoder.encode("导出商品.xls", "UTF-8");
            System.out.println("设置文件名: " + fileName);

            // 设置正确的Content-Type
            response.setContentType("application/vnd.ms-excel");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

            System.out.println("开始写入Excel数据到响应流...");
            wb.write(response.getOutputStream());
            System.out.println("Excel数据写入完成");

        } catch (Exception e) {
            System.err.println("Excel下载过程中发生错误: " + e.getMessage());
            e.printStackTrace();
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Excel文件生成失败");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } finally {
            // 确保资源被正确释放
            if (wb != null) {
                try {
                    wb.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    // 辅助方法：安全格式化数字，避免除零和精度问题
    private String formatDecimal(Double value) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return "0.0";
        }
        return new BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toString();
    }

    private static double costExcelNz(Double v) {
        return v == null ? 0.0 : v;
    }


    private HSSFWorkbook toCreatSupplierPurGoodsForm(GbReportEntity reportEntity) {
        HSSFWorkbook wb = new HSSFWorkbook();
        Map<String, Object> map = new HashMap<>();
        map.put("startDate", reportEntity.getGbRepStartDate());
        map.put("stopDate", reportEntity.getGbRepStopDate());
        map.put("supplierId", reportEntity.getGbRepIds());
        map.put("typeNotEqual", 9);
        map.put("dayuStatus", 2);
        // 调试信息
        sheetCreatPurchase(wb, map, reportEntity);
        return wb;

    }

    private HSSFWorkbook toCreatDisBusinessForm(GbReportEntity reportEntity) {
        HSSFWorkbook wb = new HSSFWorkbook();
        Map<String, Object> map = new HashMap<>();
        map.put("startDate", reportEntity.getGbRepStartDate());
        map.put("stopDate", reportEntity.getGbRepStopDate());
        map.put("disId", Integer.valueOf(reportEntity.getGbRepIds()));

        //第一个 sheet 是采购列表
        wb = sheetCreatPurchaseSingle(wb, map, reportEntity);

        //第二个 sheet 是支出列表，制作，损耗，废弃
        wb = sheetCreatCostSingle(wb, map, reportEntity);

        // 第三个sheet 是库存列表
        map.put("disGoodsGrandId", null);
        wb = sheetCreatStockSingle(wb, map, reportEntity);

        //第四个 sheet 是采购列表
        map.put("restWeight", null);
        wb = sheetCreatPurchaseTuihuoSingle(wb, map, reportEntity);

        return wb;
    }
    private HSSFWorkbook toCreatSubDepBusinessForm(GbReportEntity reportEntity) {
        HSSFWorkbook wb = new HSSFWorkbook();
        Map<String, Object> map = new HashMap<>();
        map.put("startDate", reportEntity.getGbRepStartDate());
        map.put("stopDate", reportEntity.getGbRepStopDate());
        map.put("depId", Integer.valueOf(reportEntity.getGbRepIds()));

        //第一个 sheet 是采购列表
        wb = sheetCreatPurchaseSingle(wb, map, reportEntity);

        //第二个 sheet 是支出列表，制作，损耗，废弃
        wb = sheetCreatCostSingle(wb, map, reportEntity);

        // 第三个sheet 是库存列表
        map.put("disGoodsGrandId", null);
        wb = sheetCreatStockSingle(wb, map, reportEntity);

        //第四个 sheet 是采购列表
        System.out.println("tuithuiutiutit" );
        map.put("restWeight", null);
        wb = sheetCreatPurchaseTuihuoSingle(wb, map, reportEntity);

        return wb;
    }

    private HSSFWorkbook sheetCreatCost(HSSFWorkbook wb, Map<String, Object> map, GbReportEntity reportEntity) {
        System.out.println("creatCostSheetcreatCostSheet");
        List<GbDistributerFatherGoodsEntity> distributerFatherGoodsEntities = gbDepGoodsStockService.queryDepStockTreeFatherGoodsByParams(map);

        if (distributerFatherGoodsEntities != null && distributerFatherGoodsEntities.size() > 0) {
            for (GbDistributerFatherGoodsEntity greatGrandFather : distributerFatherGoodsEntities) {
                for (GbDistributerFatherGoodsEntity grand : greatGrandFather.getFatherGoodsEntities()) {
                    String sheetName = grand.getGbDfgFatherGoodsName() != null ? grand.getGbDfgFatherGoodsName() : "未命名工作表";
                    if (sheetName.length() > 31) {
                        sheetName = sheetName.substring(0, 31);
                    }
                    HSSFSheet sheet = wb.createSheet(sheetName);
                    //设置表头
                    HSSFRow row1 = sheet.createRow(0);
                    row1.createCell(0).setCellValue("序号");
                    row1.createCell(1).setCellValue("商品名称");
                    row1.createCell(2).setCellValue("规格");
                    row1.createCell(3).setCellValue("品牌");
                    row1.createCell(4).setCellValue("详细");
                    row1.createCell(5).setCellValue("总成本数量");
                    row1.createCell(6).setCellValue("制作数量");
                    row1.createCell(7).setCellValue("损耗数量");
                    row1.createCell(8).setCellValue("废弃数量");
                    row1.createCell(9).setCellValue("退货数量");
                    row1.createCell(10).setCellValue("总成本");
                    row1.createCell(11).setCellValue("销售成本");
                    row1.createCell(12).setCellValue("损耗成本");
                    row1.createCell(13).setCellValue("废弃成本");
                    row1.createCell(14).setCellValue("退货成本");
                    row1.createCell(15).setCellValue("库存数量");
                    row1.createCell(16).setCellValue("库存成本");

                    map.put("disGoodsGrandId", grand.getGbDistributerFatherGoodsId());
                    List<GbDistributerGoodsEntity> goodsEntities = gbDepGoodsStockService.queryDisGoodsStockByParams(map);
                    //设置表体
                    HSSFRow goodsRow = null;
                    if (goodsEntities != null && goodsEntities.size() > 0) {
                        for (int i = 0; i < goodsEntities.size(); i++) {
                            GbDistributerGoodsEntity ckGoodsEntity = goodsEntities.get(i);
                            goodsRow = sheet.createRow(sheet.getLastRowNum() + 1);
                            goodsRow.createCell(0).setCellValue(sheet.getLastRowNum());
                            goodsRow.createCell(1).setCellValue(ckGoodsEntity.getGbDgGoodsName());
                            goodsRow.createCell(2).setCellValue(ckGoodsEntity.getGbDgGoodsStandardname());
                            goodsRow.createCell(3).setCellValue(ckGoodsEntity.getGbDgGoodsBrand());
                            goodsRow.createCell(4).setCellValue(ckGoodsEntity.getGbDgGoodsDetail());

                            //5 totalWeight
                            Map<String, Object> disGoodsMap = new HashMap<>();
                            disGoodsMap.put("startDate", reportEntity.getGbRepStartDate());
                            disGoodsMap.put("stopDate", reportEntity.getGbRepStopDate());
                            disGoodsMap.put("disGoodsId", ckGoodsEntity.getGbDistributerGoodsId());
                            for (String scopeKey : Arrays.asList("disId", "depId", "depFatherId", "dayuStatus", "depType")) {
                                Object scopeVal = map.get(scopeKey);
                                if (scopeVal != null) {
                                    disGoodsMap.put(scopeKey, scopeVal);
                                }
                            }

                            Double aDoubleRT = 0.0;
                            Double aDoubleRTV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.PRODUCTION);
                            Integer integerProduce = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerProduce != null && integerProduce > 0) {
                                aDoubleRT = gbDepartmentStockReduceService.queryReduceProduceWeightTotal(disGoodsMap);
                                aDoubleRTV = gbDepartmentStockReduceService.queryReduceProduceTotal(disGoodsMap);
                            }
                            goodsRow.createCell(6).setCellValue(formatDecimal(aDoubleRT));
                            goodsRow.createCell(11).setCellValue(new BigDecimal(aDoubleRTV).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleS = 0.0;
                            Double aDoubleSV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.LOSS);
                            Integer integerLoss = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerLoss != null && integerLoss > 0) {
                                aDoubleS = gbDepartmentStockReduceService.queryReduceLossWeightTotal(disGoodsMap);
                                aDoubleSV = gbDepartmentStockReduceService.queryReduceLossTotal(disGoodsMap);
                            }
                            goodsRow.createCell(7).setCellValue(new BigDecimal(aDoubleS != null ? aDoubleS : 0.0).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(12).setCellValue(new BigDecimal(aDoubleSV != null ? aDoubleSV : 0.0).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleST = 0.0;
                            Double aDoubleSTV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.WASTE);
                            Integer integerWaste = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerWaste != null && integerWaste > 0) {
                                aDoubleST = gbDepartmentStockReduceService.queryReduceWasteWeightTotal(disGoodsMap);
                                aDoubleSTV = gbDepartmentStockReduceService.queryReduceWasteTotal(disGoodsMap);
                            }
                            goodsRow.createCell(8).setCellValue(new BigDecimal(aDoubleST != null ? aDoubleST : 0.0).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(13).setCellValue(new BigDecimal(aDoubleSTV != null ? aDoubleSTV : 0.0).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleRTW = 0.0;
                            Double aDoubleRTWV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.RETURN);
                            Integer integerReturn = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerReturn != null && integerReturn > 0) {
                                aDoubleRTW = gbDepartmentStockReduceService.queryReduceReturnWeightTotal(disGoodsMap);
                                aDoubleRTWV = gbDepartmentStockReduceService.queryReduceReturnTotal(disGoodsMap);
                            }
                            goodsRow.createCell(9).setCellValue(new BigDecimal(aDoubleRTW != null ? aDoubleRTW : 0.0).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(14).setCellValue(new BigDecimal(aDoubleRTWV != null ? aDoubleRTWV : 0.0).setScale(1, RoundingMode.HALF_UP).toString());

                            double aDoubleRV = costExcelNz(aDoubleRTV) + costExcelNz(aDoubleSV) + costExcelNz(aDoubleSTV);
                            double aDoubleR = costExcelNz(aDoubleRT) + costExcelNz(aDoubleS) + costExcelNz(aDoubleST);
                            goodsRow.createCell(5).setCellValue(formatDecimal(aDoubleR));
                            goodsRow.createCell(10).setCellValue(new BigDecimal(aDoubleRV).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleRRest = gbDepGoodsStockService.queryDepGoodsRestWeightTotal(disGoodsMap);
                            goodsRow.createCell(15).setCellValue(formatDecimal(aDoubleRRest));
                            Double aDoubleRRestV = gbDepGoodsStockService.queryDepGoodsRestTotal(disGoodsMap);
                            goodsRow.createCell(16).setCellValue(new BigDecimal(costExcelNz(aDoubleRRestV)).setScale(1, RoundingMode.HALF_UP).toString());

                        }
                    }
                }
            }
        }


        return wb;

    }

    // 备份原始方法 - 为每个商品分类创建多个sheet
    private HSSFWorkbook sheetCreatCostOriginal(HSSFWorkbook wb, Map<String, Object> map, GbReportEntity reportEntity) {
        System.out.println("creatCostSheetcreatCostSheet");
        List<GbDistributerFatherGoodsEntity> distributerFatherGoodsEntities = gbDepGoodsStockService.queryDepStockTreeFatherGoodsByParams(map);

        if (distributerFatherGoodsEntities != null && distributerFatherGoodsEntities.size() > 0) {
            for (GbDistributerFatherGoodsEntity greatGrandFather : distributerFatherGoodsEntities) {
                for (GbDistributerFatherGoodsEntity grand : greatGrandFather.getFatherGoodsEntities()) {
                    String sheetName = grand.getGbDfgFatherGoodsName() != null ? grand.getGbDfgFatherGoodsName() : "未命名工作表";
                    if (sheetName.length() > 31) {
                        sheetName = sheetName.substring(0, 31);
                    }
                    HSSFSheet sheet = wb.createSheet(sheetName);
                    //设置表头
                    HSSFRow row1 = sheet.createRow(0);
                    row1.createCell(0).setCellValue("序号");
                    row1.createCell(1).setCellValue("商品名称");
                    row1.createCell(2).setCellValue("规格");
                    row1.createCell(3).setCellValue("品牌");
                    row1.createCell(4).setCellValue("详细");
                    row1.createCell(5).setCellValue("总成本数量");
                    row1.createCell(6).setCellValue("制作数量");
                    row1.createCell(7).setCellValue("损耗数量");
                    row1.createCell(8).setCellValue("废弃数量");
                    row1.createCell(9).setCellValue("退货数量");
                    row1.createCell(10).setCellValue("总成本");
                    row1.createCell(11).setCellValue("销售成本");
                    row1.createCell(12).setCellValue("损耗成本");
                    row1.createCell(13).setCellValue("废弃成本");
                    row1.createCell(14).setCellValue("退货成本");
                    row1.createCell(15).setCellValue("库存数量");
                    row1.createCell(16).setCellValue("库存成本");

                    map.put("disGoodsGrandId", grand.getGbDistributerFatherGoodsId());
                    List<GbDistributerGoodsEntity> goodsEntities = gbDepGoodsStockService.queryDisGoodsStockByParams(map);
                    //设置表体
                    HSSFRow goodsRow = null;
                    if (goodsEntities != null && goodsEntities.size() > 0) {
                        for (int i = 0; i < goodsEntities.size(); i++) {
                            GbDistributerGoodsEntity ckGoodsEntity = goodsEntities.get(i);
                            goodsRow = sheet.createRow(sheet.getLastRowNum() + 1);
                            goodsRow.createCell(0).setCellValue(sheet.getLastRowNum());
                            goodsRow.createCell(1).setCellValue(ckGoodsEntity.getGbDgGoodsName());
                            goodsRow.createCell(2).setCellValue(ckGoodsEntity.getGbDgGoodsStandardname());
                            goodsRow.createCell(3).setCellValue(ckGoodsEntity.getGbDgGoodsBrand());
                            goodsRow.createCell(4).setCellValue(ckGoodsEntity.getGbDgGoodsDetail());

                            //5 totalWeight
                            Map<String, Object> disGoodsMap = new HashMap<>();
                            disGoodsMap.put("startDate", reportEntity.getGbRepStartDate());
                            disGoodsMap.put("stopDate", reportEntity.getGbRepStopDate());
                            disGoodsMap.put("disGoodsId", ckGoodsEntity.getGbDistributerGoodsId());
                            for (String scopeKey : Arrays.asList("disId", "depId", "depFatherId", "dayuStatus", "depType")) {
                                Object scopeVal = map.get(scopeKey);
                                if (scopeVal != null) {
                                    disGoodsMap.put(scopeKey, scopeVal);
                                }
                            }

                            Double aDoubleRT = 0.0;
                            Double aDoubleRTV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.PRODUCTION);
                            Integer integerProduce = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerProduce != null && integerProduce > 0) {
                                aDoubleRT = gbDepartmentStockReduceService.queryReduceProduceWeightTotal(disGoodsMap);
                                aDoubleRTV = gbDepartmentStockReduceService.queryReduceProduceTotal(disGoodsMap);
                            }
                            goodsRow.createCell(6).setCellValue(formatDecimal(aDoubleRT));
                            goodsRow.createCell(11).setCellValue(new BigDecimal(aDoubleRTV).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleS = 0.0;
                            Double aDoubleSV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.LOSS);
                            Integer integerLoss = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerLoss != null && integerLoss > 0) {
                                aDoubleS = gbDepartmentStockReduceService.queryReduceLossWeightTotal(disGoodsMap);
                                aDoubleSV = gbDepartmentStockReduceService.queryReduceLossTotal(disGoodsMap);
                            }
                            goodsRow.createCell(7).setCellValue(new BigDecimal(aDoubleS != null ? aDoubleS : 0.0).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(12).setCellValue(new BigDecimal(aDoubleSV != null ? aDoubleSV : 0.0).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleST = 0.0;
                            Double aDoubleSTV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.WASTE);
                            Integer integerWaste = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerWaste != null && integerWaste > 0) {
                                aDoubleST = gbDepartmentStockReduceService.queryReduceWasteWeightTotal(disGoodsMap);
                                aDoubleSTV = gbDepartmentStockReduceService.queryReduceWasteTotal(disGoodsMap);
                            }
                            goodsRow.createCell(8).setCellValue(new BigDecimal(aDoubleST != null ? aDoubleST : 0.0).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(13).setCellValue(new BigDecimal(aDoubleSTV != null ? aDoubleSTV : 0.0).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleRTW = 0.0;
                            Double aDoubleRTWV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.RETURN);
                            Integer integerReturn = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerReturn != null && integerReturn > 0) {
                                aDoubleRTW = gbDepartmentStockReduceService.queryReduceReturnWeightTotal(disGoodsMap);
                                aDoubleRTWV = gbDepartmentStockReduceService.queryReduceReturnTotal(disGoodsMap);
                            }
                            goodsRow.createCell(9).setCellValue(new BigDecimal(aDoubleRTW != null ? aDoubleRTW : 0.0).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(14).setCellValue(new BigDecimal(aDoubleRTWV != null ? aDoubleRTWV : 0.0).setScale(1, RoundingMode.HALF_UP).toString());

                            double aDoubleRV = costExcelNz(aDoubleRTV) + costExcelNz(aDoubleSV) + costExcelNz(aDoubleSTV);
                            double aDoubleR = costExcelNz(aDoubleRT) + costExcelNz(aDoubleS) + costExcelNz(aDoubleST);
                            goodsRow.createCell(5).setCellValue(formatDecimal(aDoubleR));
                            goodsRow.createCell(10).setCellValue(new BigDecimal(aDoubleRV).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleRRest = gbDepGoodsStockService.queryDepGoodsRestWeightTotal(disGoodsMap);
                            goodsRow.createCell(15).setCellValue(formatDecimal(aDoubleRRest));
                            Double aDoubleRRestV = gbDepGoodsStockService.queryDepGoodsRestTotal(disGoodsMap);
                            goodsRow.createCell(16).setCellValue(new BigDecimal(costExcelNz(aDoubleRRestV)).setScale(1, RoundingMode.HALF_UP).toString());

                        }
                    }
                }
            }
        }

        return wb;
    }

    // 新方法 - 创建单个统一的支出列表sheet
    private HSSFWorkbook sheetCreatCostSingle(HSSFWorkbook wb, Map<String, Object> map, GbReportEntity reportEntity) {
        System.out.println("creatCostSheetSingle - 创建单个支出列表sheet");
        List<GbDistributerFatherGoodsEntity> distributerFatherGoodsEntities = gbDepGoodsStockService.queryDepStockTreeFatherGoodsByParams(map);

        // 创建一个统一的支出列表sheet
        HSSFSheet sheet = wb.createSheet("支出列表");

        //设置表头
        HSSFRow row1 = sheet.createRow(0);
        row1.createCell(0).setCellValue("序号");
        row1.createCell(1).setCellValue("商品名称");
        row1.createCell(2).setCellValue("规格");
        row1.createCell(3).setCellValue("品牌");
        row1.createCell(4).setCellValue("详细");
        row1.createCell(5).setCellValue("总成本数量");
        row1.createCell(6).setCellValue("制作数量");
        row1.createCell(7).setCellValue("损耗数量");
        row1.createCell(8).setCellValue("废弃数量");
        row1.createCell(9).setCellValue("退货数量");
        row1.createCell(10).setCellValue("总成本");
        row1.createCell(11).setCellValue("销售成本");
        row1.createCell(12).setCellValue("损耗成本");
        row1.createCell(13).setCellValue("废弃成本");
        row1.createCell(14).setCellValue("退货成本");
        row1.createCell(15).setCellValue("库存数量");
        row1.createCell(16).setCellValue("库存成本");

        int rowIndex = 1; // 从第2行开始填充数据

        if (distributerFatherGoodsEntities != null && distributerFatherGoodsEntities.size() > 0) {
            for (GbDistributerFatherGoodsEntity greatGrandFather : distributerFatherGoodsEntities) {
                for (GbDistributerFatherGoodsEntity grand : greatGrandFather.getFatherGoodsEntities()) {
                    map.put("disGoodsGrandId", grand.getGbDistributerFatherGoodsId());
                    List<GbDistributerGoodsEntity> goodsEntities = gbDepGoodsStockService.queryDisGoodsStockByParams(map);

                    //设置表体
                    if (goodsEntities != null && goodsEntities.size() > 0) {
                        for (int i = 0; i < goodsEntities.size(); i++) {
                            GbDistributerGoodsEntity ckGoodsEntity = goodsEntities.get(i);
                            HSSFRow goodsRow = sheet.createRow(rowIndex++);
                            goodsRow.createCell(0).setCellValue(rowIndex - 1);
                            goodsRow.createCell(1).setCellValue(ckGoodsEntity.getGbDgGoodsName());
                            goodsRow.createCell(2).setCellValue(ckGoodsEntity.getGbDgGoodsStandardname());
                            goodsRow.createCell(3).setCellValue(ckGoodsEntity.getGbDgGoodsBrand());
                            goodsRow.createCell(4).setCellValue(ckGoodsEntity.getGbDgGoodsDetail());

                            //5 totalWeight
                            Map<String, Object> disGoodsMap = new HashMap<>();
                            disGoodsMap.put("startDate", reportEntity.getGbRepStartDate());
                            disGoodsMap.put("stopDate", reportEntity.getGbRepStopDate());
                            disGoodsMap.put("disGoodsId", ckGoodsEntity.getGbDistributerGoodsId());
                            for (String scopeKey : Arrays.asList("disId", "depId", "depFatherId", "dayuStatus", "depType")) {
                                Object scopeVal = map.get(scopeKey);
                                if (scopeVal != null) {
                                    disGoodsMap.put(scopeKey, scopeVal);
                                }
                            }

                            Double aDoubleRT = 0.0;
                            Double aDoubleRTV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.PRODUCTION);
                            Integer integerProduce = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerProduce != null && integerProduce > 0) {
                                aDoubleRT = gbDepartmentStockReduceService.queryReduceProduceWeightTotal(disGoodsMap);
                                aDoubleRTV = gbDepartmentStockReduceService.queryReduceProduceTotal(disGoodsMap);
                            }
                            goodsRow.createCell(6).setCellValue(formatDecimal(aDoubleRT));
                            goodsRow.createCell(11).setCellValue(new BigDecimal(aDoubleRTV).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleS = 0.0;
                            Double aDoubleSV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.LOSS);
                            Integer integerLoss = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerLoss != null && integerLoss > 0) {
                                aDoubleS = gbDepartmentStockReduceService.queryReduceLossWeightTotal(disGoodsMap);
                                aDoubleSV = gbDepartmentStockReduceService.queryReduceLossTotal(disGoodsMap);
                            }
                            goodsRow.createCell(7).setCellValue(new BigDecimal(costExcelNz(aDoubleS)).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(12).setCellValue(new BigDecimal(costExcelNz(aDoubleSV)).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleST = 0.0;
                            Double aDoubleSTV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.WASTE);
                            Integer integerWaste = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerWaste != null && integerWaste > 0) {
                                aDoubleST = gbDepartmentStockReduceService.queryReduceWasteWeightTotal(disGoodsMap);
                                aDoubleSTV = gbDepartmentStockReduceService.queryReduceWasteTotal(disGoodsMap);
                            }
                            goodsRow.createCell(8).setCellValue(new BigDecimal(costExcelNz(aDoubleST)).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(13).setCellValue(new BigDecimal(costExcelNz(aDoubleSTV)).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleRTW = 0.0;
                            Double aDoubleRTWV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.RETURN);
                            Integer integerReturn = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerReturn != null && integerReturn > 0) {
                                aDoubleRTW = gbDepartmentStockReduceService.queryReduceReturnWeightTotal(disGoodsMap);
                                aDoubleRTWV = gbDepartmentStockReduceService.queryReduceReturnTotal(disGoodsMap);
                            }
                            goodsRow.createCell(9).setCellValue(new BigDecimal(costExcelNz(aDoubleRTW)).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(14).setCellValue(new BigDecimal(costExcelNz(aDoubleRTWV)).setScale(1, RoundingMode.HALF_UP).toString());

                            double aDoubleRV = costExcelNz(aDoubleRTV) + costExcelNz(aDoubleSV) + costExcelNz(aDoubleSTV);
                            double aDoubleR = costExcelNz(aDoubleRT) + costExcelNz(aDoubleS) + costExcelNz(aDoubleST);
                            goodsRow.createCell(5).setCellValue(formatDecimal(aDoubleR));
                            goodsRow.createCell(10).setCellValue(new BigDecimal(aDoubleRV).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleRRest = gbDepGoodsStockService.queryDepGoodsRestWeightTotal(disGoodsMap);
                            goodsRow.createCell(15).setCellValue(formatDecimal(aDoubleRRest));
                            Double aDoubleRRestV = gbDepGoodsStockService.queryDepGoodsRestTotal(disGoodsMap);
                            goodsRow.createCell(16).setCellValue(new BigDecimal(costExcelNz(aDoubleRRestV)).setScale(1, RoundingMode.HALF_UP).toString());

                        }
                    }
                }
            }
        }

        return wb;
    }

    private HSSFWorkbook toCreatDisCostForm(GbReportEntity reportEntity) {
        HSSFWorkbook wb = new HSSFWorkbook();
        Map<String, Object> map = new HashMap<>();
        map.put("startDate", reportEntity.getGbRepStartDate());
        map.put("stopDate", reportEntity.getGbRepStopDate());
        map.put("disId", Integer.valueOf(reportEntity.getGbRepIds()));
        wb = sheetCreatCost(wb, map, reportEntity);
        System.out.println("toCreatDisCostFormtoCreatDisCostForm");
        return wb;
    }

    private HSSFWorkbook toCreatDepCostForm(GbReportEntity reportEntity) {
        System.out.println("cres");
        HSSFWorkbook wb = new HSSFWorkbook();
        GbDepartmentEntity departmentEntity = gbDepartmentService.queryDepInfoGb(Integer.valueOf(reportEntity.getGbRepIds()));
        Map<String, Object> map = new HashMap<>();
        map.put("startDate", reportEntity.getGbRepStartDate());
        map.put("stopDate", reportEntity.getGbRepStopDate());
        map.put("depFatherId", departmentEntity.getGbDepartmentId());
        wb = sheetCreatCost(wb, map, reportEntity);

        return wb;
    }

    private HSSFWorkbook toCreatSubDepCostForm(GbReportEntity reportEntity) {
        System.out.println("=== 开始生成子部门成本分析Excel ===");
        HSSFWorkbook wb = new HSSFWorkbook();

        Map<String, Object> map = new HashMap<>();
        map.put("startDate", reportEntity.getGbRepStartDate());
        map.put("stopDate", reportEntity.getGbRepStopDate());
        map.put("depId", Integer.valueOf(reportEntity.getGbRepIds()));
        wb = sheetCreatCost(wb, map, reportEntity);
        System.out.println("查询参数toCreatSubDepCostForm: " + map);
        return wb;
    }

    private HSSFWorkbook toCreatPurDepUserForm(GbReportEntity reportEntity) {
        System.out.println("=== 开始生成采购员统计Excel ===");
        HSSFWorkbook wb = new HSSFWorkbook();
        Map<String, Object> map = new HashMap<>();
        map.put("startDate", reportEntity.getGbRepStartDate());
        map.put("stopDate", reportEntity.getGbRepStopDate());
        map.put("purUserId", reportEntity.getGbRepIds());
        map.put("typeNotEqual", 9);
        map.put("dayuStatus", 2);
        // 与 bbbDisPurUser 中 queryDisTreeGoodsWithPurList 一致：仅自采（nx_supplier_id = -1）
        map.put("supplierBuy", -1);

        sheetCreatPurchase(wb, map, reportEntity);

        return wb;
    }

    private HSSFWorkbook toCreatDisStockNowForm(GbReportEntity reportEntity) {
        HSSFWorkbook wb = new HSSFWorkbook();
        Map<String, Object> map = new HashMap<>();
        map.put("startDate", reportEntity.getGbRepStartDate());
        map.put("stopDate", reportEntity.getGbRepStopDate());
        map.put("disId", Integer.valueOf(reportEntity.getGbRepIds()));
        map.put("restWeight", 0);

        wb = sheetCreatStock(wb, map, reportEntity);
        return wb;
    }

    private HSSFWorkbook toCreatSubDepStockNowForm(GbReportEntity reportEntity) {
        HSSFWorkbook wb = new HSSFWorkbook();
        GbDepartmentEntity departmentEntity = gbDepartmentService.queryDepInfoGb(Integer.valueOf(reportEntity.getGbRepIds()));
        Map<String, Object> map = new HashMap<>();
        map.put("startDate", reportEntity.getGbRepStartDate());
        map.put("stopDate", reportEntity.getGbRepStopDate());
        map.put("depId", departmentEntity.getGbDepartmentId());
        map.put("restWeight", 0);
        wb = sheetCreatStock(wb, map, reportEntity);

        return wb;
    }

    /**
     * 采购 Excel：{@code purSupplier}（按 nx 供货商 ID）与 {@code purDepUser}（按部门采购员 ID）共用。
     * 商品列表条件与行内汇总必须使用同一套 scope（supplierId / purUserId 不可混用）。
     */
    private HSSFWorkbook sheetCreatPurchase(HSSFWorkbook wb, Map<String, Object> map, GbReportEntity reportEntity) {
        String repType = reportEntity.getGbRepType();
        boolean bySupplier = "purSupplier".equals(repType);

        System.out.println("sheetCreatPurchase - 报表类型: " + repType + ", 查询参数: " + map);
        List<GbDistributerGoodsEntity> gbDistributerGoodsEntities = gbDistributerPurchaseGoodsService.queryDisTreeGoodsWithPurList(map);
        System.out.println("sheetCreatPurchase - 查询到的商品数量: " + (gbDistributerGoodsEntities != null ? gbDistributerGoodsEntities.size() : "null"));
        if (gbDistributerGoodsEntities != null && gbDistributerGoodsEntities.size() > 0) {
            System.out.println("sheetCreatPurchase - 开始处理 " + gbDistributerGoodsEntities.size() + " 个商品");

            String sheetName = bySupplier ? "供货商采购统计" : "采购员统计";
            if (sheetName.length() > 31) {
                sheetName = sheetName.substring(0, 31);
            }
            HSSFSheet sheet = wb.createSheet(sheetName);

            // 设置表头（供货商 / 采购员-自采 文案区分）
            HSSFRow headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("序号");
            headerRow.createCell(1).setCellValue("商品名称");
            headerRow.createCell(2).setCellValue("规格");
            headerRow.createCell(3).setCellValue("品牌");
            headerRow.createCell(4).setCellValue("详细");
            if (bySupplier) {
                headerRow.createCell(5).setCellValue("供货总额");
                headerRow.createCell(6).setCellValue("供货数量");
                headerRow.createCell(7).setCellValue("平均单价");
                headerRow.createCell(8).setCellValue("退货总额");
                headerRow.createCell(9).setCellValue("退货数量");
                headerRow.createCell(10).setCellValue("退货单价");
            } else {
                headerRow.createCell(5).setCellValue("自采总额");
                headerRow.createCell(6).setCellValue("自采数量");
                headerRow.createCell(7).setCellValue("平均单价");
                headerRow.createCell(8).setCellValue("自采退货总额");
                headerRow.createCell(9).setCellValue("自采退货数量");
                headerRow.createCell(10).setCellValue("自采退货单价");
            }

            int rowIndex = 1; // 从第2行开始填充数据

            for (GbDistributerGoodsEntity ckGoodsEntity : gbDistributerGoodsEntities) {
                System.out.println("sheetCreatPurchase - 处理商品: " + ckGoodsEntity.getGbDgGoodsName());
                //设置表体
                HSSFRow goodsRow = sheet.createRow(rowIndex);
                goodsRow.createCell(0).setCellValue(rowIndex);
                goodsRow.createCell(1).setCellValue(ckGoodsEntity.getGbDgGoodsName());
                goodsRow.createCell(2).setCellValue(ckGoodsEntity.getGbDgGoodsStandardname());
                goodsRow.createCell(3).setCellValue(ckGoodsEntity.getGbDgGoodsBrand());
                goodsRow.createCell(4).setCellValue(ckGoodsEntity.getGbDgGoodsDetail());
                goodsRow.createCell(5).setCellValue(0.0);
                goodsRow.createCell(6).setCellValue(0.0);
                goodsRow.createCell(7).setCellValue(0.0);
                goodsRow.createCell(8).setCellValue(0.0);
                goodsRow.createCell(9).setCellValue(0.0);
                goodsRow.createCell(10).setCellValue(0.0);

                Map<String, Object> disGoodsMap = new HashMap<>();
                disGoodsMap.put("startDate", reportEntity.getGbRepStartDate());
                disGoodsMap.put("stopDate", reportEntity.getGbRepStopDate());
                disGoodsMap.put("disGoodsId", ckGoodsEntity.getGbDistributerGoodsId());
                disGoodsMap.put("typeNotEqual", map.get("typeNotEqual"));
                Object dayuStatus = map.get("dayuStatus");
                disGoodsMap.put("dayuStatus", dayuStatus != null ? dayuStatus : 2);
                for (String scopeKey : Arrays.asList("disId", "purDepId")) {
                    Object v = map.get(scopeKey);
                    if (v != null) {
                        disGoodsMap.put(scopeKey, v);
                    }
                }
                if (bySupplier) {
                    Object supplierId = map.get("supplierId");
                    if (supplierId != null) {
                        disGoodsMap.put("supplierId", supplierId);
                    }
                } else {
                    Object purUserId = map.get("purUserId");
                    if (purUserId != null) {
                        disGoodsMap.put("purUserId", purUserId);
                    }
                    Object supplierBuy = map.get("supplierBuy");
                    if (supplierBuy != null) {
                        disGoodsMap.put("supplierBuy", supplierBuy);
                    }
                }
                Double aDoubleSupplier = 0.0;
                Double aDoubleSupplierTui = 0.0;
                Double aDoubleSupplieWeight = 0.0;
                Double aDoubleSupplieWeightTui = 0.0;
                Double aDoubleSuppliePerPrice = 0.0;
                Double aDoubleSuppliePerPriceTui = 0.0;

                // 与 queryGbPurchaseGoodsCount / queryDisTreeGoodsWithPurList 一致：按入库完成日 + join 批次；
                // queryPurchaseGoodsSubTotal 默认按订货日需显式使用 stock_finish_date。
                disGoodsMap.put("useStockFinishDate", Boolean.TRUE);

                // 正常采购行：排除退货类型（与其它采购统计口径一致）
                Map<String, Object> mapNormal = new HashMap<>(disGoodsMap);
                Integer cntNormal = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(mapNormal);
                if (cntNormal != null && cntNormal > 0) {
                    Double wN = gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightTotal(mapNormal);
                    Double avgN = gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightedAvgBuyPrice(mapNormal);
                    Double subN = gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(mapNormal);
                    aDoubleSupplieWeight = wN != null ? wN : 0.0;
                    aDoubleSupplier = subN != null ? subN : 0.0;
                    if (aDoubleSupplieWeight > 0) {
                        aDoubleSuppliePerPrice = aDoubleSupplier / aDoubleSupplieWeight;
                    } else if (avgN != null && avgN > 0) {
                        aDoubleSuppliePerPrice = avgN;
                    }
                }

                // 退货：仅 purchase_type = 9，不能再带 typeNotEqual（否则与 purchaseType 互斥）
                Map<String, Object> mapTui = new HashMap<>(disGoodsMap);
                mapTui.remove("typeNotEqual");
                mapTui.put("purchaseType", GbConstants.PurchaseOrderType.RETURN);
                Integer cntTui = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(mapTui);
                if (cntTui != null && cntTui > 0) {
                    Double wT = gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightTotal(mapTui);
                    Double subT = gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(mapTui);
                    aDoubleSupplieWeightTui = wT != null ? wT : 0.0;
                    aDoubleSupplierTui = subT != null ? subT : 0.0;
                    if (aDoubleSupplieWeightTui > 0) {
                        aDoubleSuppliePerPriceTui = Math.abs(aDoubleSupplierTui) / aDoubleSupplieWeightTui;
                    } else {
                        Double avgT = gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightedAvgBuyPrice(mapTui);
                        aDoubleSuppliePerPriceTui = avgT != null ? Math.abs(avgT) : 0.0;
                    }
                }

                System.out.println("sheetCreatPurchase - 行内统计(正常) mapNormal=" + mapNormal + " | 退货 mapTui=" + mapTui);

                goodsRow.createCell(5).setCellValue(new BigDecimal(aDoubleSupplier).setScale(1, RoundingMode.HALF_UP).toString());
                goodsRow.createCell(6).setCellValue(new BigDecimal(aDoubleSupplieWeight).setScale(1, RoundingMode.HALF_UP).toString());
                goodsRow.createCell(7).setCellValue(new BigDecimal(aDoubleSuppliePerPrice).setScale(1, RoundingMode.HALF_UP).toString());

                goodsRow.createCell(8).setCellValue(new BigDecimal(aDoubleSupplierTui).setScale(1, RoundingMode.HALF_UP).toString());
                goodsRow.createCell(9).setCellValue(new BigDecimal(aDoubleSupplieWeightTui).setScale(1, RoundingMode.HALF_UP).toString());
                goodsRow.createCell(10).setCellValue(new BigDecimal(aDoubleSuppliePerPriceTui).setScale(1, RoundingMode.HALF_UP).toString());

                rowIndex++; // 移动到下一行
            }
        } else {
            System.out.println("sheetCreatPurchase - 没有查询到商品数据");
        }

        System.out.println("sheetCreatPurchase - 完成，工作表数量: " + wb.getNumberOfSheets());
        return wb;

    }

    // 新方法 - 创建单个统一的采购列表sheet（用于业务表单）
    private HSSFWorkbook sheetCreatPurchaseSingle(HSSFWorkbook wb, Map<String, Object> map, GbReportEntity reportEntity) {
        // 调试信息
        List<GbDistributerGoodsEntity> gbDistributerGoodsEntities = gbDepGoodsStockService.queryDisGoodsStockByParams(map);
        if (gbDistributerGoodsEntities != null && gbDistributerGoodsEntities.size() > 0) {
            System.out.println("sheetCreatPurchaseneewnwnwnwwn - 开始处理 " + gbDistributerGoodsEntities.size() + " 个商品");

            // 创建主工作表
            HSSFSheet sheet = wb.createSheet("采购列表");

            // 设置表头
            HSSFRow headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("序号");
            headerRow.createCell(1).setCellValue("商品名称");
            headerRow.createCell(2).setCellValue("规格");
            headerRow.createCell(3).setCellValue("品牌");
            headerRow.createCell(4).setCellValue("详细");
            headerRow.createCell(5).setCellValue("采购总额");
            headerRow.createCell(6).setCellValue("采购数量");

            int rowIndex = 1; // 从第2行开始填充数据

            for (GbDistributerGoodsEntity ckGoodsEntity : gbDistributerGoodsEntities) {
                System.out.println("sheetCreaNNNNNNN - 处理商品: " + ckGoodsEntity.getGbDgGoodsName());
                //设置表体
                HSSFRow goodsRow = sheet.createRow(rowIndex);
                goodsRow.createCell(0).setCellValue(rowIndex);
                goodsRow.createCell(1).setCellValue(ckGoodsEntity.getGbDgGoodsName());
                goodsRow.createCell(2).setCellValue(ckGoodsEntity.getGbDgGoodsStandardname());
                goodsRow.createCell(3).setCellValue(ckGoodsEntity.getGbDgGoodsBrand());
                goodsRow.createCell(4).setCellValue(ckGoodsEntity.getGbDgGoodsDetail());
                goodsRow.createCell(5).setCellValue(0.0);
                goodsRow.createCell(6).setCellValue(0.0);

                //5 totalWeight
                Map<String, Object> disGoodsMap = new HashMap<>();
                disGoodsMap.put("startDate", reportEntity.getGbRepStartDate());
                disGoodsMap.put("stopDate", reportEntity.getGbRepStopDate());
                disGoodsMap.put("disGoodsId", ckGoodsEntity.getGbDistributerGoodsId());
                for (String scopeKey : Arrays.asList("disId", "depId", "depFatherId", "dayuStatus", "depType", "purUserId")) {
                    Object scopeVal = map.get(scopeKey);
                    if (scopeVal != null) {
                        disGoodsMap.put(scopeKey, scopeVal);
                    }
                }
                Double aDoubleTotal = 0.0;
                Double aDoubleWeight = 0.0;
                // 查询商品统计数据
                System.out.println("sheetCreatPurchaseSingletotalWeight - 查询商品统计参数: " + disGoodsMap);
                Integer integerProduce = gbDepGoodsStockService.queryGoodsStockCount(disGoodsMap);
                System.out.println("sheetCreatPurchaseSingletotalWeight - 商品统计数量: " + integerProduce);
                if (integerProduce != null && integerProduce > 0) {
                    aDoubleTotal = gbDepGoodsStockService.queryDepGoodsSubtotal(disGoodsMap);
                    aDoubleWeight = gbDepGoodsStockService.queryDepStockWeightTotal(disGoodsMap);
                }
                if (aDoubleTotal == null) {
                    aDoubleTotal = 0.0;
                }
                if (aDoubleWeight == null) {
                    aDoubleWeight = 0.0;
                }
                goodsRow.createCell(5).setCellValue(new BigDecimal(aDoubleTotal).setScale(1, RoundingMode.HALF_UP).toString());
                goodsRow.createCell(6).setCellValue(new BigDecimal(aDoubleWeight).setScale(1, RoundingMode.HALF_UP).toString());

                rowIndex++; // 移动到下一行
            }
        } else {
            System.out.println("sheetCreatPurchaseSingle - 没有查询到商品数据");
        }

        System.out.println("sheetCreatPurchaseSingle - 完成处理，工作表数量: " + wb.getNumberOfSheets());
        return wb;
    }

    // 新方法 - 创建单个统一的采购列表sheet（用于业务表单）
    private HSSFWorkbook sheetCreatPurchaseTuihuoSingle(HSSFWorkbook wb, Map<String, Object> map, GbReportEntity reportEntity) {
        // 调试信息
        System.out.println("dpeTut - 查询参数: " + map);
        map.put("equalType", 4);
//        List<GbDistributerGoodsEntity> gbDistributerGoodsEntities = gbDepartmentStockReduceService.queryGoodsStockRecordListByParams(map);
        List<GbDistributerGoodsEntity> gbDistributerGoodsEntities = new ArrayList<>();

        if (gbDistributerGoodsEntities != null && gbDistributerGoodsEntities.size() > 0) {
            System.out.println("sheetCreatPurchaseSingle - 开始处理 " + gbDistributerGoodsEntities.size() + " 个商品");

            // 创建主工作表
            HSSFSheet sheet = wb.createSheet("退货商品列表");

            // 设置表头
            HSSFRow headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("序号");
            headerRow.createCell(1).setCellValue("商品名称");
            headerRow.createCell(2).setCellValue("规格");
            headerRow.createCell(3).setCellValue("品牌");
            headerRow.createCell(4).setCellValue("详细");
            headerRow.createCell(5).setCellValue("退货总额");
            headerRow.createCell(6).setCellValue("退货数量");

            int rowIndex = 1; // 从第2行开始填充数据

            for (GbDistributerGoodsEntity ckGoodsEntity : gbDistributerGoodsEntities) {
                System.out.println("sheetCreatPurchaseSingle - 处理商品: " + ckGoodsEntity.getGbDgGoodsName());
                //设置表体
                HSSFRow goodsRow = sheet.createRow(rowIndex);
                goodsRow.createCell(0).setCellValue(rowIndex);
                goodsRow.createCell(1).setCellValue(ckGoodsEntity.getGbDgGoodsName());
                goodsRow.createCell(2).setCellValue(ckGoodsEntity.getGbDgGoodsStandardname());
                goodsRow.createCell(3).setCellValue(ckGoodsEntity.getGbDgGoodsBrand());
                goodsRow.createCell(4).setCellValue(ckGoodsEntity.getGbDgGoodsDetail());
                goodsRow.createCell(5).setCellValue(0.0);
                goodsRow.createCell(6).setCellValue(0.0);

                //5 totalWeight
                Map<String, Object> disGoodsMap = new HashMap<>();
                disGoodsMap.put("startDate", reportEntity.getGbRepStartDate());
                disGoodsMap.put("stopDate", reportEntity.getGbRepStopDate());
                disGoodsMap.put("disGoodsId", ckGoodsEntity.getGbDistributerGoodsId());
                disGoodsMap.put("equalType", 4);
                Double aDoubleTotal = 0.0;
                Double aDoubleWeight = 0.0;
                // 查询商品统计数据
                System.out.println("sheetCreatPurchaseSingle - 查询商品统计参数: " + disGoodsMap);
                Integer integerProduce = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                if (integerProduce != null && integerProduce > 0) {
                    System.out.println("sheetCreatPurchaseSingle - 商品统计数量退货Subtitoall: " + disGoodsMap);
//                    aDoubleTotal = gbDepartmentStockReduceService.queryReduceReturnTotal(disGoodsMap);
                    System.out.println("sheetCreatPurchaseSingle - 商品统计数量退货Weeight: " + disGoodsMap);
//                    aDoubleWeight = gbDepartmentStockReduceService.queryReduceReturnWeightTotal(disGoodsMap);
                }
                goodsRow.createCell(5).setCellValue(new BigDecimal(aDoubleTotal).setScale(1, RoundingMode.HALF_UP).toString());
                goodsRow.createCell(6).setCellValue(new BigDecimal(aDoubleWeight).setScale(1, RoundingMode.HALF_UP).toString());

                rowIndex++; // 移动到下一行
            }
        } else {
            System.out.println("sheetCreatPurchaseSingle - 没有查询到商品数据");
        }

        System.out.println("sheetCreatPurchaseSingle - 完成处理，工作表数量: " + wb.getNumberOfSheets());
        return wb;
    }


    private HSSFWorkbook sheetCreatStock(HSSFWorkbook wb, Map<String, Object> map, GbReportEntity reportEntity) {

        System.out.println("mapamapapapExcelleleel" + map);
        List<GbDistributerFatherGoodsEntity> distributerFatherGoodsEntities = gbDepGoodsStockService.queryDepStockTreeFatherGoodsByParams(map);

        if (distributerFatherGoodsEntities != null && distributerFatherGoodsEntities.size() > 0) {
            for (GbDistributerFatherGoodsEntity greatGrandFather : distributerFatherGoodsEntities) {
                for (GbDistributerFatherGoodsEntity grand : greatGrandFather.getFatherGoodsEntities()) {
                    String sheetName = grand.getGbDfgFatherGoodsName() != null ? grand.getGbDfgFatherGoodsName() : "未命名工作表";
                    if (sheetName.length() > 31) {
                        sheetName = sheetName.substring(0, 31);
                    }
                    HSSFSheet sheet = wb.createSheet(sheetName);
                    //设置表头
                    HSSFRow row1 = sheet.createRow(0);
                    row1.createCell(0).setCellValue("序号");
                    row1.createCell(1).setCellValue("商品名称");
                    row1.createCell(2).setCellValue("规格");
                    row1.createCell(3).setCellValue("品牌");
                    row1.createCell(4).setCellValue("详细");
                    row1.createCell(5).setCellValue("库存总量");
                    row1.createCell(6).setCellValue("库存总金额");

                    map.put("disGoodsGrandId", grand.getGbDistributerFatherGoodsId());
                    List<GbDistributerGoodsEntity> goodsEntities = gbDepGoodsStockService.queryDisGoodsStockByParams(map);
                    //设置表体
                    HSSFRow goodsRow = null;
                    if (goodsEntities != null && goodsEntities.size() > 0) {
                        for (int i = 0; i < goodsEntities.size(); i++) {
                            GbDistributerGoodsEntity ckGoodsEntity = goodsEntities.get(i);
                            goodsRow = sheet.createRow(sheet.getLastRowNum() + 1);
                            goodsRow.createCell(0).setCellValue(sheet.getLastRowNum());
                            goodsRow.createCell(1).setCellValue(ckGoodsEntity.getGbDgGoodsName());
                            goodsRow.createCell(2).setCellValue(ckGoodsEntity.getGbDgGoodsStandardname());
                            goodsRow.createCell(3).setCellValue(ckGoodsEntity.getGbDgGoodsBrand());
                            goodsRow.createCell(4).setCellValue(ckGoodsEntity.getGbDgGoodsDetail());
                            GbDepartmentEntity departmentEntity = gbDepartmentService.queryDepInfoGb(Integer.valueOf(reportEntity.getGbRepIds()));

                            //5 totalWeight
                            Map<String, Object> disGoodsMap = new HashMap<>();
                            disGoodsMap.put("disGoodsId", ckGoodsEntity.getGbDistributerGoodsId());
                            System.out.println("zenmmeiyouoelele" + disGoodsMap);
                            Double aDoubleR = gbDepGoodsStockService.queryDepGoodsRestWeightTotal(disGoodsMap);
                            goodsRow.createCell(5).setCellValue(formatDecimal(aDoubleR));
                            Double aDoubleRT = gbDepGoodsStockService.queryDepGoodsRestTotal(disGoodsMap);
                            goodsRow.createCell(6).setCellValue(formatDecimal(aDoubleRT));

                        }
                    }
                }

            }
        }

        return wb;
    }

    // 新方法 - 创建单个统一的库存列表sheet（用于业务表单）
    private HSSFWorkbook sheetCreatStockSingle(HSSFWorkbook wb, Map<String, Object> map, GbReportEntity reportEntity) {

        System.out.println("sheetCreatStockSingle - 创建单个库存列表sheet: " + map);
        map.put("restWeight", 0);
        List<GbDistributerGoodsEntity> goodsEntities = gbDepGoodsStockService.queryDisGoodsStockByParams(map);

        // 创建一个统一的库存列表sheet
        HSSFSheet sheet = wb.createSheet("库存列表");

        //设置表头
        HSSFRow row1 = sheet.createRow(0);
        row1.createCell(0).setCellValue("序号");
        row1.createCell(1).setCellValue("商品名称");
        row1.createCell(2).setCellValue("规格");
        row1.createCell(3).setCellValue("品牌");
        row1.createCell(4).setCellValue("详细");
        row1.createCell(5).setCellValue("库存总量");
        row1.createCell(6).setCellValue("库存总金额");

        int rowIndex = 1; // 从第2行开始填充数据

        //设置表体
        if (goodsEntities != null && goodsEntities.size() > 0) {
            for (int i = 0; i < goodsEntities.size(); i++) {
                GbDistributerGoodsEntity ckGoodsEntity = goodsEntities.get(i);
                HSSFRow goodsRow = sheet.createRow(rowIndex++);
                goodsRow.createCell(0).setCellValue(rowIndex - 1);
                goodsRow.createCell(1).setCellValue(ckGoodsEntity.getGbDgGoodsName());
                goodsRow.createCell(2).setCellValue(ckGoodsEntity.getGbDgGoodsStandardname());
                goodsRow.createCell(3).setCellValue(ckGoodsEntity.getGbDgGoodsBrand());
                goodsRow.createCell(4).setCellValue(ckGoodsEntity.getGbDgGoodsDetail());

                //5 totalWeight
                Map<String, Object> disGoodsMap = new HashMap<>();
                disGoodsMap.put("startDate", reportEntity.getGbRepStartDate());
                disGoodsMap.put("stopDate", reportEntity.getGbRepStopDate());
                disGoodsMap.put("disGoodsId", ckGoodsEntity.getGbDistributerGoodsId());
                disGoodsMap.put("restWeight", 0);
                Double aDoubleR = gbDepGoodsStockService.queryDepGoodsRestWeightTotal(disGoodsMap);
                goodsRow.createCell(5).setCellValue(formatDecimal(aDoubleR));
                Double aDoubleRT = gbDepGoodsStockService.queryDepGoodsRestTotal(disGoodsMap);
                goodsRow.createCell(6).setCellValue(formatDecimal(aDoubleRT));

            }
        }
        return wb;
    }


}




