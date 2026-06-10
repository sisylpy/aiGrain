package com.nongxinle.service.support;

import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import com.nongxinle.utils.GbDateTimeUtils;
import com.nongxinle.utils.GbDepFoodSalesMetricsSupport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 日营业额列表页：主账（gb_ai_daily_revenue）+ 菜品五类销售（gb_dep_food_sales）合并输出。
 */
public final class GbAiDailyRevenueListSupport {

    private GbAiDailyRevenueListSupport() {
    }

    public static Map<String, Object> buildPayload(
            Long depFatherId,
            Long subDepId,
            Long distributerId,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, GbAiDailyRevenueEntity> revenueByDate,
            Map<String, List<GbDepFoodSalesEntity>> foodSalesByDate) {

        TreeSet<String> dateKeys = new TreeSet<>();
        if (startDate != null && endDate != null && !endDate.isBefore(startDate)) {
            for (LocalDate d : GbDateTimeUtils.inclusiveLocalDates(startDate, endDate)) {
                dateKeys.add(GbDateTimeUtils.formatDay(d));
            }
        }
        if (revenueByDate != null) {
            dateKeys.addAll(revenueByDate.keySet());
        }
        if (foodSalesByDate != null) {
            dateKeys.addAll(foodSalesByDate.keySet());
        }
        if (dateKeys.isEmpty()) {
            return null;
        }

        String resolvedStart = startDate != null ? GbDateTimeUtils.formatDay(startDate) : dateKeys.iterator().next();
        String resolvedEnd = endDate != null ? GbDateTimeUtils.formatDay(endDate) : dateKeys.stream().reduce((a, b) -> b).orElse(resolvedStart);

        List<Map<String, Object>> dailyRows = new ArrayList<>();
        DailyRevenueListAccumulator summaryAcc = new DailyRevenueListAccumulator();
        for (String dateKey : dateKeys) {
            GbAiDailyRevenueEntity rev = revenueByDate == null ? null : revenueByDate.get(dateKey);
            List<GbDepFoodSalesEntity> foodRows = foodSalesByDate == null ? null : foodSalesByDate.get(dateKey);
            FoodSalesTypeDayRollup food = FoodSalesTypeDayRollup.fromRows(foodRows);
            Map<String, Object> row = buildDailyRow(dateKey, rev, food);
            dailyRows.add(row);
            summaryAcc.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("startDate", resolvedStart);
        data.put("endDate", resolvedEnd);
        if (subDepId != null) {
            data.put("subDepId", subDepId);
        }
        if (distributerId != null) {
            data.put("distributerId", distributerId);
        }
        data.put("depFatherId", depFatherId);
        data.put("summary", summaryAcc.toSummaryMap());
        data.put("dailyRows", dailyRows);
        return data;
    }

    public static Map<String, GbAiDailyRevenueEntity> indexRevenueByDate(
            List<GbAiDailyRevenueEntity> aggregatedByDay) {
        Map<String, GbAiDailyRevenueEntity> out = new LinkedHashMap<>();
        if (aggregatedByDay == null) {
            return out;
        }
        for (GbAiDailyRevenueEntity row : aggregatedByDay) {
            if (row == null || row.getGbAiDailyRevenueRecordDate() == null) {
                continue;
            }
            String dk = GbDateTimeUtils.formatDay(row.getGbAiDailyRevenueRecordDate());
            if (dk != null) {
                out.put(dk, row);
            }
        }
        return out;
    }

    public static Map<String, List<GbDepFoodSalesEntity>> groupFoodSalesByDate(
            Collection<GbDepFoodSalesEntity> rows) {
        Map<String, List<GbDepFoodSalesEntity>> out = new LinkedHashMap<>();
        if (rows == null) {
            return out;
        }
        for (GbDepFoodSalesEntity row : rows) {
            if (row == null || row.getGbDfsFullDate() == null || row.getGbDfsFullDate().trim().isEmpty()) {
                continue;
            }
            String dk = row.getGbDfsFullDate().trim();
            out.computeIfAbsent(dk, k -> new ArrayList<>()).add(row);
        }
        return out;
    }

    private static Map<String, Object> buildDailyRow(String dateKey, GbAiDailyRevenueEntity rev,
            FoodSalesTypeDayRollup food) {
        Map<String, Object> m = new LinkedHashMap<>();
        Date recordDate = GbDateTimeUtils.parseDay(dateKey);
        m.put("date", dateKey);
        m.put("weekLabel", GbDateTimeUtils.chineseWeekdayShort(recordDate));

        BigDecimal dineIn = rev != null && rev.getGbAiDailyRevenueDineInRevenue() != null
                ? rev.getGbAiDailyRevenueDineInRevenue() : BigDecimal.ZERO;
        BigDecimal takeout = rev != null && rev.getGbAiDailyRevenueTakeoutRevenue() != null
                ? rev.getGbAiDailyRevenueTakeoutRevenue() : BigDecimal.ZERO;
        BigDecimal platform = rev != null && rev.getGbAiDailyRevenuePlatformFee() != null
                ? rev.getGbAiDailyRevenuePlatformFee() : BigDecimal.ZERO;
        int dineInOrders = rev != null && rev.getGbAiDailyRevenueDineInOrders() != null
                ? rev.getGbAiDailyRevenueDineInOrders() : 0;
        int takeoutOrders = rev != null && rev.getGbAiDailyRevenueTakeoutOrders() != null
                ? rev.getGbAiDailyRevenueTakeoutOrders() : 0;

        BigDecimal totalRevenue = dineIn.add(takeout);
        BigDecimal netReceived = totalRevenue.subtract(platform);

        m.put("totalRevenueAmount", moneyPlain(totalRevenue));
        m.put("netReceivedAmount", moneyPlain(netReceived));
        m.put("orderCount", dineInOrders + takeoutOrders);
        m.put("dineInRevenueAmount", moneyPlain(dineIn));
        m.put("takeoutRevenueAmount", moneyPlain(takeout));
        m.put("platformFeeAmount", moneyPlain(platform));

        m.put("operatingSalesPortions", qtyPlain(food.operatingSalesPortions()));
        m.put("totalConsumptionPortions", qtyPlain(food.totalConsumptionPortions()));
        m.put("normalPortions", qtyPlain(food.normalPortions));
        m.put("normalSalesAmount", moneyPlain(food.normalSalesAmount));
        m.put("discountPortions", qtyPlain(food.discountPortions));
        m.put("discountSalesAmount", moneyPlain(food.discountSalesAmount));
        m.put("discountConcessionAmount", moneyPlain(food.discountConcessionAmount));
        m.put("memberPortions", qtyPlain(food.memberPortions));
        m.put("memberSalesAmount", moneyPlain(food.memberSalesAmount));
        m.put("memberConcessionAmount", moneyPlain(food.memberConcessionAmount));
        m.put("complimentaryPortions", qtyPlain(food.complimentaryPortions));
        m.put("complimentaryOriginalValue", moneyPlain(food.complimentaryOriginalValue));
        m.put("employeeMealPortions", qtyPlain(food.employeeMealPortions));
        m.put("employeeMealCostAmount", null);

        m.put("dishOperatingSalesAmount", moneyPlain(food.operatingSalesAmount));
        m.put("revenueLedgerDineInAmount", moneyPlain(dineIn));
        m.put("reconciliationDifferenceAmount", moneyPlain(dineIn.subtract(food.operatingSalesAmount)));
        return m;
    }

    static final class FoodSalesTypeDayRollup {
        BigDecimal normalPortions = BigDecimal.ZERO;
        BigDecimal discountPortions = BigDecimal.ZERO;
        BigDecimal memberPortions = BigDecimal.ZERO;
        BigDecimal complimentaryPortions = BigDecimal.ZERO;
        BigDecimal employeeMealPortions = BigDecimal.ZERO;
        BigDecimal normalSalesAmount = BigDecimal.ZERO;
        BigDecimal discountSalesAmount = BigDecimal.ZERO;
        BigDecimal discountConcessionAmount = BigDecimal.ZERO;
        BigDecimal memberSalesAmount = BigDecimal.ZERO;
        BigDecimal memberConcessionAmount = BigDecimal.ZERO;
        BigDecimal complimentaryOriginalValue = BigDecimal.ZERO;
        BigDecimal operatingSalesAmount = BigDecimal.ZERO;

        static FoodSalesTypeDayRollup fromRows(List<GbDepFoodSalesEntity> rows) {
            FoodSalesTypeDayRollup r = new FoodSalesTypeDayRollup();
            if (rows == null) {
                return r;
            }
            for (GbDepFoodSalesEntity row : rows) {
                if (row == null) {
                    continue;
                }
                r.normalPortions = r.normalPortions.add(GbDepFoodSalesMetricsSupport.normalSaleQty(row));
                r.discountPortions = r.discountPortions.add(GbDepFoodSalesMetricsSupport.discountSaleQty(row));
                r.memberPortions = r.memberPortions.add(GbDepFoodSalesMetricsSupport.memberSaleQty(row));
                r.complimentaryPortions = r.complimentaryPortions.add(GbDepFoodSalesMetricsSupport.complimentaryQty(row));
                r.employeeMealPortions = r.employeeMealPortions.add(GbDepFoodSalesMetricsSupport.employeeMealQty(row));
                r.normalSalesAmount = r.normalSalesAmount.add(GbDepFoodSalesMetricsSupport.normalSaleRevenue(row));
                r.discountSalesAmount = r.discountSalesAmount.add(GbDepFoodSalesMetricsSupport.discountSaleRevenue(row));
                r.discountConcessionAmount = r.discountConcessionAmount.add(GbDepFoodSalesMetricsSupport.discountConcession(row));
                r.memberSalesAmount = r.memberSalesAmount.add(GbDepFoodSalesMetricsSupport.memberSaleRevenue(row));
                r.memberConcessionAmount = r.memberConcessionAmount.add(GbDepFoodSalesMetricsSupport.memberConcession(row));
                r.complimentaryOriginalValue = r.complimentaryOriginalValue.add(
                        GbDepFoodSalesMetricsSupport.complimentaryOriginalValue(row));
                r.operatingSalesAmount = r.operatingSalesAmount.add(
                        GbDepFoodSalesMetricsSupport.operationalActualRevenue(row));
            }
            return r;
        }

        BigDecimal operatingSalesPortions() {
            return normalPortions.add(discountPortions).add(memberPortions);
        }

        BigDecimal totalConsumptionPortions() {
            return operatingSalesPortions().add(complimentaryPortions).add(employeeMealPortions);
        }
    }

    private static final class DailyRevenueListAccumulator {
        BigDecimal totalRevenueAmount = BigDecimal.ZERO;
        BigDecimal netReceivedAmount = BigDecimal.ZERO;
        int totalOrderCount;
        BigDecimal dineInRevenueAmount = BigDecimal.ZERO;
        BigDecimal takeoutRevenueAmount = BigDecimal.ZERO;
        BigDecimal platformFeeAmount = BigDecimal.ZERO;
        BigDecimal operatingSalesPortions = BigDecimal.ZERO;
        BigDecimal totalConsumptionPortions = BigDecimal.ZERO;
        BigDecimal normalPortions = BigDecimal.ZERO;
        BigDecimal normalSalesAmount = BigDecimal.ZERO;
        BigDecimal discountPortions = BigDecimal.ZERO;
        BigDecimal discountSalesAmount = BigDecimal.ZERO;
        BigDecimal discountConcessionAmount = BigDecimal.ZERO;
        BigDecimal memberPortions = BigDecimal.ZERO;
        BigDecimal memberSalesAmount = BigDecimal.ZERO;
        BigDecimal memberConcessionAmount = BigDecimal.ZERO;
        BigDecimal complimentaryPortions = BigDecimal.ZERO;
        BigDecimal complimentaryOriginalValue = BigDecimal.ZERO;
        BigDecimal employeeMealPortions = BigDecimal.ZERO;
        BigDecimal dishOperatingSalesAmount = BigDecimal.ZERO;
        BigDecimal revenueLedgerDineInAmount = BigDecimal.ZERO;
        BigDecimal reconciliationDifferenceAmount = BigDecimal.ZERO;
        int activeDayCount;

        void add(Map<String, Object> row) {
            if (row == null) {
                return;
            }
            BigDecimal dayTotal = parseMoney(row.get("totalRevenueAmount"));
            BigDecimal dayConsumption = parseQty(row.get("totalConsumptionPortions"));
            if (dayTotal.compareTo(BigDecimal.ZERO) > 0 || dayConsumption.compareTo(BigDecimal.ZERO) > 0) {
                activeDayCount++;
            }
            totalRevenueAmount = totalRevenueAmount.add(dayTotal);
            netReceivedAmount = netReceivedAmount.add(parseMoney(row.get("netReceivedAmount")));
            totalOrderCount += parseInt(row.get("orderCount"));
            dineInRevenueAmount = dineInRevenueAmount.add(parseMoney(row.get("dineInRevenueAmount")));
            takeoutRevenueAmount = takeoutRevenueAmount.add(parseMoney(row.get("takeoutRevenueAmount")));
            platformFeeAmount = platformFeeAmount.add(parseMoney(row.get("platformFeeAmount")));
            operatingSalesPortions = operatingSalesPortions.add(parseQty(row.get("operatingSalesPortions")));
            totalConsumptionPortions = totalConsumptionPortions.add(parseQty(row.get("totalConsumptionPortions")));
            normalPortions = normalPortions.add(parseQty(row.get("normalPortions")));
            normalSalesAmount = normalSalesAmount.add(parseMoney(row.get("normalSalesAmount")));
            discountPortions = discountPortions.add(parseQty(row.get("discountPortions")));
            discountSalesAmount = discountSalesAmount.add(parseMoney(row.get("discountSalesAmount")));
            discountConcessionAmount = discountConcessionAmount.add(parseMoney(row.get("discountConcessionAmount")));
            memberPortions = memberPortions.add(parseQty(row.get("memberPortions")));
            memberSalesAmount = memberSalesAmount.add(parseMoney(row.get("memberSalesAmount")));
            memberConcessionAmount = memberConcessionAmount.add(parseMoney(row.get("memberConcessionAmount")));
            complimentaryPortions = complimentaryPortions.add(parseQty(row.get("complimentaryPortions")));
            complimentaryOriginalValue = complimentaryOriginalValue.add(parseMoney(row.get("complimentaryOriginalValue")));
            employeeMealPortions = employeeMealPortions.add(parseQty(row.get("employeeMealPortions")));
            dishOperatingSalesAmount = dishOperatingSalesAmount.add(parseMoney(row.get("dishOperatingSalesAmount")));
            revenueLedgerDineInAmount = revenueLedgerDineInAmount.add(parseMoney(row.get("revenueLedgerDineInAmount")));
            reconciliationDifferenceAmount = reconciliationDifferenceAmount.add(
                    parseMoney(row.get("reconciliationDifferenceAmount")));
        }

        Map<String, Object> toSummaryMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("totalRevenueAmount", moneyPlain(totalRevenueAmount));
            m.put("netReceivedAmount", moneyPlain(netReceivedAmount));
            m.put("totalOrderCount", totalOrderCount);
            m.put("dineInRevenueAmount", moneyPlain(dineInRevenueAmount));
            m.put("takeoutRevenueAmount", moneyPlain(takeoutRevenueAmount));
            m.put("platformFeeAmount", moneyPlain(platformFeeAmount));
            m.put("operatingSalesPortions", qtyPlain(operatingSalesPortions));
            m.put("totalConsumptionPortions", qtyPlain(totalConsumptionPortions));
            m.put("normalPortions", qtyPlain(normalPortions));
            m.put("normalSalesAmount", moneyPlain(normalSalesAmount));
            m.put("discountPortions", qtyPlain(discountPortions));
            m.put("discountSalesAmount", moneyPlain(discountSalesAmount));
            m.put("discountConcessionAmount", moneyPlain(discountConcessionAmount));
            m.put("memberPortions", qtyPlain(memberPortions));
            m.put("memberSalesAmount", moneyPlain(memberSalesAmount));
            m.put("memberConcessionAmount", moneyPlain(memberConcessionAmount));
            m.put("complimentaryPortions", qtyPlain(complimentaryPortions));
            m.put("complimentaryOriginalValue", moneyPlain(complimentaryOriginalValue));
            m.put("employeeMealPortions", qtyPlain(employeeMealPortions));
            m.put("employeeMealCostAmount", null);
            m.put("dishOperatingSalesAmount", moneyPlain(dishOperatingSalesAmount));
            m.put("revenueLedgerDineInAmount", moneyPlain(revenueLedgerDineInAmount));
            m.put("reconciliationDifferenceAmount", moneyPlain(reconciliationDifferenceAmount));
            m.put("activeDayCount", activeDayCount);
            if (activeDayCount > 0) {
                m.put("averageDailyRevenue", moneyPlain(
                        totalRevenueAmount.divide(BigDecimal.valueOf(activeDayCount), 2, RoundingMode.HALF_UP)));
                m.put("averageDailyOrderCount", BigDecimal.valueOf(totalOrderCount)
                        .divide(BigDecimal.valueOf(activeDayCount), 2, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString());
            } else {
                m.put("averageDailyRevenue", moneyPlain(BigDecimal.ZERO));
                m.put("averageDailyOrderCount", "0");
            }
            return m;
        }
    }

    private static BigDecimal parseMoney(Object raw) {
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        if (raw instanceof BigDecimal) {
            return (BigDecimal) raw;
        }
        try {
            return new BigDecimal(raw.toString().trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal parseQty(Object raw) {
        return parseMoney(raw);
    }

    private static int parseInt(Object raw) {
        if (raw == null) {
            return 0;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String qtyPlain(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        return v.stripTrailingZeros().toPlainString();
    }

    public static String moneyPlain(BigDecimal v) {
        if (v == null) {
            return "0.00";
        }
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
