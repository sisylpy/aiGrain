package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockReduceEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockSimpleEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDepartmentGoodsStockQueryService;
import com.nongxinle.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 部门商品库存查询编排实现（门店时段看板等）。
 */
@Service
public class GbDepartmentGoodsStockQueryServiceImpl implements GbDepartmentGoodsStockQueryService {

    private static final Logger log = LoggerFactory.getLogger(GbDepartmentGoodsStockQueryServiceImpl.class);

    private static final String[] PERIOD_KEYS = {"in", "one", "two", "three", "exceed"};
    private static final int[] PERIOD_OFFSETS = {0, -1, -2, -3, -4};
    private static final String[] TOTAL_KEYS = {"zeroTotal", "oneTotal", "twoTotal", "threeTotal", "exceedThreeTotal"};

    @Autowired
    private GbDepartmentGoodsStockService gbDepGoodsStockService;
    @Autowired
    private GbDepartmentGoodsStockReduceService gbDepartmentGoodsStockReduceService;

    @Override
    public List<GbDepartmentGoodsStockEntity> queryDepGoodsBusiness(Integer depGoodsId, String startDate, String stopDate) {
        Map<String, Object> mapMain = new HashMap<>();
        mapMain.put("depGoodsId", depGoodsId);
        mapMain.put("dayuStatus", -1);
        mapMain.put("restWeight", 0);
        if (startDate != null && !startDate.isEmpty()) {
            mapMain.put("startDate", startDate);
        }
        if (stopDate != null && !stopDate.isEmpty()) {
            mapMain.put("stopDate", stopDate);
        }

        List<GbDepartmentGoodsStockEntity> withRest = gbDepGoodsStockService.queryGoodsStockByParams(mapMain);

        Map<String, Object> mapToday = new HashMap<>();
        mapToday.put("depGoodsId", depGoodsId);
        mapToday.put("dayuStatus", -1);
        mapToday.put("date", DateUtils.formatWhatDay(0));
        mapToday.put("equalRestWeight", 0);

        List<GbDepartmentGoodsStockEntity> exhaustedToday = gbDepGoodsStockService.queryGoodsStockByParams(mapToday);

        Map<Integer, GbDepartmentGoodsStockEntity> merged = new LinkedHashMap<>();
        for (GbDepartmentGoodsStockEntity e : withRest) {
            if (e.getGbDepartmentGoodsStockId() != null) {
                merged.put(e.getGbDepartmentGoodsStockId(), e);
            }
        }
        for (GbDepartmentGoodsStockEntity e : exhaustedToday) {
            if (e.getGbDepartmentGoodsStockId() != null) {
                merged.putIfAbsent(e.getGbDepartmentGoodsStockId(), e);
            }
        }
        List<GbDepartmentGoodsStockEntity> result = new ArrayList<>(merged.values());
        attachGoodsStockReduces(result);
        log.debug("queryDepGoodsBusiness depGoodsId={} mergedStocks={}", depGoodsId, result.size());
        return result;
    }

    private void attachGoodsStockReduces(List<GbDepartmentGoodsStockEntity> stocks) {
        if (stocks.isEmpty()) {
            return;
        }
        List<Integer> stockIds = stocks.stream()
                .map(GbDepartmentGoodsStockEntity::getGbDepartmentGoodsStockId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (stockIds.isEmpty()) {
            return;
        }
        List<GbDepartmentGoodsStockReduceEntity> all = gbDepartmentGoodsStockReduceService.list(
                new LambdaQueryWrapper<GbDepartmentGoodsStockReduceEntity>()
                        .in(GbDepartmentGoodsStockReduceEntity::getGbDgsrGbGoodsStockId, stockIds)
                        .orderByDesc(GbDepartmentGoodsStockReduceEntity::getGbDepartmentGoodsStockReduceId));
        Map<Integer, List<GbDepartmentGoodsStockReduceEntity>> byStock = new HashMap<>();
        for (GbDepartmentGoodsStockReduceEntity r : all) {
            Integer sid = r.getGbDgsrGbGoodsStockId();
            if (sid == null) {
                continue;
            }
            byStock.computeIfAbsent(sid, k -> new ArrayList<>()).add(r);
        }
        for (GbDepartmentGoodsStockEntity stock : stocks) {
            Integer id = stock.getGbDepartmentGoodsStockId();
            stock.setGoodsStockReduceEntityList(
                    id == null ? Collections.emptyList() : byStock.getOrDefault(id, Collections.emptyList()));
        }
    }

    @Override
    public Map<String, Object> queryMendianStockTypePeriod(Integer disId, Integer whichDay, String searchDepIds, String searchDepId, Integer type) {
        log.debug("queryMendianStockTypePeriod disId={} whichDay={} searchDepIds={} searchDepId={} type={}",
                disId, whichDay, searchDepIds, searchDepId, type);

        Map<String, Object> mapResult = new HashMap<>();
        List<String> idsGb = parseDepIds(searchDepIds);
        Map<String, Object> baseParams = buildBaseParams(disId, searchDepId, searchDepIds, idsGb);

        Integer totalCount = gbDepGoodsStockService.queryGoodsStockCount(baseParams);

        if (totalCount > 0) {
            Double totalAmount = gbDepGoodsStockService.queryDepGoodsRestTotal(baseParams);
            Map<String, Object> totalData = new HashMap<>();
            totalData.put("dateString", getTotalDateString(type));
            totalData.put("restTotal", formatDecimal(totalAmount));
            mapResult.put("total", totalData);
            mapResult.putAll(buildPeriodTotals(baseParams, type));
        } else {
            Map<String, Object> emptyTotal = new HashMap<>();
            emptyTotal.put("dateString", getTotalDateString(type));
            emptyTotal.put("restTotal", "0");
            mapResult.put("total", emptyTotal);
        }

        List<GbDistributerFatherGoodsEntity> goods = getGoodsByPeriod(disId, whichDay, baseParams, mapResult, type);
        mapResult.put("arr", goods);
        return mapResult;
    }

    @Override
    public Map<String, Object> queryDayStockByGreatId(Integer disId, String searchDepId, Integer depId, String greatId, Integer which, Integer type) {
        return buildDayStockByGreatId(greatId, which, type, disId, searchDepId, depId);
    }

    private List<String> parseDepIds(String searchDepIds) {
        if (searchDepIds == null || "-1".equals(searchDepIds)) {
            return new ArrayList<>();
        }
        return Arrays.asList(searchDepIds.split(","));
    }

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

    private String getTotalDateString(Integer type) {
        int t = type == null ? 0 : type;
        switch (t) {
            case 0:
                return "全部";
            case 1:
                return "全部（按周）";
            case 2:
                return "全部（按月）";
            default:
                return "全部";
        }
    }

    private String formatDecimal(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return "0";
        }
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toString();
    }

    private String formatWhatDay(int what) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, what);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(cal.getTime());
    }

    private String[] getDateRange(int offset, int type) {
        switch (type) {
            case 0:
                String date = formatWhatDay(-offset);
                return new String[]{date, date};
            case 1:
                if (offset == -4) {
                    return new String[]{null, getWeekStartDate(-3)};
                }
                return new String[]{getWeekStartDate(offset), getWeekStopDate(offset)};
            case 2:
                if (offset == -4) {
                    return new String[]{null, getMonthStartDate(-3)};
                }
                return new String[]{getMonthStartDate(offset), getMonthStopDate(offset)};
            default:
                return new String[]{null, null};
        }
    }

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

    private Double queryStockTotalByPeriod(Map<String, Object> baseParams, int offset, int type) {
        Map<String, Object> params = new HashMap<>(baseParams);

        switch (type) {
            case 0:
                if (offset == -4) {
                    params.put("stopDate", formatWhatDay(-4));
                } else {
                    params.put("date", formatWhatDay(offset));
                }
                break;
            case 1:
                String[] weekRange = getDateRange(offset, type);
                if (weekRange[0] == null) {
                    params.put("stopDate", weekRange[1]);
                } else {
                    params.put("startDate", weekRange[0]);
                    params.put("stopDate", weekRange[1]);
                }
                break;
            case 2:
                String[] monthRange = getDateRange(offset, type);
                if (monthRange[0] == null) {
                    params.put("stopDate", monthRange[1]);
                } else {
                    params.put("startDate", monthRange[0]);
                    params.put("stopDate", monthRange[1]);
                }
                break;
            default:
                break;
        }

        Integer count = gbDepGoodsStockService.queryGoodsStockCount(params);
        return count > 0 ? gbDepGoodsStockService.queryDepGoodsRestTotal(params) : 0.0;
    }

    private Map<String, Object> buildPeriodTotals(Map<String, Object> baseParams, Integer type) {
        Map<String, Object> result = new HashMap<>();
        int t = type == null ? 0 : type;

        // TODO: 可改为 Mapper 单次 SQL 按日期桶聚合，减少 5×(count+sum) 往返
        for (int i = 0; i < PERIOD_KEYS.length; i++) {
            String periodKey = PERIOD_KEYS[i];
            int offset = PERIOD_OFFSETS[i];

            Double total = queryStockTotalByPeriod(baseParams, offset, t);

            Map<String, Object> periodData = new HashMap<>();
            String[] dateRange = getDateRange(offset, t);
            if (dateRange[0] == null) {
                periodData.put("stopDate", dateRange[1]);
            } else {
                periodData.put("startDate", dateRange[0]);
                periodData.put("stopDate", dateRange[1]);
            }

            periodData.put("dateString", getDateString(offset, t, i));
            periodData.put(TOTAL_KEYS[i], formatDecimal(total));
            result.put(periodKey, periodData);
        }

        return result;
    }

    private String getDateString(int offset, int type, int index) {
        if (index == 4) {
            switch (type) {
                case 0:
                    return "3天以前";
                case 1:
                    return "3周以前";
                case 2:
                    return "3个月以前";
                default:
                    return "更早";
            }
        }

        switch (type) {
            case 0:
                switch (offset) {
                    case 0:
                        return "今天";
                    case -1:
                        return "昨天";
                    case -2:
                        return "前天";
                    case -3:
                        return "大前天";
                    default:
                        return offset + "天前";
                }
            case 1:
                switch (offset) {
                    case 0:
                        return "本周";
                    case -1:
                        return "1周";
                    case -2:
                        return "2周";
                    case -3:
                        return "3周";
                    default:
                        return Math.abs(offset) + "周以前";
                }
            case 2:
                switch (offset) {
                    case 0:
                        return "本月";
                    case -1:
                        return "1个月";
                    case -2:
                        return "2个月";
                    case -3:
                        return "3个月";
                    default:
                        return Math.abs(offset) + "个月以前";
                }
            default:
                return "未知";
        }
    }

    private List<GbDistributerFatherGoodsEntity> getGoodsByPeriod(Integer disId, Integer whichDay, Map<String, Object> baseParams,
                                                                  Map<String, Object> mapResult, Integer type) {
        int t = type == null ? 0 : type;

        if (whichDay == 99) {
            Map<String, Object> totalData = (Map<String, Object>) mapResult.get("total");
            double totalAmount = 0.0;
            if (totalData != null && totalData.get("restTotal") != null) {
                totalAmount = Double.parseDouble((String) totalData.get("restTotal"));
            }

            Map<String, Object> baseParamsForWaste = new HashMap<>(baseParams);
            return getStockGoodsFatherRestSubTotal(baseParams, totalAmount, baseParamsForWaste);
        }

        if (whichDay >= 5) {
            return queryExceedData(disId);
        }

        double totalAmount = 0.0;
        if (whichDay < PERIOD_KEYS.length) {
            Map<String, Object> periodData = (Map<String, Object>) mapResult.get(PERIOD_KEYS[whichDay]);
            if (periodData != null && periodData.get(TOTAL_KEYS[whichDay]) != null) {
                totalAmount = Double.parseDouble((String) periodData.get(TOTAL_KEYS[whichDay]));
            }
        }

        Map<String, Object> queryParams = new HashMap<>(baseParams);
        Map<String, Object> queryParamsW = new HashMap<>(baseParams);

        if (whichDay >= 0 && whichDay < 5) {
            switch (t) {
                case 0:
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
                case 1:
                    if (whichDay == 4) {
                        String[] weekRange = getDateRange(-4, t);
                        queryParams.put("stopDate", weekRange[1]);
                        queryParamsW.put("stopDate", weekRange[1]);
                    } else {
                        String[] weekRange = getDateRange(-whichDay, t);
                        queryParams.put("startDate", weekRange[0]);
                        queryParams.put("stopDate", weekRange[1]);
                        queryParamsW.put("startDate", weekRange[0]);
                        queryParamsW.put("stopDate", weekRange[1]);
                    }
                    break;
                case 2:
                    if (whichDay == 4) {
                        String[] monthRange = getDateRange(-4, t);
                        queryParams.put("stopDate", monthRange[1]);
                        queryParamsW.put("stopDate", monthRange[1]);
                    } else {
                        String[] monthRange = getDateRange(-whichDay, t);
                        queryParams.put("startDate", monthRange[0]);
                        queryParams.put("stopDate", monthRange[1]);
                        queryParamsW.put("startDate", monthRange[0]);
                        queryParamsW.put("stopDate", monthRange[1]);
                    }
                    break;
                default:
                    break;
            }
        }

        return getStockGoodsFatherRestSubTotal(queryParams, totalAmount, queryParamsW);
    }

    private List<GbDistributerFatherGoodsEntity> getStockGoodsFatherRestSubTotal(Map<String, Object> map0, Double total, Map<String, Object> map0W) {
        map0.put("restWeight", 0);
        List<GbDistributerFatherGoodsEntity> stockAndRecordFatherGoodsTreeSet = getStockFatherGoodsTreeSet(map0);
        return getStockFatherGoodsRestSubtotal(stockAndRecordFatherGoodsTreeSet, map0, total, map0W);
    }

    private List<GbDistributerFatherGoodsEntity> getStockFatherGoodsTreeSet(Map<String, Object> map0) {
        Integer integerStock = gbDepGoodsStockService.queryGoodsStockCount(map0);
        if (integerStock > 0) {
            return gbDepGoodsStockService.queryDepStockTreeFatherGoodsByParams(map0);
        }
        return new ArrayList<>();
    }

    private List<GbDistributerFatherGoodsEntity> getStockFatherGoodsRestSubtotal(
            List<GbDistributerFatherGoodsEntity> treeSet, Map<String, Object> map0, Double total, Map<String, Object> map0W) {

        Map<Integer, List<GbDepartmentGoodsStockEntity>> byFatherId = new HashMap<>();
        if (!treeSet.isEmpty()) {
            Map<String, Object> batchParams = new HashMap<>(map0);
            List<GbDepartmentGoodsStockEntity> allStocks = gbDepGoodsStockService.queryGoodsStockListForMendianPeriod(batchParams);
            for (GbDepartmentGoodsStockEntity stock : allStocks) {
                Integer fid = stock.getGbDgsGbDisGoodsFatherId();
                if (fid == null) {
                    continue;
                }
                byFatherId.computeIfAbsent(fid, k -> new ArrayList<>()).add(stock);
            }
        }

        for (GbDistributerFatherGoodsEntity greatGrandFather : treeSet) {
            BigDecimal greatGrandTotal = new BigDecimal(0);
            double greatWasteValue = 0.0;
            int greatGrandStockCount = 0;

            if (greatGrandFather.getFatherGoodsEntities() != null) {
                for (GbDistributerFatherGoodsEntity grandFather : greatGrandFather.getFatherGoodsEntities()) {
                    BigDecimal grandTotal = new BigDecimal(0);
                    double grandWasteValue = 0.0;
                    int grandStockCount = 0;

                    if (grandFather.getFatherGoodsEntities() != null) {
                        for (GbDistributerFatherGoodsEntity fatherGoods : grandFather.getFatherGoodsEntities()) {
                            Integer fatherId = fatherGoods.getGbDistributerFatherGoodsId();
                            List<GbDepartmentGoodsStockEntity> stockList =
                                    fatherId == null ? Collections.emptyList() : byFatherId.getOrDefault(fatherId, Collections.emptyList());

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

                            if (total > 0) {
                                double percent = fatherSubtotal / total * 100;
                                fatherGoods.setFatherStockTotalPercent(String.format("%.1f", percent));
                            }

                            fatherGoods.setFatherStockManyString(String.valueOf(stockList.size()));
                            fatherGoods.setFatherWasteTotalString("0");
                            fatherGoods.setFatherWasteRateString("0");
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
     * 按大类 + 时间维度组装「商品列表 + 批次简化行 + 损耗统计」。
     */
    private Map<String, Object> buildDayStockByGreatId(String greatId, Integer which, Integer type,
                                                        Integer disId, String searchDepId, Integer depId) {
        List<GbDistributerGoodsEntity> stockGoodsList = new ArrayList<>();
        Map<String, Object> result = new HashMap<>();
        double total = 0.0;
        Map<String, Object> map = new HashMap<>();
        map.put("disGoodsGreatId", greatId);
        map.put("restWeight", 0);

        boolean greatIdEffective = greatId != null && !greatId.isEmpty() && !"-1".equals(greatId);
        if (!greatIdEffective && disId != null) {
            map.put("disId", disId);
        }
        if (depId != null) {
            map.put("depId", depId);
        } else if (searchDepId != null && !"-1".equals(searchDepId) && !searchDepId.isEmpty()) {
            map.put("depFatherId", searchDepId);
        }

        int t = type == null ? 0 : type;
        if (which != null && which != 99) {
            switch (t) {
                case 0:
                    if (which == -4) {
                        map.put("stopDate", formatWhatDay(which));
                    } else {
                        map.put("date", formatWhatDay(which));
                    }
                    break;
                case 1:
                    if (which == -4) {
                        String[] weekRange = getDateRange(-4, t);
                        map.put("stopDate", weekRange[1]);
                    } else {
                        String[] weekRange = getDateRange(which, t);
                        map.put("startDate", weekRange[0]);
                        map.put("stopDate", weekRange[1]);
                    }
                    break;
                case 2:
                    if (which == -4) {
                        String[] monthRange = getDateRange(-4, t);
                        map.put("stopDate", monthRange[1]);
                    } else {
                        String[] monthRange = getDateRange(which, t);
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

            Map<String, Object> stockParams = new HashMap<>(map);
            stockParams.remove("disGoodsGreatId");
            stockParams.remove("orderByGoodsStockTotal");
            List<GbDepartmentGoodsStockSimpleEntity> simpleStockList = gbDepGoodsStockService.queryGoodsStockSimpleByParams(stockParams);
            log.debug("buildDayStockByGreatId simpleStockList size={}, stockGoodsList size={}",
                    simpleStockList.size(), stockGoodsList.size());

            Map<Integer, List<GbDepartmentGoodsStockSimpleEntity>> stocksByDisGoodsId = new HashMap<>();
            for (GbDepartmentGoodsStockSimpleEntity stock : simpleStockList) {
                Integer sid = stock.getGbDgsGbDisGoodsId();
                if (sid == null) {
                    continue;
                }
                stocksByDisGoodsId.computeIfAbsent(sid, k -> new ArrayList<>()).add(stock);
            }
            for (GbDistributerGoodsEntity goods : stockGoodsList) {
                Integer gid = goods.getGbDistributerGoodsId();
                goods.setGbDepartmentGoodsStockSimpleEntities(
                        gid == null ? new ArrayList<>() : stocksByDisGoodsId.getOrDefault(gid, new ArrayList<>()));
            }

            total = gbDepGoodsStockService.queryDepGoodsRestTotal(map);
            result.put("arr", stockGoodsList);
        } else {
            result.put("arr", stockGoodsList);
        }

        double greatWasteValue = 0.0;
        Integer wasteGoodsCount = 0;
        if (integerIn > 0) {
            greatWasteValue = gbDepGoodsStockService.queryDepGoodsWasteTotal(map);
            wasteGoodsCount = gbDepGoodsStockService.queryDisStockGoodsCount(map);
        }

        result.put("wasteGoodsCount", wasteGoodsCount);
        result.put("wasteSubtotal", BigDecimal.valueOf(greatWasteValue).setScale(1, RoundingMode.HALF_UP));

        int whichForLabel = which == null ? 0 : which;
        result.put("dateString", getDateString(whichForLabel, t, 0));
        result.put("total", BigDecimal.valueOf(total).setScale(1, RoundingMode.HALF_UP));

        return result;
    }

    private List<GbDistributerFatherGoodsEntity> queryExceedData(Integer disId) {
        Map<String, Object> mapRen4 = new HashMap<>();
        mapRen4.put("disId", disId);
        mapRen4.put("dayuStatus", -1);
        mapRen4.put("stopDate", formatWhatDay(-4));
        mapRen4.put("restWeight", 0);

        Map<String, Object> mapRen4W = new HashMap<>(mapRen4);

        List<GbDistributerFatherGoodsEntity> recentlyStockDayuThree = new ArrayList<>();
        Integer integer33 = gbDepGoodsStockService.queryGoodsStockCount(mapRen4);
        if (integer33 > 0) {
            Double exceedThreeTotal = gbDepGoodsStockService.queryDepGoodsRestTotal(mapRen4);
            recentlyStockDayuThree = getStockGoodsFatherRestSubTotal(mapRen4, exceedThreeTotal, mapRen4W);
        }
        return recentlyStockDayuThree;
    }
}
