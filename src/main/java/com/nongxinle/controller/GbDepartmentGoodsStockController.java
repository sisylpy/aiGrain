package com.nongxinle.controller;

import com.nongxinle.entity.*;
import com.nongxinle.service.*;
import com.nongxinle.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Calendar;

/**
 * 部门商品库存Controller
 */
@RestController
@RequestMapping("gbdepartmentgoodsstock")
public class GbDepartmentGoodsStockController {

    @Autowired
    private GbDepartmentGoodsStockService gbDepGoodsStockService;
    @Autowired
    private GbDepartmentGoodsDailyService gbDepGoodsDailyService;
    @Autowired
    private GbDepartmentDisGoodsService gbDepartmentDisGoodsService;
    @Autowired
    private GbDistributerGoodsService disGoodsService;
    @Autowired
    private GbDepartmentGoodsStockReduceService gbDepartmentStockReduceService;
    @Autowired
    private GbDistributerPurchaseGoodsService gbDistributerPurchaseGoodsService;
    @Autowired
    private GbDepartmentService gbDepartmentService;

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
        
        System.out.println("=== 开始查询门店库存按时间段分类统计 ===");
        System.out.println("请求参数 - disId: " + disId + ", whichDay: " + whichDay + ", searchDepIds: " + searchDepIds + ", searchDepId: " + searchDepId + ", type: " + type);

        Map<String, Object> mapResult = new HashMap<>();
        List<String> idsGb = parseDepIds(searchDepIds);
        System.out.println("解析后的部门ID列表: " + idsGb);
        
        // 构建基础查询参数
        Map<String, Object> baseParams = buildBaseParams(disId, searchDepId, searchDepIds, idsGb);
        System.out.println("基础查询参数: " + baseParams);
        
        // 查询总库存
        Integer totalCount = gbDepGoodsStockService.queryGoodsStockCount(baseParams);
        System.out.println("总库存记录数: " + totalCount);
        
        if (totalCount > 0) {
            System.out.println("查询库存总额的字段" + baseParams);
            Double totalAmount = gbDepGoodsStockService.queryDepGoodsRestTotal(baseParams);
            System.out.println("总库存金额: " + totalAmount);
            
            Map<String, Object> totalData = new HashMap<>();
            
            // 根据查询类型设置总数据的说明
            String totalDateString = getTotalDateString(type);
            totalData.put("dateString", totalDateString);
            totalData.put("restTotal", formatDecimal(totalAmount));
            mapResult.put("total", totalData);

            
            // 构建各期间数据
            System.out.println("开始构建各期间数据，查询类型: " + type);
            mapResult.putAll(buildPeriodTotals(disId, searchDepId, searchDepIds, idsGb, type));
            System.out.println("各期间数据构建完成");
        } else {
            System.out.println("无库存记录，返回空数据");
            Map<String, Object> emptyTotal = new HashMap<>();
            emptyTotal.put("dateString", getTotalDateString(type));
            emptyTotal.put("restTotal", "0");
            mapResult.put("total", emptyTotal);
        }
        
        // 获取商品数据
        System.out.println("开始获取商品数据，whichDay: " + whichDay);
        List<GbDistributerFatherGoodsEntity> goods = getGoodsByPeriod(disId, whichDay, baseParams, mapResult, type);
        System.out.println("获取到商品数量: " + (goods != null ? goods.size() : 0));
        mapResult.put("arr", goods);
        
        System.out.println("=== 查询完成，返回结果 ===");
        return R.ok().put("data", mapResult);
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析部门ID字符串为列表
     */
    private List<String> parseDepIds(String searchDepIds) {
        if (searchDepIds == null || "-1".equals(searchDepIds)) {
            return new ArrayList<>();
        }
        return Arrays.asList(searchDepIds.split(","));
    }

    /**
     * 构建基础查询参数
     */
    private Map<String, Object> buildBaseParams(Integer disId, String searchDepId, String searchDepIds, List<String> idsGb) {
        Map<String, Object> params = new HashMap<>();
        params.put("disId", disId);
        params.put("dayuStatus", -1);
        params.put("restWeight", 0);
        
        if (searchDepId != null && !"-1".equals(searchDepId)) {
            params.put("depFatherId", searchDepId);
        } else if (searchDepIds != null && !"-1".equals(searchDepIds)) {
            params.put("depFatherIds", idsGb);
        }
        
        return params;
    }

    /**
     * 获取总数据的汉字说明
     */
    private String getTotalDateString(Integer type) {
        if (type == null) {
            type = 0; // 默认为按天查询
        }
        switch (type) {
            case 0: return "全部";
            case 1: return "全部（按周）";
            case 2: return "全部（按月）";
            default: return "全部";
        }
    }

    /**
     * 格式化金额为字符串
     */
    private String formatDecimal(Double value) {
        return new BigDecimal(value).setScale(1, BigDecimal.ROUND_HALF_UP).toString();
    }

    /**
     * 根据偏移量获取日期（按天）
     */
    private String formatWhatDay(int what) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, what);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(cal.getTime());
    }

    /**
     * 根据偏移量和查询类型获取日期范围
     */
    private String[] getDateRange(int offset, Integer type) {
        switch (type) {
            case 0: // 按天
                String date = formatWhatDay(-offset);
                return new String[]{date, date};
            case 1: // 按周（7天周期）
                if (offset == -4) {
                    return new String[]{null, getWeekStartDate(-3)};
                }
                return new String[]{getWeekStartDate(offset), getWeekStopDate(offset)};
            case 2: // 按月（30天周期）
                if (offset == -4) {
                    return new String[]{null, getMonthStartDate(-3)};
                }
                return new String[]{getMonthStartDate(offset), getMonthStopDate(offset)};
            default:
                return new String[]{null, null};
        }
    }

    /**
     * 按周查询：7天为一个周期
     */
    private String getWeekStartDate(int weekOffset) {
        Calendar cal = Calendar.getInstance();
        int daysToSubtract = Math.abs(weekOffset) * 7 + 6;
        cal.add(Calendar.DAY_OF_YEAR, -daysToSubtract);
        return new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
    }

    private String getWeekStopDate(int weekOffset) {
        Calendar cal = Calendar.getInstance();
        int daysToSubtract = Math.abs(weekOffset) * 7;
        cal.add(Calendar.DAY_OF_YEAR, -daysToSubtract);
        return new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
    }

    /**
     * 按月查询：30天为一个周期
     */
    private String getMonthStartDate(int monthOffset) {
        Calendar cal = Calendar.getInstance();
        int daysToSubtract = Math.abs(monthOffset) * 30 + 29;
        cal.add(Calendar.DAY_OF_YEAR, -daysToSubtract);
        return new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
    }

    private String getMonthStopDate(int monthOffset) {
        Calendar cal = Calendar.getInstance();
        int daysToSubtract = Math.abs(monthOffset) * 30;
        cal.add(Calendar.DAY_OF_YEAR, -daysToSubtract);
        return new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
    }

    /**
     * 根据期间配置
     */
    private static class PeriodConfig {
        private final String[] periodKeys;
        private final int[] offsets;
        private final String[] totalKeys;
        
        public PeriodConfig(String[] periodKeys, int[] offsets, String[] totalKeys) {
            this.periodKeys = periodKeys;
            this.offsets = offsets;
            this.totalKeys = totalKeys;
        }
        
        public String[] getPeriodKeys() { return periodKeys; }
        public int[] getOffsets() { return offsets; }
        public String[] getTotalKeys() { return totalKeys; }
    }

    /**
     * 根据查询类型获取期间配置
     */
    private PeriodConfig getPeriodConfig(Integer type) {
        return new PeriodConfig(
            new String[]{"in", "one", "two", "three", "exceed"},
            new int[]{0, -1, -2, -3, -4},
            new String[]{"zeroTotal", "oneTotal", "twoTotal", "threeTotal", "exceedThreeTotal"}
        );
    }

    /**
     * 根据期间查询库存总额
     */
    private Double queryStockTotalByPeriod(Integer disId, int offset, String searchDepId, String searchDepIds, List<String> idsGb, Integer type) {
        Map<String, Object> params = buildBaseParams(disId, searchDepId, searchDepIds, idsGb);
        
        switch (type) {
            case 0: // 按天
                if (offset == -4) {
                    String stopDate = formatWhatDay(-4);
                    params.put("stopDate", stopDate);
                } else {
                    String date = formatWhatDay(offset);
                    params.put("date", date);
                }
                break;
            case 1: // 按周
                String[] weekRange = getDateRange(offset, type);
                if (weekRange[0] == null) {
                    params.put("stopDate", weekRange[1]);
                } else {
                    params.put("startDate", weekRange[0]);
                    params.put("stopDate", weekRange[1]);
                }
                break;
            case 2: // 按月
                String[] monthRange = getDateRange(offset, type);
                if (monthRange[0] == null) {
                    params.put("stopDate", monthRange[1]);
                } else {
                    params.put("startDate", monthRange[0]);
                    params.put("stopDate", monthRange[1]);
                }
                break;
        }
        
        Integer count = gbDepGoodsStockService.queryGoodsStockCount(params);
        return count > 0 ? gbDepGoodsStockService.queryDepGoodsRestTotal(params) : 0.0;
    }

    /**
     * 构建各期间数据
     */
    private Map<String, Object> buildPeriodTotals(Integer disId, String searchDepId, String searchDepIds, List<String> idsGb, Integer type) {
        Map<String, Object> result = new HashMap<>();
        
        PeriodConfig config = getPeriodConfig(type);
        
        for (int i = 0; i < config.getPeriodKeys().length; i++) {
            String periodKey = config.getPeriodKeys()[i];
            int offset = config.getOffsets()[i];
            
            Double total = queryStockTotalByPeriod(disId, offset, searchDepId, searchDepIds, idsGb, type);
            
            Map<String, Object> periodData = new HashMap<>();
            
            // 获取日期范围
            String[] dateRange = getDateRange(offset, type);
            if (dateRange[0] == null) {
                periodData.put("stopDate", dateRange[1]);
            } else {
                periodData.put("startDate", dateRange[0]);
                periodData.put("stopDate", dateRange[1]);
            }
            
            // 获取汉字说明
            String dateString = getDateString(offset, type, i);
            periodData.put("dateString", dateString);
            
            periodData.put(config.getTotalKeys()[i], formatDecimal(total));
            
            result.put(periodKey, periodData);
        }
        
        return result;
    }

    /**
     * 获取日期汉字说明
     */
    private String getDateString(int offset, Integer type, int index) {
        if (index == 4) {
            switch (type) {
                case 0: return "3天以前";
                case 1: return "3周以前";
                case 2: return "3个月以前";
                default: return "更早";
            }
        }
        
        switch (type) {
            case 0:
                switch (offset) {
                    case 0: return "今天";
                    case -1: return "昨天";
                    case -2: return "前天";
                    case -3: return "大前天";
                    default: return offset + "天前";
                }
            case 1:
                switch (offset) {
                    case 0: return "本周";
                    case -1: return "1周";
                    case -2: return "2周";
                    case -3: return "3周";
                    default: return Math.abs(offset) + "周以前";
                }
            case 2:
                switch (offset) {
                    case 0: return "本月";
                    case -1: return "1个月";
                    case -2: return "2个月";
                    case -3: return "3个月";
                    default: return Math.abs(offset) + "个月以前";
                }
            default:
                return "未知";
        }
    }

    /**
     * 根据期间获取商品数据
     */
    private List<GbDistributerFatherGoodsEntity> getGoodsByPeriod(Integer disId, Integer whichDay, Map<String, Object> baseParams, Map<String, Object> mapResult, Integer type) {
        // 查询全部库存
        if (whichDay == 99) {
            Map<String, Object> totalData = (Map<String, Object>) mapResult.get("total");
            Double totalAmount = 0.0;
            if (totalData != null && totalData.get("restTotal") != null) {
                totalAmount = Double.parseDouble((String) totalData.get("restTotal"));
            }
            
            Map<String, Object> baseParamsForWaste = new HashMap<>(baseParams);
            return getStockGoodsFatherRestSubTotal(baseParams, totalAmount, baseParamsForWaste);
        }
        
        if (whichDay >= 5) {
            return queryExceedData(disId);
        }
        
        // 根据whichDay获取对应的总额
        Double totalAmount = 0.0;
        String[] periodKeys = {"in", "one", "two", "three", "exceed"};
        String[] totalKeys = {"zeroTotal", "oneTotal", "twoTotal", "threeTotal", "exceedThreeTotal"};
        
        if (whichDay < periodKeys.length) {
            Map<String, Object> periodData = (Map<String, Object>) mapResult.get(periodKeys[whichDay]);
            if (periodData != null && periodData.get(totalKeys[whichDay]) != null) {
                totalAmount = Double.parseDouble((String) periodData.get(totalKeys[whichDay]));
            }
        }
        
        // 构建查询参数
        Map<String, Object> queryParams = new HashMap<>(baseParams);
        Map<String, Object> queryParamsW = new HashMap<>(baseParams);
        
        if (whichDay >= 0 && whichDay < 5) {
            switch (type) {
                case 0: // 按天
                    if (whichDay == 4) {
                        String stopDate = formatWhatDay(-4);
                        queryParams.put("stopDate", stopDate);
                        queryParamsW.put("stopDate", stopDate);
                    } else {
                        String date = formatWhatDay(-whichDay);
                        queryParams.put("date", date);
                        queryParamsW.put("date", date);
                    }
                    break;
                case 1: // 按周
                    if (whichDay == 4) {
                        String[] weekRange = getDateRange(-4, type);
                        queryParams.put("stopDate", weekRange[1]);
                        queryParamsW.put("stopDate", weekRange[1]);
                    } else {
                        String[] weekRange = getDateRange(-whichDay, type);
                        queryParams.put("startDate", weekRange[0]);
                        queryParams.put("stopDate", weekRange[1]);
                        queryParamsW.put("startDate", weekRange[0]);
                        queryParamsW.put("stopDate", weekRange[1]);
                    }
                    break;
                case 2: // 按月
                    if (whichDay == 4) {
                        String[] monthRange = getDateRange(-4, type);
                        queryParams.put("stopDate", monthRange[1]);
                        queryParamsW.put("stopDate", monthRange[1]);
                    } else {
                        String[] monthRange = getDateRange(-whichDay, type);
                        queryParams.put("startDate", monthRange[0]);
                        queryParams.put("stopDate", monthRange[1]);
                        queryParamsW.put("startDate", monthRange[0]);
                        queryParamsW.put("stopDate", monthRange[1]);
                    }
                    break;
            }
        }
        
        return getStockGoodsFatherRestSubTotal(queryParams, totalAmount, queryParamsW);
    }

    /**
     * 获取库存商品父类剩余总额
     */
    private List<GbDistributerFatherGoodsEntity> getStockGoodsFatherRestSubTotal(Map<String, Object> map0, Double total, Map<String, Object> map0W) {
        map0.put("restWeight", 0);
        
        List<GbDistributerFatherGoodsEntity> stockAndRecordFatherGoodsTreeSet = getStockFatherGoodsTreeSet(map0);
        
        return getStockFatherGoodsRestSubtotal(stockAndRecordFatherGoodsTreeSet, map0, total, map0W);
    }

    /**
     * 获取库存父类树形结构
     */
    private List<GbDistributerFatherGoodsEntity> getStockFatherGoodsTreeSet(Map<String, Object> map0) {
        Integer integerStock = gbDepGoodsStockService.queryGoodsStockCount(map0);
        
        if (integerStock > 0) {
            List<GbDistributerFatherGoodsEntity> fatherGoodsEntities = gbDepGoodsStockService.queryDepStockTreeFatherGoodsByParams(map0);
            return fatherGoodsEntities;
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * 获取库存父类剩余总额
     */
    private List<GbDistributerFatherGoodsEntity> getStockFatherGoodsRestSubtotal(
            List<GbDistributerFatherGoodsEntity> treeSet, Map<String, Object> map0, Double total, Map<String, Object> map0W) {

        for (GbDistributerFatherGoodsEntity greatGrandFather : treeSet) {
            BigDecimal greatGrandTotal = new BigDecimal(0);
            double greatWasteValue = 0.0;
            int greatGrandStockCount = 0;
            
            // 遍历子类
            if (greatGrandFather.getFatherGoodsEntities() != null) {
                for (GbDistributerFatherGoodsEntity grandFather : greatGrandFather.getFatherGoodsEntities()) {
                    BigDecimal grandTotal = new BigDecimal(0);
                    double grandWasteValue = 0.0;
                    int grandStockCount = 0;
                    
                    // 遍历父类
                    if (grandFather.getFatherGoodsEntities() != null) {
                        for (GbDistributerFatherGoodsEntity fatherGoods : grandFather.getFatherGoodsEntities()) {
                            // 构建查询参数
                            Map<String, Object> mapFather = new HashMap<>(map0);
                            mapFather.put("disGoodsGreatId", greatGrandFather.getGbDistributerFatherGoodsId());
                            mapFather.put("disGoodsGrandId", grandFather.getGbDistributerFatherGoodsId());
                            mapFather.put("disGoodsFatherId", fatherGoods.getGbDistributerFatherGoodsId());
                            
                            // 查询子类商品
                            List<GbDepartmentGoodsStockEntity> stockList = gbDepGoodsStockService.queryGoodsStockByParams(mapFather);
                            
                            double fatherSubtotal = 0.0;
                            for (GbDepartmentGoodsStockEntity stock : stockList) {
                                if (stock.getGbDgsRestSubtotal() != null) {
                                    try {
                                        fatherSubtotal += Double.parseDouble(stock.getGbDgsRestSubtotal());
                                    } catch (NumberFormatException e) {
                                        // ignore
                                    }
                                }
                            }
                            
                            fatherGoods.setFatherStockTotalString(String.format("%.1f", fatherSubtotal));
                            grandTotal = grandTotal.add(new BigDecimal(fatherSubtotal));
                            grandStockCount += stockList.size();
                            
                            // 计算占比
                            if (total > 0) {
                                double percent = fatherSubtotal / total * 100;
                                fatherGoods.setFatherStockTotalPercent(String.format("%.1f", percent));
                            }
                            
                            // 设置库存数量为子商品数量
                            fatherGoods.setFatherStockManyString(String.valueOf(stockList.size()));
                            
                            // 查询过期损耗（这里简单设为0，可根据实际业务计算）
                            fatherGoods.setFatherWasteTotalString("0");
                            fatherGoods.setFatherWasteRateString("0");
                            
                            // 查询损耗
//                            Map<String, Object> wasteParams = new HashMap<>(map0W);
//                            wasteParams.put("disGoodsGreatId", greatGrandFather.getGbDistributerFatherGoodsId());
//                            wasteParams.put("disGoodsGrandId", grandFather.getGbDistributerFatherGoodsId());
//                            wasteParams.put("disGoodsFatherId", fatherGoods.getGbDistributerFatherGoodsId());
                            
//                            Integer wasteCount = gbDepGoodsDailyService.queryDepGoodsDailyCount(wasteParams);
//                            if (wasteCount > 0) {
//                                double waste = wasteCount * 10.0; // 估算损耗
//                                grandWasteValue += waste;
//                            }
                        }
                    }
                    
                    grandFather.setFatherStockTotalString(String.format("%.1f", grandTotal.doubleValue()));
                    grandFather.setFatherStockManyString(String.valueOf(grandStockCount));
                    greatGrandTotal = greatGrandTotal.add(grandTotal);
                    greatGrandStockCount += grandStockCount;
                    greatWasteValue += grandWasteValue;
                }
            }
            
            greatGrandFather.setFatherStockTotalString(String.format("%.1f", greatGrandTotal.doubleValue()));
            greatGrandFather.setFatherStockManyString(String.valueOf(greatGrandStockCount));
            if (total > 0) {
                double percent = greatGrandTotal.doubleValue() / total * 100;
                greatGrandFather.setFatherStockTotalPercent(String.format("%.1f", percent));
            }
        }
        
        return treeSet;
    }

    /**
     * 查询超过期限的数据
     */
    private List<GbDistributerFatherGoodsEntity> queryExceedData(Integer disId) {
        Map<String, Object> mapRen4 = new HashMap<>();
        mapRen4.put("disId", disId);
        mapRen4.put("dayuStatus", -1);
        mapRen4.put("stopDate", formatWhatDay(-4));
        mapRen4.put("restWeight", 0);

        Map<String, Object> mapRen4W = new HashMap<>(mapRen4);

        Double exceedThreeTotal = 0.0;
        Integer integer33 = gbDepGoodsStockService.queryGoodsStockCount(mapRen4);
        List<GbDistributerFatherGoodsEntity> recentlyStockDayuThree = new ArrayList<>();
        if (integer33 > 0) {
            exceedThreeTotal = gbDepGoodsStockService.queryDepGoodsRestTotal(mapRen4);
            recentlyStockDayuThree = getStockGoodsFatherRestSubTotal(mapRen4, exceedThreeTotal, mapRen4W);
        }
        return recentlyStockDayuThree;
    }

    /**
     * 根据大类ID查询商品库存按时间段分类统计
     */
    @RequestMapping(value = "/disGetDayStockByGreatId", method = RequestMethod.POST)
    @ResponseBody
    public R disGetDayStockByGreatId(
            @RequestParam Integer disId,
            @RequestParam(required = false) String searchDepId,
            @RequestParam String greatId,
            @RequestParam Integer whichDay,
            @RequestParam(required = false, defaultValue = "0") Integer type) {
        Map<String, Object> map = new HashMap<>();

        if (whichDay == 99 || whichDay == 0) {
            map.put("oneDay", disGetStockDayStockByGreatId(greatId, whichDay, type));
        } else {
            map.put("oneDay", disGetStockDayStockByGreatId(greatId, -whichDay, type));
        }

        return R.ok().put("data", map);
    }

    /**
     * 根据大类查询库存数据（私有方法）
     */
    private Map<String, Object> disGetStockDayStockByGreatId(String greatId, Integer which, Integer type) {
        List<GbDistributerGoodsEntity> stockGoodsList = new ArrayList<>();
        Map<String, Object> result = new HashMap<>();
        double total = 0.0;
        Map<String, Object> map = new HashMap<>();
        map.put("disGoodsGreatId", greatId);
        map.put("restWeight", 0);

        if (which != 99) {
            switch (type) {
                case 0: // 按天
                    if (which == -4) {
                        map.put("stopDate", formatWhatDay(which));
                    } else {
                        map.put("date", formatWhatDay(which));
                    }
                    break;
                case 1: // 按周
                    if (which == -4) {
                        String[] weekRange = getDateRange(-4, type);
                        map.put("stopDate", weekRange[1]);
                    } else {
                        String[] weekRange = getDateRange(which, type);
                        map.put("startDate", weekRange[0]);
                        map.put("stopDate", weekRange[1]);
                    }
                    break;
                case 2: // 按月
                    if (which == -4) {
                        String[] monthRange = getDateRange(-4, type);
                        map.put("stopDate", monthRange[1]);
                    } else {
                        String[] monthRange = getDateRange(which, type);
                        map.put("startDate", monthRange[0]);
                        map.put("stopDate", monthRange[1]);
                    }
                    break;
                default:
                    if (which == -4) {
                        map.put("stopDate", formatWhatDay(which));
                    } else {
                        map.put("date", formatWhatDay(which));
                    }
                    break;
            }
        }


        Integer integerIn = gbDepGoodsStockService.queryGoodsStockCount(map);

        if (integerIn > 0) {
            map.put("orderByGoodsStockTotal", 1);
            stockGoodsList = gbDepGoodsStockService.queryDisGoodsStockByParams(map);
            
            // 使用简化版查询，一次性获取所有库存记录及关联数据
            Map<String, Object> stockParams = new HashMap<>(map);
            stockParams.remove("disGoodsGreatId");
            stockParams.remove("orderByGoodsStockTotal");
            List<GbDepartmentGoodsStockSimpleEntity> simpleStockList = gbDepGoodsStockService.queryGoodsStockSimpleByParams(stockParams);
            System.out.println("=== DEBUG: simpleStockList size: " + simpleStockList.size());
            if (simpleStockList.size() > 0) {
                System.out.println("=== DEBUG: first stock: " + simpleStockList.get(0));
            }
            System.out.println("=== DEBUG: stockGoodsList size: " + stockGoodsList.size());
            
            // 按商品ID分组，设置到各个商品下
            for (GbDistributerGoodsEntity goods : stockGoodsList) {
                List<GbDepartmentGoodsStockSimpleEntity> goodsSimpleStocks = new ArrayList<>();
                for (GbDepartmentGoodsStockSimpleEntity stock : simpleStockList) {
                    if (stock.getGbDgsGbDisGoodsId() != null && goods.getGbDistributerGoodsId() != null
                            && stock.getGbDgsGbDisGoodsId().equals(goods.getGbDistributerGoodsId())) {
                        goodsSimpleStocks.add(stock);
                    }
                }
                goods.setGbDepartmentGoodsStockSimpleEntities(goodsSimpleStocks);
            }
            
            total = gbDepGoodsStockService.queryDepGoodsRestTotal(map);
            result.put("arr", stockGoodsList);
        } else {
            result.put("arr", stockGoodsList);
        }


        double greatWasteValue = 0.0;
        Integer wasteGoodsCount = 0;

        int count = gbDepGoodsStockService.queryGoodsStockCount(map);
        if (count > 0) {
            greatWasteValue = gbDepGoodsStockService.queryDepGoodsWasteTotal(map);
            wasteGoodsCount = gbDepGoodsStockService.queryDisStockGoodsCount(map);
        }

        result.put("wasteGoodsCount", wasteGoodsCount);
        result.put("wasteSubtotal", new BigDecimal(greatWasteValue).setScale(1, BigDecimal.ROUND_HALF_UP));

        String dateString = getDateString(which, type, 0);
        result.put("dateString", dateString);
        result.put("total", new BigDecimal(total).setScale(1, BigDecimal.ROUND_HALF_UP));

        return result;
    }

    /**
     * 保存部门废弃商品库存
     */
    @RequestMapping(value = "/saveDepWasteGoodsStock", method = RequestMethod.POST)
    @ResponseBody
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
        BigDecimal costPrice = new BigDecimal(stock.getGbDgsPrice()); // 成本单价

        // 1.4 如果是废弃接口
        if (what.equals("waste")) {
            // 转换数据
            BigDecimal wasteWeight = new BigDecimal(stock.getGbDgsMyWasteWeight()); // 最新提交待损耗数量
            BigDecimal wasteSubtotal = wasteWeight.multiply(costPrice).setScale(1, BigDecimal.ROUND_HALF_UP); // 最新剩余成本

            BigDecimal produceWeight = new BigDecimal(stock.getGbDgsMyProduceWeight()).setScale(1, BigDecimal.ROUND_HALF_UP); // 最新提交待损耗数量
            BigDecimal produceSubtotal = produceWeight.multiply(costPrice).setScale(2, BigDecimal.ROUND_HALF_UP); // 总制作成本
            BigDecimal allWeightProduce = new BigDecimal(stock.getGbDgsProduceWeight()).add(produceWeight).setScale(1, BigDecimal.ROUND_HALF_UP); // 总损耗数量
            BigDecimal allSubtotalProduce = new BigDecimal(stock.getGbDgsProduceSubtotal()).add(produceSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP); // 总损耗数量
            myChangeWeight = wasteWeight.add(produceWeight);
            myChangeSubtotal = wasteSubtotal.add(produceSubtotal);
            if (!stock.getGbDgsSellingPrice().equals("-1")) {

                // 利润
                BigDecimal gbDgsBetweenPrice = new BigDecimal(stock.getGbDgsBetweenPrice()); // 生产利润单价
                BigDecimal newProfitSubtotal = gbDgsBetweenPrice.multiply(produceWeight).setScale(1, BigDecimal.ROUND_HALF_UP);
                profitSubtotal = new BigDecimal(stock.getGbDgsProfitSubtotal()).add(newProfitSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP);
                stock.setGbDgsProfitSubtotal(profitSubtotal.toString()); //
                // 销售利润=总利润+利润
                BigDecimal stockAfterProfitSubtotal = new BigDecimal(stock.getGbDgsAfterProfitSubtotal()); // 总的销售利润
                newAfterProfitSubtotal = stockAfterProfitSubtotal.add(newProfitSubtotal).subtract(wasteSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP);
                stock.setGbDgsAfterProfitSubtotal(newAfterProfitSubtotal.toString());

                BigDecimal newSellingSubtotal = new BigDecimal(stock.getGbDgsSellingPrice()).multiply(produceWeight);
                salesSubtotal = newSellingSubtotal.add(new BigDecimal(stock.getGbDgsProduceSellingSubtotal()));
                stock.setGbDgsProduceSellingSubtotal(salesSubtotal.toString());
                // 产生利润的数量
                BigDecimal add = new BigDecimal(stock.getGbDgsProfitWeight()).add(produceWeight);
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
            if (new BigDecimal(stock.getGbDgsRestWeightShowStandard()).compareTo(new BigDecimal(0)) == 1) {
                Integer gbDgsGbDepDisGoodsId = stock.getGbDgsGbDepDisGoodsId();
                GbDepartmentDisGoodsEntity departmentDisGoodsEntity = gbDepartmentDisGoodsService.getById(gbDgsGbDepDisGoodsId);
                BigDecimal decimal = new BigDecimal(departmentDisGoodsEntity.getGbDdgShowStandardScale());
                BigDecimal myChangeWeightScale = myChangeWeight.divide(decimal, 1, BigDecimal.ROUND_HALF_UP);
                BigDecimal decimal1 = new BigDecimal(stock.getGbDgsRestWeightShowStandard()).subtract(myChangeWeightScale).setScale(1, BigDecimal.ROUND_HALF_UP);
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

    // ==================== 常量方法 ====================

    private String formatWhatFullTime(int day) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, day);
        return sdf.format(calendar.getTime());
    }

    private String formatFullTime() {
        return formatWhatFullTime(0);
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
