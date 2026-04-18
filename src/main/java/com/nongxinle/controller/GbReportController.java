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
        Map<String, Object> result = new java.util.HashMap<>();
        // TODO: 实现完整的门店营业统计逻辑
        result.put("disId", map.get("disId"));
        result.put("startDate", map.get("startDate"));
        result.put("stopDate", map.get("stopDate"));
        return result;
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
                System.out.println("生成类型: disCost");
                wb = toCreatDisBusinessForm(reportEntity);
            }
            if (reportEntity.getGbRepType().equals("subDepBusiness")) {
                System.out.println("生成类型: disCost");
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

                            Double aDoubleRT = 0.0;
                            Double aDoubleRTV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.PRODUCTION);
                            Integer integerProduce = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerProduce != null && integerProduce > 0) {
//                                aDoubleRT = gbDepartmentStockReduceService.queryReduceProduceWeightTotal(disGoodsMap);
//                                aDoubleRTV = gbDepartmentStockReduceService.queryReduceProduceTotal(disGoodsMap);
                            }
                            goodsRow.createCell(6).setCellValue(formatDecimal(aDoubleRT));
                            goodsRow.createCell(11).setCellValue(new BigDecimal(aDoubleRTV).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleS = 0.0;
                            Double aDoubleSV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.LOSS);
                            Integer integerLoss = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerLoss > 0) {
//                                aDoubleS = gbDepartmentStockReduceService.queryReduceLossWeightTotal(disGoodsMap);
//                                aDoubleSV = gbDepartmentStockReduceService.queryReduceLossTotal(disGoodsMap);
                            }
                            goodsRow.createCell(7).setCellValue(new BigDecimal(aDoubleS).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(12).setCellValue(new BigDecimal(aDoubleSV).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleST = 0.0;
                            Double aDoubleSTV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.WASTE);
                            Integer integerWaste = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerWaste > 0) {
//                                aDoubleST = gbDepartmentStockReduceService.queryReduceWasteWeightTotal(disGoodsMap);
//                                aDoubleSTV = gbDepartmentStockReduceService.queryReduceWasteTotal(disGoodsMap);
                            }
                            goodsRow.createCell(8).setCellValue(new BigDecimal(aDoubleST).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(13).setCellValue(new BigDecimal(aDoubleSTV).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleRTW = 0.0;
                            Double aDoubleRTWV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.RETURN);
                            Integer integerReturn = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerReturn > 0) {
//                                aDoubleRTW = gbDepartmentStockReduceService.queryReduceReturnWeightTotal(disGoodsMap);
//                                aDoubleRTWV = gbDepartmentStockReduceService.queryReduceReturnTotal(disGoodsMap);
                            }
                            goodsRow.createCell(9).setCellValue(new BigDecimal(aDoubleRTW).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(14).setCellValue(new BigDecimal(aDoubleRTWV).setScale(1, RoundingMode.HALF_UP).toString());

                            double aDoubleRV = aDoubleRTV + aDoubleSV + aDoubleSTV;
                            double aDoubleR = aDoubleRT + aDoubleS + aDoubleST;
                            goodsRow.createCell(5).setCellValue(formatDecimal(aDoubleR));
                            goodsRow.createCell(10).setCellValue(new BigDecimal(aDoubleRV).setScale(1, RoundingMode.HALF_UP).toString());

//                            Double aDoubleRRest = gbDepGoodsStockService.queryDepStockRestWeightTotal(disGoodsMap);
//                            goodsRow.createCell(15).setCellValue(new BigDecimal(aDoubleRRest).setScale(1, RoundingMode.HALF_UP).toString());
                            Double aDoubleRRestV = gbDepGoodsStockService.queryDepGoodsRestTotal(disGoodsMap);
                            goodsRow.createCell(16).setCellValue(new BigDecimal(aDoubleRRestV).setScale(1, RoundingMode.HALF_UP).toString());

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

                            Double aDoubleRT = 0.0;
                            Double aDoubleRTV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.PRODUCTION);
                            Integer integerProduce = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerProduce != null && integerProduce > 0) {
//                                aDoubleRT = gbDepartmentStockReduceService.queryReduceProduceWeightTotal(disGoodsMap);
//                                aDoubleRTV = gbDepartmentStockReduceService.queryReduceProduceTotal(disGoodsMap);
                            }
                            goodsRow.createCell(6).setCellValue(formatDecimal(aDoubleRT));
                            goodsRow.createCell(11).setCellValue(new BigDecimal(aDoubleRTV).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleS = 0.0;
                            Double aDoubleSV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.LOSS);
                            Integer integerLoss = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerLoss > 0) {
//                                aDoubleS = gbDepartmentStockReduceService.queryReduceLossWeightTotal(disGoodsMap);
//                                aDoubleSV = gbDepartmentStockReduceService.queryReduceLossTotal(disGoodsMap);
                            }
                            goodsRow.createCell(7).setCellValue(new BigDecimal(aDoubleS).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(12).setCellValue(new BigDecimal(aDoubleSV).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleST = 0.0;
                            Double aDoubleSTV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.WASTE);
                            Integer integerWaste = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerWaste > 0) {
//                                aDoubleST = gbDepartmentStockReduceService.queryReduceWasteWeightTotal(disGoodsMap);
//                                aDoubleSTV = gbDepartmentStockReduceService.queryReduceWasteTotal(disGoodsMap);
                            }
                            goodsRow.createCell(8).setCellValue(new BigDecimal(aDoubleST).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(13).setCellValue(new BigDecimal(aDoubleSTV).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleRTW = 0.0;
                            Double aDoubleRTWV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.RETURN);
                            Integer integerReturn = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerReturn > 0) {
//                                aDoubleRTW = gbDepartmentStockReduceService.queryReduceReturnWeightTotal(disGoodsMap);
//                                aDoubleRTWV = gbDepartmentStockReduceService.queryReduceReturnTotal(disGoodsMap);
                            }
                            goodsRow.createCell(9).setCellValue(new BigDecimal(aDoubleRTW).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(14).setCellValue(new BigDecimal(aDoubleRTWV).setScale(1, RoundingMode.HALF_UP).toString());

                            double aDoubleRV = aDoubleRTV + aDoubleSV + aDoubleSTV;
                            double aDoubleR = aDoubleRT + aDoubleS + aDoubleST;
                            goodsRow.createCell(5).setCellValue(formatDecimal(aDoubleR));
                            goodsRow.createCell(10).setCellValue(new BigDecimal(aDoubleRV).setScale(1, RoundingMode.HALF_UP).toString());

//                            Double aDoubleRRest = gbDepGoodsStockService.queryDepStockRestWeightTotal(disGoodsMap);
//                            goodsRow.createCell(15).setCellValue(new BigDecimal(aDoubleRRest).setScale(1, RoundingMode.HALF_UP).toString());
                            Double aDoubleRRestV = gbDepGoodsStockService.queryDepGoodsRestTotal(disGoodsMap);
                            goodsRow.createCell(16).setCellValue(new BigDecimal(aDoubleRRestV).setScale(1, RoundingMode.HALF_UP).toString());

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

                            Double aDoubleRT = 0.0;
                            Double aDoubleRTV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.PRODUCTION);
                            Integer integerProduce = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerProduce != null && integerProduce > 0) {
//                                aDoubleRT = gbDepartmentStockReduceService.queryReduceProduceWeightTotal(disGoodsMap);
//                                aDoubleRTV = gbDepartmentStockReduceService.queryReduceProduceTotal(disGoodsMap);
                            }
                            goodsRow.createCell(6).setCellValue(formatDecimal(aDoubleRT));
                            goodsRow.createCell(11).setCellValue(new BigDecimal(aDoubleRTV).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleS = 0.0;
                            Double aDoubleSV = 0.0;
                            disGoodsMap.put("equalType",GbConstants.StockReduceType.LOSS);
                            Integer integerLoss = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerLoss > 0) {
//                                aDoubleS = gbDepartmentStockReduceService.queryReduceLossWeightTotal(disGoodsMap);
//                                aDoubleSV = gbDepartmentStockReduceService.queryReduceLossTotal(disGoodsMap);
                            }
                            goodsRow.createCell(7).setCellValue(new BigDecimal(aDoubleS).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(12).setCellValue(new BigDecimal(aDoubleSV).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleST = 0.0;
                            Double aDoubleSTV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.WASTE);
                            Integer integerWaste = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerWaste > 0) {
//                                aDoubleST = gbDepartmentStockReduceService.queryReduceWasteWeightTotal(disGoodsMap);
//                                aDoubleSTV = gbDepartmentStockReduceService.queryReduceWasteTotal(disGoodsMap);
                            }
                            goodsRow.createCell(8).setCellValue(new BigDecimal(aDoubleST).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(13).setCellValue(new BigDecimal(aDoubleSTV).setScale(1, RoundingMode.HALF_UP).toString());

                            Double aDoubleRTW = 0.0;
                            Double aDoubleRTWV = 0.0;
                            disGoodsMap.put("equalType", GbConstants.StockReduceType.RETURN);
                            Integer integerReturn = gbDepartmentStockReduceService.queryReduceTypeCount(disGoodsMap);
                            if (integerReturn > 0) {
//                                aDoubleRTW = gbDepartmentStockReduceService.queryReduceReturnWeightTotal(disGoodsMap);
//                                aDoubleRTWV = gbDepartmentStockReduceService.queryReduceReturnTotal(disGoodsMap);
                            }
                            goodsRow.createCell(9).setCellValue(new BigDecimal(aDoubleRTW).setScale(1, RoundingMode.HALF_UP).toString());
                            goodsRow.createCell(14).setCellValue(new BigDecimal(aDoubleRTWV).setScale(1, RoundingMode.HALF_UP).toString());

                            double aDoubleRV = aDoubleRTV + aDoubleSV + aDoubleSTV;
                            double aDoubleR = aDoubleRT + aDoubleS + aDoubleST;
                            goodsRow.createCell(5).setCellValue(formatDecimal(aDoubleR));
                            goodsRow.createCell(10).setCellValue(new BigDecimal(aDoubleRV).setScale(1, RoundingMode.HALF_UP).toString());

//                            Double aDoubleRRest = gbDepGoodsStockService.queryDepStockRestWeightTotal(disGoodsMap);
//                            goodsRow.createCell(15).setCellValue(new BigDecimal(aDoubleRRest).setScale(1, RoundingMode.HALF_UP).toString());
                            Double aDoubleRRestV = gbDepGoodsStockService.queryDepGoodsRestTotal(disGoodsMap);
                            goodsRow.createCell(16).setCellValue(new BigDecimal(aDoubleRRestV).setScale(1, RoundingMode.HALF_UP).toString());

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

    private HSSFWorkbook sheetCreatPurchase(HSSFWorkbook wb, Map<String, Object> map, GbReportEntity reportEntity) {
        // 调试信息
        System.out.println("toCreatDepuseerrPurGoodsForm - 查询参数: " + map);
        List<GbDistributerGoodsEntity> gbDistributerGoodsEntities = gbDistributerPurchaseGoodsService.queryDisTreeGoodsWithPurList(map);
//        List<GbDistributerGoodsEntity> gbDistributerGoodsEntities = gbDistributerPurchaseGoodsService.queryDisTreeGoods(map);
        System.out.println("toCreatSupplierPurGoodsForm - 查询到的商品数量: " + (gbDistributerGoodsEntities != null ? gbDistributerGoodsEntities.size() : "null"));

        if (gbDistributerGoodsEntities != null && gbDistributerGoodsEntities.size() > 0) {
            System.out.println("toCreatSupplierPurGoodsForm - 开始处理 " + gbDistributerGoodsEntities.size() + " 个商品");

            // 创建主工作表
            HSSFSheet sheet = wb.createSheet("采购员统计");

            // 设置表头
            HSSFRow headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("序号");
            headerRow.createCell(1).setCellValue("商品名称");
            headerRow.createCell(2).setCellValue("规格");
            headerRow.createCell(3).setCellValue("品牌");
            headerRow.createCell(4).setCellValue("详细");
            headerRow.createCell(5).setCellValue("供货商总额");
            headerRow.createCell(6).setCellValue("供货商数量");
            headerRow.createCell(7).setCellValue("供货商单价");
            headerRow.createCell(8).setCellValue("供货商退货总额");
            headerRow.createCell(9).setCellValue("供货商退货数量");
            headerRow.createCell(10).setCellValue("供货商退货单价");

            int rowIndex = 1; // 从第2行开始填充数据

            for (GbDistributerGoodsEntity ckGoodsEntity : gbDistributerGoodsEntities) {
                System.out.println("toCreatSupplierPurGoodsForm - 处理商品: " + ckGoodsEntity.getGbDgGoodsName());
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

                //5 totalWeight
                Map<String, Object> disGoodsMap = new HashMap<>();
                disGoodsMap.put("startDate", reportEntity.getGbRepStartDate());
                disGoodsMap.put("stopDate", reportEntity.getGbRepStopDate());
                disGoodsMap.put("disGoodsId", ckGoodsEntity.getGbDistributerGoodsId());
                disGoodsMap.put("dayuStatus", 1);
                disGoodsMap.put("typeNotEqual", 9);
                disGoodsMap.put("purUserId", reportEntity.getGbRepIds());
                Double aDoubleTotal = 0.0;
                Double aDoubleWeight = 0.0;
                Double aDoublePerPrice = 0.0;
                Double aDoubleSupplier = 0.0;
                Double aDoubleSupplierTui = 0.0;
                Double aDoubleSupplieWeight = 0.0;
                Double aDoubleSupplieWeightTui = 0.0;
                Double aDoubleSuppliePerPrice = 0.0;
                Double aDoubleSuppliePerPriceTui = 0.0;
                // 查询商品统计数据
                System.out.println("toCreatSupplierPurGoodsForm - 查询商品统计参数: " + disGoodsMap);
                Integer integerProduce = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(disGoodsMap);
                System.out.println("toCreatSupplierPurGoodsForm - 商品统计数量: " + integerProduce);
                if (integerProduce != null && integerProduce > 0) {
                    Double totalResult = gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(disGoodsMap);
//                    Double weightResult = gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightTotal(disGoodsMap);
                    aDoubleTotal = totalResult != null ? totalResult : 0.0;
//                    aDoubleWeight = weightResult != null ? weightResult : 0.0;
                    aDoublePerPrice = aDoubleWeight != 0 ? aDoubleTotal / aDoubleWeight : 0.0;
                    System.out.println("toCreatSupplierPurGoodsForm - 商品统计结果 - 总额:" + aDoubleTotal + ", 重量:" + aDoubleWeight + ", 单价:" + aDoublePerPrice);
                    Integer integer2 = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(disGoodsMap);
                    if (integer2 != null && integer2 > 0) {
                        Double supplierResult = gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(disGoodsMap);
//                        Double supplierWeightResult = gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightTotal(disGoodsMap);
                        aDoubleSupplier = supplierResult != null ? supplierResult : 0.0;
//                        aDoubleSupplieWeight = supplierWeightResult != null ? supplierWeightResult : 0.0;
                        aDoubleSuppliePerPrice = aDoubleSupplieWeight != 0 ? aDoubleSupplier / aDoubleSupplieWeight : 0.0;
                        System.out.println("toCreatSupplierPurGoodsForm - 供货商统计结果 - 总额:" + aDoubleSupplier + ", 重量:" + aDoubleSupplieWeight + ", 单价:" + aDoubleSuppliePerPrice);
                    }
                    // 调试信息已移除
                    Integer integerSupTui = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(disGoodsMap);
                    if (integerSupTui != null && integerSupTui > 0) {
                        Double supplierTuiResult = gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(disGoodsMap);
                        aDoubleSupplierTui = supplierTuiResult != null ? supplierTuiResult : 0.0;
                        double absoluteValue = Math.abs(aDoubleSupplierTui); // 取绝对值
//                        Double supplierWeightTuiResult = gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightTotal(disGoodsMap);
//                        aDoubleSupplieWeightTui = supplierWeightTuiResult != null ? supplierWeightTuiResult : 0.0;
                        aDoubleSuppliePerPriceTui = aDoubleSupplieWeightTui != 0 ? absoluteValue / aDoubleSupplieWeightTui : 0.0;
                        System.out.println("toCreatSupplierPurGoodsForm - 供货商退货统计结果 - 总额:" + aDoubleSupplierTui + ", 重量:" + aDoubleSupplieWeightTui + ", 单价:" + aDoubleSuppliePerPriceTui);
                    }

                }

                goodsRow.createCell(5).setCellValue(new BigDecimal(aDoubleSupplier).setScale(1, RoundingMode.HALF_UP).toString());
                goodsRow.createCell(6).setCellValue(new BigDecimal(aDoubleSupplieWeight).setScale(1, RoundingMode.HALF_UP).toString());
                goodsRow.createCell(7).setCellValue(new BigDecimal(aDoubleSuppliePerPrice).setScale(1, RoundingMode.HALF_UP).toString());

                goodsRow.createCell(8).setCellValue(new BigDecimal(aDoubleSupplierTui).setScale(1, RoundingMode.HALF_UP).toString());
                goodsRow.createCell(9).setCellValue(new BigDecimal(aDoubleSupplieWeightTui).setScale(1, RoundingMode.HALF_UP).toString());
                goodsRow.createCell(10).setCellValue(new BigDecimal(aDoubleSuppliePerPriceTui).setScale(1, RoundingMode.HALF_UP).toString());

                rowIndex++; // 移动到下一行
            }
        } else {
            System.out.println("toCreatSupplierPurGoodsForm - 没有查询到商品数据");
        }

        System.out.println("toCreatSupplierPurGoodsForm - 完成处理，工作表数量: " + wb.getNumberOfSheets());
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
                Double aDoubleTotal = 0.0;
                Double aDoubleWeight = 0.0;
                // 查询商品统计数据
                System.out.println("sheetCreatPurchaseSingletotalWeight - 查询商品统计参数: " + disGoodsMap);
                Integer integerProduce = gbDepGoodsStockService.queryGoodsStockCount(disGoodsMap);
                System.out.println("sheetCreatPurchaseSingletotalWeight - 商品统计数量: " + integerProduce);
                if (integerProduce != null && integerProduce > 0) {
//                    aDoubleTotal = gbDepGoodsStockService.queryDepGoodsSubtotal(disGoodsMap);
//                    aDoubleWeight = gbDepGoodsStockService.queryDepStockWeightTotal(disGoodsMap);
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
                            disGoodsMap.put("startDate", reportEntity.getGbRepStartDate());
                            disGoodsMap.put("stopDate", reportEntity.getGbRepStopDate());
                            disGoodsMap.put("disGoodsId", ckGoodsEntity.getGbDistributerGoodsId());
                            disGoodsMap.put("depFatherId", departmentEntity.getGbDepartmentId());
                            disGoodsMap.put("restWeight", 0);
//                            Double aDoubleR = gbDepGoodsStockService.queryDepStockRestWeightTotal(disGoodsMap);
//                            goodsRow.createCell(5).setCellValue(formatDecimal(aDoubleR));
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
//                Double aDoubleR = gbDepGoodsStockService.queryDepStockRestWeightTotal(disGoodsMap);
//                goodsRow.createCell(5).setCellValue(formatDecimal(aDoubleR));
                Double aDoubleRT = gbDepGoodsStockService.queryDepGoodsRestTotal(disGoodsMap);
                goodsRow.createCell(6).setCellValue(formatDecimal(aDoubleRT));

            }
        }
        return wb;
    }


}




