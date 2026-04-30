package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.dto.GbDepReorderAuxHint;
import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDepartmentDisGoodsService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDepartmentReorderReminderService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.utils.GbConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.nongxinle.utils.DateUtils.formatWhatDay;

/**
 * 订货习惯优先：到货日期间隔 + 习惯订货重量；辅以库存偏多 / 损耗与废弃偏多提示。
 */
@Service
public class GbDepartmentReorderReminderServiceImpl implements GbDepartmentReorderReminderService {

    private static final Logger log = LoggerFactory.getLogger(GbDepartmentReorderReminderServiceImpl.class);

    private static final int DEFAULT_WINDOW_DAYS = 56;
    private static final int DEFAULT_MIN_TIMES = 2;
    /** 剩余库存大于「习惯单次订货量 × 因子」时提示可适当少订 */
    private static final BigDecimal HIGH_STOCK_FACTOR = new BigDecimal("2.5");
    /** （损耗+废弃重量）/（生产+损耗+废弃）高于该比例且绝对值足够时提示 */
    private static final BigDecimal HIGH_LOSS_WASTE_RATIO = new BigDecimal("0.22");
    private static final BigDecimal MIN_LOSS_WASTE_WEIGHT = new BigDecimal("1.5");
    /** 库存按参考日均可支撑天数 ≥ 该阈值时，不作为提醒商品（库存相对日均用量过剩） */
    private static final BigDecimal ABUNDANT_COVER_MIN_BASE_DAYS = new BigDecimal("8");
    /** 与习惯订货间隔（天）联动：所需覆盖天数不低于「间隔 × 该倍数」再与 BASE 取较大值 */
    private static final int ABUNDANT_COVER_INTERVAL_MULTIPLIER = 4;
    /** 按 reduce 推算「建议补货缺口」时使用的覆盖天数（生产为主线；含损耗/废弃为补充口径） */
    private static final BigDecimal ORDER_COVER_DAYS = new BigDecimal("3");
    private static final BigDecimal REDUCE_DAILY_EPS = new BigDecimal("0.0001");

    @Autowired
    private GbDepartmentOrdersService gbDepartmentOrdersService;
    @Autowired
    private GbDepartmentDisGoodsService gbDepartmentDisGoodsService;
    @Autowired
    private GbDistributerGoodsService gbDistributerGoodsService;
    @Autowired
    private GbDepartmentGoodsStockReduceService gbDepartmentGoodsStockReduceService;
    @Autowired
    private GbDepartmentGoodsStockService gbDepartmentGoodsStockService;

    @Override
    public Map<String, Object> depReorderReminderPage(Integer depId, Integer page, Integer limit,
            Integer windowDays, Integer minTimes) {
        int wd = windowDays != null && windowDays > 0 ? windowDays : DEFAULT_WINDOW_DAYS;
        int mt = minTimes != null && minTimes > 0 ? minTimes : DEFAULT_MIN_TIMES;
        int p = page != null && page > 0 ? page : 1;
        int lim = limit != null && limit > 0 ? limit : 20;

        String stopDate = formatWhatDay(0);
        String startDate = formatWhatDay(-(wd - 1));

        log.info("[reorderReminder] start depId={} page={} limit={} windowDays={} minTimes={} arriveDate=[{},{}] filterOrders=equalStatus RECEIVED({})",
                depId, p, lim, wd, mt, startDate, stopDate, GbConstants.DepartmentOrderStatus.RECEIVED);

        Map<String, Object> candParams = new HashMap<>();
        candParams.put("depId", depId);
        candParams.put("startDate", startDate);
        candParams.put("stopDate", stopDate);
        candParams.put("minTimes", mt);
        candParams.put("equalStatus", GbConstants.DepartmentOrderStatus.RECEIVED);

        Integer totalObj = gbDepartmentOrdersService.countDepGoodsReorderCandidates(candParams);
        int total = totalObj == null ? 0 : totalObj;

        candParams.put("limit", lim);
        candParams.put("offset", (p - 1) * lim);
        List<Integer> depGoodsIds = gbDepartmentOrdersService.selectDepGoodsReorderCandidatesPage(candParams);

        log.info("[reorderReminder] candidateSql totalCount={} pageOffset={} pageDepGoodsIds={}",
                total, (p - 1) * lim, depGoodsIds);

        Set<Integer> orderingDepGoodsIds = loadOrderingDepGoodsIds(depId);

        log.info("[reorderReminder] skipDepGoodsWithOrderInProgress count={} ids={}",
                orderingDepGoodsIds.size(), orderingDepGoodsIds);

        List<GbDepartmentDisGoodsEntity> list = new ArrayList<>();
        Set<Integer> pageCandidateIds = new HashSet<>();
        if (depGoodsIds != null) {
            for (Integer id : depGoodsIds) {
                if (id != null) {
                    pageCandidateIds.add(id);
                }
            }
        }
        if (depGoodsIds != null) {
            for (Integer depGoodsId : depGoodsIds) {
                if (depGoodsId == null) {
                    continue;
                }
                if (orderingDepGoodsIds.contains(depGoodsId)) {
                    log.info("[reorderReminder] skip depGoodsId={} reason=has_unfinished_order(status<RECEIVED)", depGoodsId);
                    continue;
                }
                GbDepartmentDisGoodsEntity row = loadDepGoodsRow(depId, depGoodsId);
                if (row == null) {
                    log.info("[reorderReminder] skip depGoodsId={} reason=dep_goods_row_absent_or_dep_mismatch", depGoodsId);
                    continue;
                }
                applyStockWeightFromBatches(row);
                enrichAiReturnWindowOrderCount(row, depId, startDate, stopDate);
                if (!"true".equals(row.getAiShouldRemindToday())) {
                    log.info("[reorderReminder] omit list depGoodsId={} reason=not_should_remind_today "
                                    + "(habit_interval_not_due_or_single_low_stock_false)",
                            depGoodsId);
                    continue;
                }
                if (omitReminderDueToAbundantStockVsDaily(row)) {
                    log.info("[reorderReminder] omit list depGoodsId={} reason=abundant_cover_vs_refDaily", depGoodsId);
                    continue;
                }
                list.add(row);
            }
        }

        if (p == 1 && list.size() < lim) {
            Map<String, Object> singleMap = new HashMap<>();
            singleMap.put("depId", depId);
            singleMap.put("startDate", startDate);
            singleMap.put("stopDate", stopDate);
            singleMap.put("equalStatus", GbConstants.DepartmentOrderStatus.RECEIVED);
            List<Integer> singleOnlyIds = gbDepartmentOrdersService.selectDepGoodsIdsSingleOrderInWindow(singleMap);
            if (singleOnlyIds != null) {
                for (Integer sid : singleOnlyIds) {
                    if (sid == null || pageCandidateIds.contains(sid) || orderingDepGoodsIds.contains(sid)) {
                        continue;
                    }
                    GbDepartmentDisGoodsEntity row = loadDepGoodsRow(depId, sid);
                    if (row == null) {
                        continue;
                    }
                    applyStockWeightFromBatches(row);
                    enrichAiReturnWindowOrderCount(row, depId, startDate, stopDate);
                    if (!"true".equals(row.getAiRemindLowStockBelowTwoDayUsage())) {
                        continue;
                    }
                    if (!"true".equals(row.getAiShouldRemindToday())) {
                        continue;
                    }
                    if (omitReminderDueToAbundantStockVsDaily(row)) {
                        log.info("[reorderReminder] omit supplement depGoodsId={} reason=abundant_cover_vs_refDaily", sid);
                        continue;
                    }
                    list.add(row);
                    if (list.size() >= lim) {
                        break;
                    }
                }
            }
        }

        log.info("[reorderReminder] done depId={} pageReturned={} pageCandidateIds={}", depId, list.size(), pageCandidateIds.size());

        Map<String, Object> pageMap = new HashMap<>();
        pageMap.put("totalCount", total);
        pageMap.put("pageSize", lim);
        pageMap.put("totalPage", lim > 0 ? (total + lim - 1) / lim : 0);
        pageMap.put("currPage", p);
        pageMap.put("list", list);
        pageMap.put("windowDays", wd);
        pageMap.put("minTimes", mt);

        Map<String, Object> root = new HashMap<>();
        root.put("page", pageMap);
        return root;
    }

    @Override
    public String buildAiReorderHabitFactsMarkdown(Integer depId, Integer windowDays, Integer minTimes, Integer maxItems) {
        if (depId == null) {
            return "";
        }
        int wd = windowDays != null && windowDays > 0 ? windowDays : DEFAULT_WINDOW_DAYS;
        int mt = minTimes != null && minTimes > 0 ? minTimes : DEFAULT_MIN_TIMES;
        int cap = maxItems != null && maxItems > 0 ? maxItems : 25;

        String stopDate = formatWhatDay(0);
        String startDate = formatWhatDay(-(wd - 1));

        StringBuilder sb = new StringBuilder();
        sb.append("【订货/到货频率与习惯】（算法与接口 depReorderReminderPage 同源）\n");
        sb.append("- **数据源**：表 **gb_department_orders**，到货日 **gb_DO_arrive_date**，状态 **gb_DO_status=")
                .append(GbConstants.DepartmentOrderStatus.RECEIVED)
                .append("**（GbConstants.DepartmentOrderStatus.RECEIVED，收货完成）；按部门商品 **gb_DO_dep_dis_goods_id** 聚合。\n");
        sb.append("- **勿与** `gb_distributer_purchase_goods`（批发商采购入库行、`gb_DPG_stock_finish_date`）混为一谈：前者是**部门订货订单到货节奏**，后者是**采购入库**；谈「**采购频率** / 订货频率 / 多久订一次 / 补货习惯」**必须以本块为准**，**禁止**仅用【本月采购数据】入库笔数回答「频率好不好」。\n");
        sb.append("- **供货商表述**：回答「频率」话题时**禁止**顺带下结论「没有供货商配送、全部自采」；是否与供货商有关须单独看【本月采购数据】中的 **供货属性摘要**、**全批发商入库供货维度**及各行的 `gb_DPG_purchase_nx_supplier_id`。\n");
        sb.append("- 回溯窗口：最近 ").append(wd).append(" 天（").append(startDate).append(" ～ ").append(stopDate)
                .append("）；候选：窗口内已收货订单数 ≥ ").append(mt).append("；下列按订单次数降序最多 ").append(cap).append(" 个品（不含途中有未完成订单而 API 会跳过的品）。\n");

        Map<String, Object> candParams = new HashMap<>();
        candParams.put("depId", depId);
        candParams.put("startDate", startDate);
        candParams.put("stopDate", stopDate);
        candParams.put("minTimes", mt);
        candParams.put("equalStatus", GbConstants.DepartmentOrderStatus.RECEIVED);
        candParams.put("limit", cap);
        candParams.put("offset", 0);

        List<Integer> depGoodsIds = gbDepartmentOrdersService.selectDepGoodsReorderCandidatesPage(candParams);
        Set<Integer> orderingDepGoodsIds = loadOrderingDepGoodsIds(depId);

        int outLines = 0;
        if (depGoodsIds != null) {
            for (Integer depGoodsId : depGoodsIds) {
                if (depGoodsId == null) {
                    continue;
                }
                if (orderingDepGoodsIds.contains(depGoodsId)) {
                    continue;
                }
                GbDepartmentDisGoodsEntity row = loadDepGoodsRow(depId, depGoodsId);
                if (row == null) {
                    continue;
                }
                applyStockWeightFromBatches(row);
                int windowCnt = enrichAiReturnWindowOrderCount(row, depId, startDate, stopDate);

                outLines++;
                String name = row.getGbDdgDepGoodsName();
                if (name == null || name.isEmpty()) {
                    name = "未命名";
                }
                sb.append("  ").append(outLines).append(". ").append(name)
                        .append("（gb_department_dis_goods_id=").append(depGoodsId).append("）")
                        .append("；窗口内已收货 ").append(windowCnt).append(" 笔");
                appendAiDailyUsageSnippet(sb, row);
                sb.append("；推算下次习惯订货日 ").append(emptyDash(row.getAiNextHabitOrderDate()));
                sb.append("；当前批次汇总库存 ").append(emptyDash(row.getGbDdgStockTotalWeight()))
                        .append(" ").append(emptyDash(row.getGbDdgOrderStandard()));
                sb.append("；今日是否建议关注订货 ").append(emptyDash(row.getAiShouldRemindToday()));
                appendAiAuxHintSnippet(sb, row);
                sb.append("\n");
            }
        }

        if (outLines == 0) {
            sb.append("- （本块无明细列表）常见原因：① 窗口内没有任何部门商品满足「已收货订单 ≥ ").append(mt)
                    .append(" 笔」；② 候选均被「尚有未完成订单」跳过；③ 门店主要从 **gb_distributer_purchase_goods** 入库，**gb_department_orders** 到货记录少或未使用。\n");
            sb.append("- **模型请注意**：无明细 **≠** 「未采购」「每次都是临时采购」。谈「采购/订货频率」请改用【本月采购数据】按 **gb_DPG_stock_finish_date** 的入库笔数/日到货分布；**禁止**在此处无数据时编造节奏结论，也 **禁止**结合采购块断言「全部为自采、没有供货商配送」。\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String emptyDash(String s) {
        return (s == null || s.isEmpty()) ? "—" : s;
    }

    private static void appendAiDailyUsageSnippet(StringBuilder sb, GbDepartmentDisGoodsEntity row) {
        sb.append("；").append(emptyDash(row.getAiDailyUsage()));
        String iv = row.getAiHabitIntervalDays();
        if (iv != null && !iv.isEmpty()) {
            sb.append("（平均间隔约 ").append(iv).append(" 天）");
        }
        sb.append("；习惯订货量中位数 ").append(emptyDash(row.getAiOrderQuantity()))
                .append(" ").append(emptyDash(row.getAiOrderStandard()));
    }

    private static void appendAiAuxHintSnippet(StringBuilder sb, GbDepartmentDisGoodsEntity row) {
        List<GbDepReorderAuxHint> hints = row.getAiAuxHints();
        if (hints == null || hints.isEmpty()) {
            return;
        }
        int n = Math.min(2, hints.size());
        sb.append("；辅助提示：");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append("；");
            }
            String msg = hints.get(i).getMessage();
            sb.append(msg != null ? msg : hints.get(i).getType());
        }
    }

    private Set<Integer> loadOrderingDepGoodsIds(Integer depId) {
        List<GbDepartmentOrdersEntity> rows = gbDepartmentOrdersService.list(
                new LambdaQueryWrapper<GbDepartmentOrdersEntity>()
                        .eq(GbDepartmentOrdersEntity::getGbDoDepartmentId, depId)
                        .lt(GbDepartmentOrdersEntity::getGbDoStatus, GbConstants.DepartmentOrderStatus.RECEIVED)
                        .isNotNull(GbDepartmentOrdersEntity::getGbDoDepDisGoodsId)
                        .select(GbDepartmentOrdersEntity::getGbDoDepDisGoodsId));
        Set<Integer> set = new HashSet<>();
        for (GbDepartmentOrdersEntity r : rows) {
            if (r.getGbDoDepDisGoodsId() != null) {
                set.add(r.getGbDoDepDisGoodsId());
            }
        }
        return set;
    }

    private GbDepartmentDisGoodsEntity loadDepGoodsRow(Integer depId, Integer depGoodsId) {
        GbDepartmentDisGoodsEntity row = gbDepartmentDisGoodsService.getById(depGoodsId);
        if (row == null) {
            return null;
        }
        if (row.getGbDdgDepartmentId() != null && !Objects.equals(row.getGbDdgDepartmentId(), depId)) {
            log.warn("dep reorder depGoodsId={} belongs to dep {}, expected {}", depGoodsId, row.getGbDdgDepartmentId(), depId);
            return null;
        }
        if (row.getGbDdgDisGoodsId() != null) {
            GbDistributerGoodsEntity dis = gbDistributerGoodsService.queryObject(row.getGbDdgDisGoodsId());
            row.setGbDistributerGoodsEntity(dis);
        }
        log.debug("[reorderReminder] loaded depGoodsId={} depIdMatch={} disGoodsId={} stockWeight={}",
                depGoodsId, row.getGbDdgDepartmentId(), row.getGbDdgDisGoodsId(), row.getGbDdgStockTotalWeight());
        return row;
    }

    /**
     * 本接口展示用库存：按部门商品下所有库存批次 {@link GbDepartmentGoodsStockEntity#getGbDgsRestWeight()} 汇总，
     * 覆盖 {@link GbDepartmentDisGoodsEntity#getGbDdgStockTotalWeight()}（表字段为台账累计，可能与批次不一致或为负）。
     */
    private void applyStockWeightFromBatches(GbDepartmentDisGoodsEntity row) {
        Integer depGoodsId = row.getGbDepartmentDisGoodsId();
        if (depGoodsId == null) {
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("depGoodsId", depGoodsId);
        List<GbDepartmentGoodsStockEntity> stocks = gbDepartmentGoodsStockService.queryGoodsStockByParams(map);
        BigDecimal sum = BigDecimal.ZERO;
        int batchRows = 0;
        if (stocks != null) {
            for (GbDepartmentGoodsStockEntity s : stocks) {
                batchRows++;
                BigDecimal rw = parseBd(s.getGbDgsRestWeight());
                sum = sum.add(rw != null ? rw : BigDecimal.ZERO);
            }
        }
        String summed = sum.setScale(1, RoundingMode.HALF_UP).toPlainString();
        String previous = row.getGbDdgStockTotalWeight();
        row.setGbDdgStockTotalWeight(summed);
        log.info("[reorderReminder] stockFromBatches depGoodsId={} batchRows={} sumGbDgsRestWeight={} gbDdgStockTotalWeight_was={}",
                depGoodsId, batchRows, summed, previous);
    }

    /**
     * @return 窗口内已收货订单笔数（与 enrich 所用列表一致）
     */
    private int enrichAiReturnWindowOrderCount(GbDepartmentDisGoodsEntity row, Integer depId, String startDate, String stopDate) {
        Map<String, Object> q = new HashMap<>();
        q.put("depId", depId);
        q.put("depGoodsId", row.getGbDepartmentDisGoodsId());
        q.put("startDate", startDate);
        q.put("stopDate", stopDate);
        q.put("equalStatus", GbConstants.DepartmentOrderStatus.RECEIVED);

        List<GbDepartmentOrdersEntity> orders = gbDepartmentOrdersService.queryDisOrdersListByParams(q);
        if (orders == null) {
            orders = new ArrayList<>();
        }

        Integer depGoodsPk = row.getGbDepartmentDisGoodsId();
        String goodsName = row.getGbDdgDepGoodsName();

        row.setAiStockEstimateDailyUsage("");
        row.setAiRemindLowStockBelowTwoDayUsage("false");
        row.setAiRemindReason("");

        List<GbDepartmentOrdersEntity> sortedByDate = orders.stream()
                .sorted(Comparator.comparing((GbDepartmentOrdersEntity o) -> parseOrderDate(o.getGbDoArriveDate()),
                        Comparator.nullsLast(LocalDate::compareTo)))
                .collect(Collectors.toList());

        GbDepartmentOrdersEntity lastOrderWithArriveDate = null;
        for (int i = sortedByDate.size() - 1; i >= 0; i--) {
            GbDepartmentOrdersEntity e = sortedByDate.get(i);
            if (parseOrderDate(e.getGbDoArriveDate()) != null) {
                lastOrderWithArriveDate = e;
                break;
            }
        }

        List<LocalDate> distinctDates = sortedByDate.stream()
                .map(o -> parseOrderDate(o.getGbDoArriveDate()))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        double habitQty = medianGbDoWeight(sortedByDate);

        List<Integer> gaps = new ArrayList<>();
        for (int i = 1; i < distinctDates.size(); i++) {
            long g = ChronoUnit.DAYS.between(distinctDates.get(i - 1), distinctDates.get(i));
            if (g > 0) {
                gaps.add((int) g);
            }
        }

        int roundedInterval = 0;
        if (!gaps.isEmpty()) {
            double avgGap = gaps.stream().mapToInt(Integer::intValue).average().orElse(0);
            roundedInterval = (int) Math.round(avgGap);
            if (roundedInterval < 1) {
                roundedInterval = 1;
            }
        } else if (!distinctDates.isEmpty() && orders.size() >= 2) {
            long span = ChronoUnit.DAYS.between(distinctDates.get(0), distinctDates.get(distinctDates.size() - 1));
            if (span > 0 && distinctDates.size() >= 2) {
                roundedInterval = (int) Math.max(1, Math.round((double) span / (distinctDates.size() - 1)));
            }
        }

        LocalDate today = LocalDate.now();
        LocalDate lastDate = distinctDates.isEmpty() ? null : distinctDates.get(distinctDates.size() - 1);
        long daysSinceLast = lastDate == null ? -1 : ChronoUnit.DAYS.between(lastDate, today);

        LocalDate nextHabit = lastDate != null && roundedInterval > 0 ? lastDate.plusDays(roundedInterval) : null;

        boolean habitRemind = false;
        if (lastDate != null && roundedInterval > 0 && daysSinceLast >= 0) {
            habitRemind = daysSinceLast >= roundedInterval;
        }

        boolean remindLowStockBelowTwoDayUsage = false;
        if (orders.size() == 1 && lastOrderWithArriveDate != null && daysSinceLast >= 0) {
            double lastOrderWeight = parseDoubleSafe(lastOrderWithArriveDate.getGbDoWeight());
            BigDecimal stockBd = parseBd(row.getGbDdgStockTotalWeight());
            double stock = stockBd != null ? stockBd.doubleValue() : 0;
            long denomDays = Math.max(1L, daysSinceLast);
            double consumedImplicit = Math.max(0, lastOrderWeight - stock);
            double estDaily = consumedImplicit / (double) denomDays;
            if (estDaily > 1e-6) {
                BigDecimal estDailyBd = BigDecimal.valueOf(estDaily).setScale(4, RoundingMode.HALF_UP);
                row.setAiStockEstimateDailyUsage(estDailyBd.setScale(2, RoundingMode.HALF_UP).toPlainString());
                BigDecimal twoDayNeed = estDailyBd.multiply(BigDecimal.valueOf(2));
                if (stockBd != null && stockBd.compareTo(twoDayNeed) < 0) {
                    remindLowStockBelowTwoDayUsage = true;
                }
            }
        }

        boolean shouldRemind = habitRemind || remindLowStockBelowTwoDayUsage;
        List<String> reasons = new ArrayList<>();
        if (habitRemind) {
            reasons.add("habit");
        }
        if (remindLowStockBelowTwoDayUsage) {
            reasons.add("stock_below_two_day_usage");
        }
        row.setAiRemindLowStockBelowTwoDayUsage(remindLowStockBelowTwoDayUsage ? "true" : "false");
        row.setAiRemindReason(reasons.isEmpty() ? "none" : String.join(",", reasons));

        log.info("[reorderReminder] infer depGoodsId={} name={} disGoodsId={} ordersInWindow={} distinctArrivalDates={} "
                        + "gapsDays={} habitIntervalDays={} lastArriveDate={} daysSinceLastArrive={} nextHabitDate={} "
                        + "habitRemind={} lowStock2DayRemind={} combinedRemind={} habitGbDoWeightMedian={} stockTotalWeight={} "
                        + "stockEstDaily={}",
                depGoodsPk, goodsName, row.getGbDdgDisGoodsId(),
                orders.size(), distinctDates.size(),
                gaps,
                roundedInterval > 0 ? roundedInterval : null,
                lastDate,
                daysSinceLast >= 0 ? daysSinceLast : null,
                nextHabit,
                habitRemind,
                remindLowStockBelowTwoDayUsage,
                shouldRemind,
                habitQty > 0 ? String.format("%.2f", habitQty) : "0",
                row.getGbDdgStockTotalWeight(),
                row.getAiStockEstimateDailyUsage());

        row.setAiHabitIntervalDays(roundedInterval > 0 ? String.valueOf(roundedInterval) : "");
        row.setAiNextHabitOrderDate(nextHabit != null ? nextHabit.toString() : "");
        row.setAiShouldRemindToday(shouldRemind ? "true" : "false");

        String unit = row.getGbDdgOrderStandard() != null ? row.getGbDdgOrderStandard() : "";
        if (habitQty > 0) {
            row.setAiOrderQuantity(String.format("%.2f", habitQty));
        } else {
            row.setAiOrderQuantity("");
        }
        row.setAiOrderStandard(unit);

        if (roundedInterval > 0) {
            row.setAiDailyUsage("约每" + roundedInterval + "天订一次货");
        } else if (orders.size() == 1 && row.getAiStockEstimateDailyUsage() != null
                && !row.getAiStockEstimateDailyUsage().isEmpty()) {
            String u = row.getGbDdgOrderStandard() != null ? row.getGbDdgOrderStandard() : "";
            row.setAiDailyUsage("单次订货：估算日均约 " + row.getAiStockEstimateDailyUsage()
                        + (u.isEmpty() ? "" : " " + u));
        } else {
            row.setAiDailyUsage("");
        }

        row.setAiSafetyStock("-");
        row.setAiReorderPoint("-");
        row.setAiTomorrowNeed("-");
        row.setAiRecentAvgUsage("");
        row.setAiUsageVariation("");
        row.setAiCurrentStock(row.getGbDdgStockTotalWeight());
        row.setAiCurrentStockUnit(unit);

        if (lastDate != null) {
            row.setAiLastOrderDate(lastDate.toString());
            row.setAiDaysSinceLastOrder(String.valueOf(Math.max(0, daysSinceLast)));
        } else {
            row.setAiLastOrderDate("");
            row.setAiDaysSinceLastOrder("");
        }

        if (lastOrderWithArriveDate != null && lastOrderWithArriveDate.getGbDoWeight() != null) {
            row.setAiLastOrderQuantity(lastOrderWithArriveDate.getGbDoWeight());
            row.setAiLastOrderUnit(lastOrderWithArriveDate.getGbDoStandard() != null ? lastOrderWithArriveDate.getGbDoStandard() : unit);
        } else {
            row.setAiLastOrderQuantity(row.getGbDdgOrderQuantity());
            row.setAiLastOrderUnit(row.getGbDdgOrderStandard());
        }

        if (habitQty > 1e-6 && row.getGbDdgStockTotalWeight() != null) {
            try {
                BigDecimal stock = new BigDecimal(row.getGbDdgStockTotalWeight().trim());
                BigDecimal hq = BigDecimal.valueOf(habitQty);
                if (stock.compareTo(BigDecimal.ZERO) >= 0 && hq.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal days = stock.divide(hq, 1, RoundingMode.HALF_UP);
                    row.setAiAvailableDays(days.toPlainString());
                }
            } catch (NumberFormatException ignored) {
                row.setAiAvailableDays("");
            }
        } else {
            row.setAiAvailableDays("");
        }

        List<GbDepReorderAuxHint> hints = new ArrayList<>();

        try {
            BigDecimal stockBd = parseBd(row.getGbDdgStockTotalWeight());
            if (stockBd != null && habitQty > 0) {
                BigDecimal hq = BigDecimal.valueOf(habitQty);
                BigDecimal threshold = hq.multiply(HIGH_STOCK_FACTOR);
                if (stockBd.compareTo(threshold) > 0) {
                    log.info("[reorderReminder] auxHint depGoodsId={} high_stock stock={} habitQty={} thresholdStock={} factor={}",
                            depGoodsPk, stockBd.toPlainString(), hq.toPlainString(), threshold.toPlainString(), HIGH_STOCK_FACTOR);
                    GbDepReorderAuxHint h = new GbDepReorderAuxHint();
                    h.setType("high_stock");
                    h.setMessage("当前库存偏多，可按习惯日期考虑适当少订");
                    hints.add(h);
                }
            }
        } catch (Exception ex) {
            log.debug("stock hint skip depGoods={}", row.getGbDepartmentDisGoodsId(), ex);
        }

        if (row.getGbDdgDisGoodsId() != null) {
            Map<String, Object> rp = new HashMap<>();
            rp.put("depId", depId);
            rp.put("disGoodsId", row.getGbDdgDisGoodsId());
            rp.put("startDate", startDate);
            rp.put("stopDate", stopDate);
            Map<String, Object> totals = gbDepartmentGoodsStockReduceService.queryReduceTypeWeightTotalsByScope(rp);
            applyReduceBasedOrderRecommendation(row, totals, startDate, stopDate, hints);
            if (totals != null) {
                BigDecimal produce = parseBd(totals.get("produceWeight"));
                BigDecimal loss = parseBd(totals.get("lossWeight"));
                BigDecimal waste = parseBd(totals.get("wasteWeight"));
                BigDecimal lw = nz(loss).add(nz(waste));
                BigDecimal denom = nz(produce).add(lw);
                if (denom.compareTo(MIN_LOSS_WASTE_WEIGHT) > 0
                        && lw.compareTo(MIN_LOSS_WASTE_WEIGHT) >= 0) {
                    BigDecimal ratio = lw.divide(denom, 4, RoundingMode.HALF_UP);
                    if (ratio.compareTo(HIGH_LOSS_WASTE_RATIO) > 0) {
                        log.info("[reorderReminder] auxHint depGoodsId={} high_loss_waste produce={} loss={} waste={} "
                                        + "lwSum={} denom={} ratio={} minRatio={}",
                                depGoodsPk,
                                nz(produce).toPlainString(),
                                nz(loss).toPlainString(),
                                nz(waste).toPlainString(),
                                lw.toPlainString(),
                                denom.toPlainString(),
                                ratio.toPlainString(),
                                HIGH_LOSS_WASTE_RATIO);
                        GbDepReorderAuxHint h = new GbDepReorderAuxHint();
                        h.setType("high_loss_waste");
                        h.setMessage("近期损耗与废弃占比偏高，订货时可留意验收与存放");
                        hints.add(h);
                    }
                }
            }
        }

        String hintTypes = hints.isEmpty()
                ? "none"
                : hints.stream().map(GbDepReorderAuxHint::getType).collect(Collectors.joining(","));
        log.info("[reorderReminder] auxHints depGoodsId={} types=[{}]", depGoodsPk, hintTypes);

        row.setAiAuxHints(hints.isEmpty() ? null : hints);
        return orders.size();
    }

    /**
     * 主线：仅用出库类型 {@link GbConstants.StockReduceType#PRODUCTION}(1) 在窗口内的合计 ÷ 窗口天数 → 生产日均，
     * 再按「生产日均 × 覆盖天数 − 当前批次汇总库存」给出建议补货缺口（≥0）。
     * <p>
     * 补充：若存在损耗(2)+损失(3)，另给出「生产+损耗+损失」合计日均下的缺口与更早的线性耗尽日（假定速率恒定，仅供参考）。
     */
    private void applyReduceBasedOrderRecommendation(GbDepartmentDisGoodsEntity row, Map<String, Object> totals,
            String startDate, String stopDate, List<GbDepReorderAuxHint> hints) {
        clearReduceRecommendationFields(row);
        if (totals == null || row.getGbDdgDisGoodsId() == null) {
            return;
        }
        BigDecimal prodW = nz(parseBd(totals.get("produceWeight")));
        BigDecimal lwW = nz(parseBd(totals.get("lossWeight"))).add(nz(parseBd(totals.get("wasteWeight"))));

        long windowDays = inclusiveWindowDays(startDate, stopDate);
        BigDecimal wdBd = BigDecimal.valueOf(windowDays);
        BigDecimal prodDaily = prodW.divide(wdBd, 8, RoundingMode.HALF_UP);
        BigDecimal lwDaily = lwW.divide(wdBd, 8, RoundingMode.HALF_UP);
        BigDecimal allDaily = prodDaily.add(lwDaily);

        row.setAiReduceProductionDailyAvg(weightPlain(prodDaily));
        row.setAiReduceLossWasteDailyAvg(weightPlain(lwDaily));
        row.setAiRecommendCoverDaysUsed(String.valueOf(ORDER_COVER_DAYS.intValue()));

        BigDecimal stockBd = nz(parseBd(row.getGbDdgStockTotalWeight()));

        BigDecimal needProd = prodDaily.multiply(ORDER_COVER_DAYS);
        BigDecimal gapProd = needProd.subtract(stockBd).max(BigDecimal.ZERO);
        row.setAiRecommendGapWeightProductionOnly(weightPlain(gapProd));

        BigDecimal needAll = allDaily.multiply(ORDER_COVER_DAYS);
        BigDecimal gapAll = needAll.subtract(stockBd).max(BigDecimal.ZERO);
        row.setAiRecommendGapWeightWithLossWaste(weightPlain(gapAll));

        LocalDate today = LocalDate.now();
        if (prodDaily.compareTo(REDUCE_DAILY_EPS) > 0) {
            BigDecimal daysRemain = stockBd.divide(prodDaily, 8, RoundingMode.HALF_UP);
            long ceilDays = daysRemain.setScale(0, RoundingMode.UP).longValue();
            row.setAiEstimateDepleteDateProductionOnly(today.plusDays(Math.max(0L, ceilDays)).toString());
        }
        if (allDaily.compareTo(REDUCE_DAILY_EPS) > 0) {
            BigDecimal daysRemain = stockBd.divide(allDaily, 8, RoundingMode.HALF_UP);
            long ceilDays = daysRemain.setScale(0, RoundingMode.UP).longValue();
            row.setAiEstimateDepleteDateWithLossWaste(today.plusDays(Math.max(0L, ceilDays)).toString());
        }

        if (hints != null && lwDaily.compareTo(REDUCE_DAILY_EPS) > 0) {
            GbDepReorderAuxHint h = new GbDepReorderAuxHint();
            h.setType("reduce_supplement");
            h.setMessage("若把近期损耗与废弃计入出库速率，可参考 aiRecommendGapWeightWithLossWaste 与更早的耗尽日");
            hints.add(h);
        }

        log.info("[reorderReminder] reduceAdvice depGoodsId={} windowDays={} prodDaily={} lwDaily={} "
                        + "gapProdCover={} gapAllCover={} depleteProd={} depleteAll={}",
                row.getGbDepartmentDisGoodsId(),
                windowDays,
                prodDaily.toPlainString(),
                lwDaily.toPlainString(),
                gapProd.toPlainString(),
                gapAll.toPlainString(),
                row.getAiEstimateDepleteDateProductionOnly(),
                row.getAiEstimateDepleteDateWithLossWaste());
    }

    private static void clearReduceRecommendationFields(GbDepartmentDisGoodsEntity row) {
        row.setAiReduceProductionDailyAvg("");
        row.setAiReduceLossWasteDailyAvg("");
        row.setAiRecommendGapWeightProductionOnly("");
        row.setAiRecommendGapWeightWithLossWaste("");
        row.setAiEstimateDepleteDateProductionOnly("");
        row.setAiEstimateDepleteDateWithLossWaste("");
        row.setAiRecommendCoverDaysUsed("");
    }

    private static long inclusiveWindowDays(String startDate, String stopDate) {
        LocalDate s = parseOrderDate(startDate);
        LocalDate e = parseOrderDate(stopDate);
        if (s == null || e == null) {
            return DEFAULT_WINDOW_DAYS;
        }
        long diff = ChronoUnit.DAYS.between(s, e) + 1;
        return Math.max(1L, diff);
    }

    private static String weightPlain(BigDecimal v) {
        if (v == null) {
            return "";
        }
        return v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /**
     * 习惯上「每若干天订一次」且单次订货量已知时，用 {@code 习惯单次量/间隔天} 作为参考日均；
     * 否则退回单次订货场景的 {@link GbDepartmentDisGoodsEntity#getAiStockEstimateDailyUsage()}。
     * 若窗口内 reduce 生产出库日均 {@link GbDepartmentDisGoodsEntity#getAiReduceProductionDailyAvg()} 可用，
     * 则与上述参考日均取较大值（避免低估实际消耗导致不该剔除仍剔除）。
     * 若库存可支撑天数 ≥ max(8, 间隔×4) 天（相对合并后的参考日均），则认为库存过剩，从提醒列表剔除。
     */
    private boolean omitReminderDueToAbundantStockVsDaily(GbDepartmentDisGoodsEntity row) {
        BigDecimal stockBd = parseBd(row.getGbDdgStockTotalWeight());
        if (stockBd == null || stockBd.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        int intervalDays = 0;
        try {
            String hs = row.getAiHabitIntervalDays();
            if (hs != null && !hs.trim().isEmpty()) {
                intervalDays = Integer.parseInt(hs.trim());
            }
        } catch (NumberFormatException ignored) {
            intervalDays = 0;
        }
        double habitQty = 0;
        try {
            String aq = row.getAiOrderQuantity();
            if (aq != null && !aq.trim().isEmpty()) {
                habitQty = Double.parseDouble(aq.trim());
            }
        } catch (NumberFormatException ignored) {
            habitQty = 0;
        }
        BigDecimal refDaily = null;
        if (intervalDays > 0 && habitQty > 1e-6) {
            refDaily = BigDecimal.valueOf(habitQty).divide(BigDecimal.valueOf(intervalDays), 6, RoundingMode.HALF_UP);
        }
        if (refDaily == null || refDaily.compareTo(REDUCE_DAILY_EPS) <= 0) {
            String est = row.getAiStockEstimateDailyUsage();
            if (est != null && !est.trim().isEmpty()) {
                refDaily = parseBd(est.trim());
            }
        }
        BigDecimal refReduceProd = parseBd(row.getAiReduceProductionDailyAvg());
        if (refReduceProd != null && refReduceProd.compareTo(REDUCE_DAILY_EPS) > 0) {
            if (refDaily == null || refDaily.compareTo(REDUCE_DAILY_EPS) <= 0) {
                refDaily = refReduceProd;
            } else {
                refDaily = refDaily.max(refReduceProd);
            }
        }
        if (refDaily == null || refDaily.compareTo(REDUCE_DAILY_EPS) <= 0) {
            return false;
        }
        BigDecimal coverDays = stockBd.divide(refDaily, 2, RoundingMode.HALF_UP);
        BigDecimal threshold = ABUNDANT_COVER_MIN_BASE_DAYS;
        if (intervalDays > 0) {
            BigDecimal byInterval = BigDecimal.valueOf((long) intervalDays * ABUNDANT_COVER_INTERVAL_MULTIPLIER);
            threshold = threshold.max(byInterval);
        }
        boolean omit = coverDays.compareTo(threshold) >= 0;
        if (omit) {
            log.info("[reorderReminder] abundantStockFilter depGoodsId={} stock={} refDailyMerged={} refReduceProd={} coverDays={} thresholdCoverDays={}",
                    row.getGbDepartmentDisGoodsId(), stockBd.toPlainString(), refDaily.toPlainString(),
                    refReduceProd != null ? refReduceProd.toPlainString() : "",
                    coverDays.toPlainString(), threshold.toPlainString());
        }
        return omit;
    }

    private static BigDecimal nz(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }

    private static BigDecimal parseBd(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        if (v instanceof Number) {
            return BigDecimal.valueOf(((Number) v).doubleValue());
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static LocalDate parseOrderDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String s = raw.trim();
        if (s.length() >= 10) {
            s = s.substring(0, 10);
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            log.debug("bad order date: {}", raw);
            return null;
        }
    }

    private static double medianGbDoWeight(List<GbDepartmentOrdersEntity> orders) {
        List<Double> ws = new ArrayList<>();
        for (GbDepartmentOrdersEntity o : orders) {
            double w = parseDoubleSafe(o.getGbDoWeight());
            if (w > 0) {
                ws.add(w);
            }
        }
        if (ws.isEmpty()) {
            return 0;
        }
        ws.sort(Double::compareTo);
        int mid = ws.size() / 2;
        if (ws.size() % 2 == 0) {
            return (ws.get(mid - 1) + ws.get(mid)) / 2.0;
        }
        return ws.get(mid);
    }

    private static double parseDoubleSafe(String s) {
        if (s == null || s.trim().isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
