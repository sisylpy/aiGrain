package com.nongxinle.service.impl;

import com.nongxinle.dao.GbDepartmentGoodsStockDao;
import com.nongxinle.dao.GbDepartmentOrdersDao;
import com.nongxinle.dao.GbDistributerGoodsDao;
import com.nongxinle.entity.*;
import com.nongxinle.service.GbDistributerStandardService;
import com.nongxinle.utils.PageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import com.nongxinle.dao.GbDepartmentDisGoodsDao;
import com.nongxinle.service.GbDepartmentDisGoodsService;

import static com.nongxinle.utils.DateUtils.formatWhatDay;


@Service("gbDepartmentDisGoodsService")
public class GbDepartmentDisGoodsServiceImpl implements GbDepartmentDisGoodsService {
    @Autowired
    private GbDepartmentDisGoodsDao gbDepartmentDisGoodsDao;
    @Autowired
    private GbDepartmentOrdersDao gbDepartmentOrdersDao;
    @Autowired
    private GbDistributerGoodsDao gbDistributerGoodsDao;
    @Autowired
    private GbDistributerStandardService gbDistributerStandardService;

    @Override
    public GbDepartmentDisGoodsEntity queryObject(Integer gbDepartmentDisGoodsId) {
        return gbDepartmentDisGoodsDao.queryObject(gbDepartmentDisGoodsId);
    }

    @Override
    public List<GbDepartmentDisGoodsEntity> queryList(Map<String, Object> map) {
        return gbDepartmentDisGoodsDao.queryList(map);
    }

    @Override
    public int queryTotal(Map<String, Object> map) {
        return gbDepartmentDisGoodsDao.queryTotal(map);
    }

    @Override
    public void save(GbDepartmentDisGoodsEntity gbDepartmentDisGoods) {
        gbDepartmentDisGoodsDao.save(gbDepartmentDisGoods);
    }

    @Override
    public void update(GbDepartmentDisGoodsEntity gbDepartmentDisGoods) {
        gbDepartmentDisGoodsDao.update(gbDepartmentDisGoods);
    }

    @Override
    public void delete(Integer gbDepartmentDisGoodsId) {
        gbDepartmentDisGoodsDao.delete(gbDepartmentDisGoodsId);
    }

    @Override
    public void deleteBatch(Integer[] gbDepartmentDisGoodsIds) {
        gbDepartmentDisGoodsDao.deleteBatch(gbDepartmentDisGoodsIds);
    }

    @Override
    public List<GbDepartmentDisGoodsEntity> queryGbDepDisGoodsByParams(Map<String, Object> map) {

        return gbDepartmentDisGoodsDao.queryGbDepDisGoodsByParams(map);
    }


    @Override
    public List<GbDistributerFatherGoodsEntity> depQueryDepGoodsWithOrderGb(Map<String, Object> map) {
        return gbDepartmentDisGoodsDao.depQueryDepGoodsWithOrderGb(map);
    }

    @Override
    public List<GbDepartmentDisGoodsEntity> depGetDepsGoodsGb(Map<String, Object> map) {
        return gbDepartmentDisGoodsDao.depGetDepsGoodsGb(map);
    }

    @Override
    public int queryGbDisGoodsTotal(Map<String, Object> map3) {

        return gbDepartmentDisGoodsDao.queryGbDisGoodsTotal(map3);
    }

    @Override
    public TreeSet<GbDepartmentDisGoodsEntity> queryDepDisGoodsQuickSearchStrGb(Map<String, Object> map1) {
        return gbDepartmentDisGoodsDao.queryDepDisGoodsQuickSearchStrGb(map1);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> disGetDepDisGoodsCataGb(Map<String, Object> map) {

        return gbDepartmentDisGoodsDao.disGetDepDisGoodsCataGb(map);
    }

    @Override
    public GbDepartmentDisGoodsEntity queryDepGoodsItemByParams(Map<String, Object> map1) {

        return gbDepartmentDisGoodsDao.queryDepGoodsItemByParams(map1);
    }

    @Override
    public List<GbDepartmentDisGoodsEntity> depQueryDepGoodsWithOrderDepGoods(Map<String, Object> map) {

        return gbDepartmentDisGoodsDao.depQueryDepGoodsWithOrderDepGoods(map);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> queryDepTypeFatherGoods(Map<String, Object> mapD) {

        return gbDepartmentDisGoodsDao.queryDepTypeFatherGoods(mapD);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> selfMendiainGetDepDisGoodsCata(Map<String, Object> mapD) {

        return gbDepartmentDisGoodsDao.selfMendiainGetDepDisGoodsCata(mapD);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> selfMendiainGetDepDisGoodsCataWithGoods(Map<String, Object> map) {

        return gbDepartmentDisGoodsDao.selfMendiainGetDepDisGoodsCataWithGoods(map);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> queryDepFatherGoodsByParams(Map<String, Object> mapG) {

        return gbDepartmentDisGoodsDao.queryDepFatherGoodsByParams(mapG);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> depQueryDepGoodsWithOrderGbNew(Map<String, Object> map) {
        return gbDepartmentDisGoodsDao.depQueryDepGoodsWithOrderGbNew(map);
    }


    @Override
    public List<GbDepartmentDisGoodsEntity> queryDepDisGoodsByParams(Map<String, Object> map) {

        return gbDepartmentDisGoodsDao.queryDepDisGoodsByParams(map);
    }

    @Override
    public List<GbDepartmentDisGoodsEntity>  queryDepartmentGoods(Map<String, Object> map) {

        return gbDepartmentDisGoodsDao.queryDepartmentGoods(map);
    }

    @Override
    public List<GbDepartmentDisGoodsEntity> depQueryDepGoodsWithOrder(Map<String, Object> map) {

        return gbDepartmentDisGoodsDao.depQueryDepGoodsWithOrder(map);
    }



    @Override
    public int queryDepGoodsCount(Map<String, Object> mapC) {

        return gbDepartmentDisGoodsDao.queryDepGoodsCount(mapC);
    }

    @Override
    public List<Integer> queryOnlyDepGoodsIds(Map<String, Object> map) {

        return gbDepartmentDisGoodsDao.queryOnlyDepGoodsIds(map);
    }

    @Override
    public List<GbDepartmentDisGoodsEntity> depQueryDepGoodsWithOrderForAi(Map<String, Object> map) {

        return gbDepartmentDisGoodsDao.depQueryDepGoodsWithOrderForAi(map);
    }

    private static final Logger log = LoggerFactory.getLogger(GbDepartmentDisGoodsServiceImpl.class);

    @Override
    public PageUtils computeReorder(Integer depId, Integer page, Integer limit) {
        long totalStartTime = System.currentTimeMillis();
        log.info("开始计算部门[{}]的补货建议，页码：{}，每页数量：{}", depId, page, limit);

        // 1. 获取正在下单的商品
        long startTime = System.currentTimeMillis();
        Map<String, Object> mapOrder = new HashMap<>();
        mapOrder.put("depId", depId);
        mapOrder.put("status", 4);
        List<Integer> orderingGoodsIds = gbDepartmentOrdersDao.queryGoodsIds(mapOrder);
        log.info("获取正在下单商品耗时: {}ms", System.currentTimeMillis() - startTime);

        // 2. 参数、常量定义
        int windowDays = 56;       // 统计过去多少天
        int minTimes = 1;          // 至少订货次数阈值4 sisy
        int leadTime = 1;          // 预计提前期（天）
        double serviceZ = 1.65;    // 95% 服务水平，对应正态分布 Z 值
        log.info("计算参数设置: windowDays={}, minTimes={}, leadTime={}, serviceZ={}",
                windowDays, minTimes, leadTime, serviceZ);

        // 3. 获取常订商品总数
        Map<String, Object> freqParams = new HashMap<>();
        freqParams.put("depId", depId);
        freqParams.put("windowDays", windowDays);
        freqParams.put("minTimes", minTimes);
        int total = gbDepartmentOrdersDao.selectFrequentGoodsCount(freqParams);
        log.info("常订商品总数: {}", total);

        // 5. 分页查询，直到获取足够的数据（首屏补齐，其余正常分页）
        List<GbDepartmentDisGoodsEntity> finalResult = new ArrayList<>();
        if (page == 1) {
            int currentOffset = 0;
            int currentPage = 1;
            while (finalResult.size() < limit) {
                freqParams.put("offset", currentOffset);
                freqParams.put("limit", limit);
                log.info("[首屏补齐] 查询参数: depId={}, windowDays={}, minTimes={}, offset={}, limit={}, currentPage={}",
                        depId, windowDays, minTimes, currentOffset, limit, currentPage);
                List<Integer> goodsIds = gbDepartmentOrdersDao.selectFrequentGoodsWithPage(freqParams);
                if (goodsIds == null || goodsIds.isEmpty()) {
                    log.info("[首屏补齐] 没有更多数据，当前offset: {}, currentPage: {}", currentOffset, currentPage);
                    break;
                }
                List<GbDepartmentDisGoodsEntity> pageResult = processGoodsList(goodsIds, orderingGoodsIds, depId, windowDays, leadTime, serviceZ);
                finalResult.addAll(pageResult);
                log.info("[首屏补齐] 本次循环后累计商品数量: {}, 期望limit: {}, 当前currentPage: {}", finalResult.size(), limit, currentPage);
                if (finalResult.size() >= limit) {
                    log.info("[首屏补齐] 已获取足够数据，当前offset: {}, currentPage: {}", currentOffset, currentPage);
                    break;
                }
                currentOffset += limit;
                currentPage++;
            }
            log.info("[首屏补齐] 计算补货建议总耗时: {}ms", System.currentTimeMillis() - totalStartTime);
            log.info("[首屏补齐] 最终返回商品数量: {}, 期望limit: {}, 实际查到的最后一页currentPage: {}", finalResult.size(), limit, currentPage);
            return new PageUtils(finalResult, total, limit, currentPage);
        } else {
            int offset = (page - 1) * limit;
            freqParams.put("offset", offset);
            freqParams.put("limit", limit);
            log.info("[普通分页] 查询参数: depId={}, windowDays={}, minTimes={}, offset={}, limit={}, page={}",
                    depId, windowDays, minTimes, offset, limit, page);
            List<Integer> goodsIds = gbDepartmentOrdersDao.selectFrequentGoodsWithPage(freqParams);
            if (goodsIds != null && !goodsIds.isEmpty()) {
                finalResult.addAll(processGoodsList(goodsIds, orderingGoodsIds, depId, windowDays, leadTime, serviceZ));
            }
            log.info("[普通分页] 计算补货建议总耗时: {}ms", System.currentTimeMillis() - totalStartTime);
            log.info("[普通分页] 最终返回商品数量: {}, 期望limit: {}, 当前页page: {}", finalResult.size(), limit, page);
            return new PageUtils(finalResult, total, limit, page);
        }
    }



    private static final double EPSILON = 1e-6;             // 避免除零
    private static final int MIN_USAGE_RECORDS = 1;          // 至少需要 4 条用量数据4 sisy
    private static final int RECENT_DAYS_FOR_CV = 7;         // 用近 7 天计算波动
    private static final int ARRIVAL_HOUR = 7;               // 次日到货时间(07:00)


    public List<GbDepartmentDisGoodsEntity> processGoodsList(List<Integer> goodsIds,
                                                             List<Integer> orderingGoodsIds,
                                                             Integer depId,
                                                             int windowDays,
                                                             int leadTime,
                                                             double serviceZ) {
        List<GbDepartmentDisGoodsEntity> result = new ArrayList<GbDepartmentDisGoodsEntity>();

        for (Integer gid : goodsIds) {
            /* ---------- 1. 过滤正在下单 ---------- */
            if (orderingGoodsIds != null && orderingGoodsIds.contains(gid)) {
                log.info("SKU[{}] 正在下单，跳过", gid);
                continue;
            }

            /* ---------- 2. 基础信息 ---------- */
            GbDistributerGoodsEntity goods = gbDistributerGoodsDao.queryDisGoodsDetail(gid);
            if (goods == null) {
                log.warn("SKU[{}] 商品信息不存在", gid);
                continue;
            }
            GbDepartmentDisGoodsEntity departmentDisGoodsEntity = queryDepartmentGoods(depId, gid, goods);
            if (departmentDisGoodsEntity == null) {
                continue;
            }

            /* ---------- 3. 历史订单 ---------- */
            List<GbDepartmentOrdersEntity> orderHistory = queryHistory(depId, gid);
            GbDepartmentOrdersEntity lastOrder = queryLastOrder(depId, gid, goods);
            if (lastOrder == null) {
                log.info("SKU[{}] 在保质期内没有订货记录", gid);
                continue;
            }

            /* ---------- 4. 日用量 ---------- */
            Map<String, Object> usageParams = new HashMap<String, Object>();
            usageParams.put("depId", depId);
            usageParams.put("gid", gid);
            usageParams.put("windowDays", windowDays);
            List<DailyUsage> usages = gbDepartmentOrdersDao.selectDailyUsage(usageParams);
            if (usages == null || usages.size() < MIN_USAGE_RECORDS) {
                log.info("SKU[{}] 用量数据不足: {} 条", gid, usages == null ? 0 : usages.size());
                continue;
            }

            /* ---------- 5. 统计 ---------- */
            int n = usages.size();
            List<Double> dailyQs = new ArrayList<Double>();
            for (DailyUsage du : usages) dailyQs.add(du.getQty());

            // 打印窗口内用量明细，帮助排查日均为何偏高/低
            if (log.isDebugEnabled()) {
                log.debug("SKU[{}] 用量窗口({} 天): {}", gid, windowDays, dailyQs);
            }

            double sumQty = 0.0;
            for (Double q : dailyQs) sumQty += q;
            double mu = sumQty / n;

            if (log.isDebugEnabled()) {
                log.debug("SKU[{}] 总量: {}, 样本数: {}, 计算日均 mu: {}", gid, sumQty, n, mu);
            }

            if (mu < EPSILON) {
                log.debug("SKU[{}] mu≈0，跳过", gid);
                continue;
            }

            double varianceSum = 0.0;
            for (Double q : dailyQs) varianceSum += Math.pow(q - mu, 2);
            double sigma = Math.sqrt(varianceSum / n);

            List<Double> recentQs = dailyQs.subList(Math.max(0, n - RECENT_DAYS_FOR_CV), n);
            double recentSum = 0.0;
            for (Double q : recentQs) recentSum += q;
            double recentMu = recentSum / recentQs.size();
            double recentVarianceSum = 0.0;
            for (Double q : recentQs) recentVarianceSum += Math.pow(q - recentMu, 2);
            double recentSigma = Math.sqrt(recentVarianceSum / recentQs.size());
            double cv = Math.max(recentSigma / Math.max(recentMu, EPSILON), 0.1);

            if (log.isDebugEnabled()) {
                log.debug("SKU[{}] recentMu: {}, recentSigma: {}, CV: {}", gid, recentMu, recentSigma, cv);
            }

            /* ---------- 6. 安全库存 ---------- */
            double adjustedZ = adjustServiceZ(serviceZ, cv);
            double safetyStock = adjustedZ * sigma * Math.sqrt(leadTime);
            double reorderPoint = mu * leadTime + safetyStock;

            if (log.isDebugEnabled()) {
                log.debug("SKU[{}] safetyStock: {}, reorderPoint: {}", gid, safetyStock, reorderPoint);
            }

            /* ---------- 7. 库存估算 ---------- */
            LocalDate lastDate = LocalDate.parse(lastOrder.getGbDoApplyDate());
            LocalDate today = LocalDate.now(ZoneId.systemDefault());
            long daysSince = Math.max(0, ChronoUnit.DAYS.between(lastDate, today));

            double remainingToday = estimateRemainingDemand(LocalDateTime.now(), recentMu);
            double lastQty = parseDoubleSafe(lastOrder.getGbDoQuantity());
            double consumed = daysSince * mu;
            double onHand = lastQty - consumed - remainingToday;

            if (log.isDebugEnabled()) {
                log.debug("SKU[{}] daysSince: {}, lastQty: {}, consumed: {}, remainingToday: {}, onHand: {}", gid,
                        daysSince, lastQty, consumed, remainingToday, onHand);
            }

            /* ---------- 8. 订货决策 ---------- */
            double tomorrowNeed = recentMu;
            int orderQty = calculateOptimalOrderQuantity(orderHistory, onHand, reorderPoint, tomorrowNeed);

            if (log.isDebugEnabled()) {
                log.debug("SKU[{}] 建议订货量(orderQty): {}", gid, orderQty);
            }

            /* ---------- 9. 写入字段 ---------- */
            setAiFields(departmentDisGoodsEntity, goods, onHand, mu, recentMu, cv,
                    safetyStock, reorderPoint, lastDate, lastQty, lastOrder,
                    daysSince, tomorrowNeed, orderQty);

            departmentDisGoodsEntity.setGbDistributerGoodsEntity(goods);

            result.add(departmentDisGoodsEntity);
        }
        return result;
    }

    /* =================== 辅助方法 =================== */

    private GbDepartmentDisGoodsEntity queryDepartmentGoods(Integer depId, Integer gid, GbDistributerGoodsEntity goods) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("disGoodsId", gid);
        map.put("depId", depId);
        GbDepartmentDisGoodsEntity dg = gbDepartmentDisGoodsDao.queryDepartmentGoodsOnly(map);
        if (dg == null) log.warn("SKU[{}] 部门商品信息不存在", gid);
        return dg;
    }

    private List<GbDepartmentOrdersEntity> queryHistory(Integer depId, Integer gid) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("depId", depId);
        params.put("disGoodsId", gid);
        params.put("limit", 20);
        return gbDepartmentOrdersDao.queryDisHistoryOrdersByParamsForAi(params);
    }

    private GbDepartmentOrdersEntity queryLastOrder(Integer depId, Integer gid, GbDistributerGoodsEntity goods) {
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("depId", depId);
        p.put("gid", gid);
        if (goods.getGbDgQuantityDays() != null) p.put("dayuDate", formatWhatDay(-goods.getGbDgQuantityDays()));
        return gbDepartmentOrdersDao.selectLastOrder(p);
    }

    private double adjustServiceZ(double baseZ, double cv) {
        if (cv < 0.5) return 1.0;
        if (cv < 1.0) return 1.3;
        return baseZ;
    }

    /**
     * 估计从“现在”到“下次到货”(默认次日 07:00)期间的消费量。
     */
    private double estimateRemainingDemand(LocalDateTime now, double dailyAvg) {
        int nowHour = now.getHour();
        double hoursToArrival = nowHour >= ARRIVAL_HOUR
                ? (24 - nowHour) + ARRIVAL_HOUR
                : ARRIVAL_HOUR - nowHour;
        double ratio = hoursToArrival / 24.0;
        return dailyAvg * ratio;
    }

    private double parseDoubleSafe(String s) {
        try {
            return (s == null || s.trim().isEmpty()) ? 0d : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            log.warn("数值解析失败: {}", s);
            return 0d;
        }
    }

    private int calculateOptimalOrderQuantity(List<GbDepartmentOrdersEntity> orderHistory,
                                              double currentStock,
                                              double reorderPoint,
                                              double tomorrowNeed) {
        if (orderHistory == null || orderHistory.isEmpty()) {
            // 如果没有历史记录，使用基础缺口
            return (int) Math.ceil(reorderPoint - currentStock);
        }

        // 1. 计算历史订货量的统计指标
        List<Double> orderQuantities = orderHistory.stream()
                .map(order -> Double.parseDouble(order.getGbDoQuantity()))
                .collect(Collectors.toList());

        // 计算平均订货量
        double avgOrderQty = orderQuantities.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        // 计算最常订的数量（众数）
        Map<Double, Long> qtyFrequency = orderQuantities.stream()
                .collect(Collectors.groupingBy(qty -> qty, Collectors.counting()));

        double modeOrderQty = qtyFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0.0);

        // 计算中位数订货量
        List<Double> sortedQuantities = new ArrayList<>(orderQuantities);
        Collections.sort(sortedQuantities);
        int mid = sortedQuantities.size() / 2;
        double medianOrderQty = sortedQuantities.size() % 2 == 0 ?
                (sortedQuantities.get(mid - 1) + sortedQuantities.get(mid)) / 2 :
                sortedQuantities.get(mid);

        // 2. 计算基础缺口
        double baseGap = reorderPoint - currentStock;

        // 3. 根据历史订货习惯调整订货量
        int orderQty;
        if (modeOrderQty > 0) {
            // 优先使用最常订的数量
            orderQty = (int) Math.ceil(modeOrderQty);
            log.info("使用最常订数量: {}", orderQty);
        } else if (avgOrderQty > 0) {
            // 如果没有最常订数量，使用平均订货量
            orderQty = (int) Math.ceil(avgOrderQty);
            log.info("使用平均订货量: {}", orderQty);
        } else {
            // 如果没有任何历史记录，使用基础缺口
            orderQty = (int) Math.ceil(baseGap);
            log.info("使用基础缺口: {}", orderQty);
        }

        // 4. 确保订货量至少能覆盖明天用量
        if (orderQty < tomorrowNeed) {
            orderQty = (int) Math.ceil(tomorrowNeed);
            log.info("调整订货量以覆盖明天用量: {}", orderQty);
        }

        // 5. 考虑商品保质期
        if (orderQty > 7 * tomorrowNeed) {  // 如果订货量超过7天用量
            orderQty = (int) Math.ceil(7 * tomorrowNeed);  // 限制在7天用量内
            log.info("考虑保质期限制，调整订货量: {}", orderQty);
        }

        return orderQty;
    }

    // 抽取设置字段的方法，避免代码重复
    private void setAiFields(GbDepartmentDisGoodsEntity entity,
                             GbDistributerGoodsEntity goods,
                             double onHand,
                             double mu,
                             double recentAvg,
                             double cv,
                             double safetyStock,
                             double reorderPoint,
                             LocalDate lastDate,
                             double lastOrderQty,
                             GbDepartmentOrdersEntity lo,
                             long daysSince,
                             double tomorrowNeed,
                             int orderQty) {

//        recentAvg
        entity.setAiCurrentStock(String.format("%.2f", onHand));
        entity.setAiCurrentStockUnit(goods.getGbDgGoodsStandardname());
        entity.setAiDailyUsage(String.format("%.2f", mu));
        entity.setAiRecentAvgUsage(String.format("%.2f", recentAvg));
        entity.setAiUsageVariation(String.format("%.2f", cv));
        entity.setAiSafetyStock(String.format("%.2f", safetyStock));
        entity.setAiReorderPoint(String.format("%.2f", reorderPoint));
        entity.setAiLastOrderDate(lastDate.toString());
        entity.setAiLastOrderQuantity(String.format("%.2f", lastOrderQty));
        entity.setAiLastOrderUnit(lo.getGbDoStandard());
        entity.setAiDaysSinceLastOrder(String.valueOf(daysSince));
        entity.setAiTomorrowNeed(String.format("%.2f", tomorrowNeed));
        entity.setAiAvailableDays(String.format("%.1f", onHand / mu));
        entity.setAiOrderQuantity(String.valueOf(orderQty));
        entity.setAiOrderStandard(entity.getGbDdgOrderStandard());

        List<GbDistributerStandardEntity> nxDistributerStandardEntities = gbDistributerStandardService.queryDisStandardByDisGoodsIdGb(goods.getGbDistributerGoodsId());
        goods.setGbDistributerStandardEntities(nxDistributerStandardEntities);

    }


    @Override
    public TreeSet<GbDistributerGoodsEntity> disQueryDisGoodsWithOrderForAiTree(Map<String, Object> map) {
        return gbDepartmentDisGoodsDao.disQueryDisGoodsWithOrderForAiTree(map);
    }

    @Override
    public List<Integer> queryOnlyDisGoodsIds(Map<String, Object> map) {

        return gbDepartmentDisGoodsDao.queryOnlyDisGoodsIds(map);
    }

    @Override
    public GbDepartmentDisGoodsEntity queryDepartmentGoodsForAi(Map<String, Object> mapD) {

        return gbDepartmentDisGoodsDao.queryDepartmentGoodsForAi(mapD);
    }


}


