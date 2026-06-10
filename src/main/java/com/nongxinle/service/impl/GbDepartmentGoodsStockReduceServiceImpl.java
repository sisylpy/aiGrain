package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockReduceEntity;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import com.nongxinle.mapper.GbDepartmentGoodsStockReduceMapper;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDepartmentUserService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.service.NxJrdhSupplierService;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 部门商品库存减少Service实现
 */
@Service
public class GbDepartmentGoodsStockReduceServiceImpl extends ServiceImpl<GbDepartmentGoodsStockReduceMapper, GbDepartmentGoodsStockReduceEntity> implements GbDepartmentGoodsStockReduceService {

    @Autowired
    private GbDepartmentGoodsStockReduceMapper gbDepartmentGoodsStockReduceMapper;
    @Autowired
    private GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    @Autowired
    private GbDistributerPurchaseGoodsService gbDistributerPurchaseGoodsService;
    @Autowired
    private GbDepartmentService gbDepartmentService;
    @Autowired
    private GbDepartmentUserService gbDepartmentUserService;
    @Autowired
    private NxJrdhSupplierService nxJrdhSupplierService;

    /**
     * Controller 历史入参使用 {@code equalType}，Mapper 使用 {@code type}；在统计入口统一转换。
     */
    private static Map<String, Object> paramsForReduceStats(Map<String, Object> map) {
        Map<String, Object> p = new HashMap<>(map);
        Object equalType = p.remove("equalType");
        if (equalType != null && p.get("type") == null) {
            p.put("type", equalType);
        }
        return p;
    }

    /** 按单一 {@code gb_dgsr_type} 汇总时须去掉 {@code types}，避免与 {@code type} 条件冲突。 */
    private static Map<String, Object> paramsForSingleTypeReduceStats(Map<String, Object> map, Integer type) {
        Map<String, Object> p = paramsForReduceStats(map);
        p.remove("types");
        p.put("type", type);
        return p;
    }

    private static Double nzDouble(Double d) {
        return d == null ? 0.0 : d;
    }

    @Override
    public Integer queryReduceTypeCount(Map<String, Object> map) {
        Map<String, Object> p = paramsForReduceStats(map);
        if (p.get("type") != null) {
            p.remove("types");
        }
        return gbDepartmentGoodsStockReduceMapper.queryReduceTypeCount(p);
    }

    @Override
    public List<GbDepartmentEntity> queryReduceDepartment(Map<String, Object> map) {
        List<GbDepartmentEntity> list = gbDepartmentGoodsStockReduceMapper.queryReduceDepartment(paramsForReduceStats(map));
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public List<GbDepartmentGoodsStockReduceEntity> queryStockReduceListByParams(Map<String, Object> map) {
        List<GbDepartmentGoodsStockReduceEntity> list =
                gbDepartmentGoodsStockReduceMapper.queryStockReduceListByParams(paramsForReduceStats(map));
        if (list == null || list.isEmpty()) {
            return list != null ? list : Collections.emptyList();
        }
        for (GbDepartmentGoodsStockReduceEntity r : list) {
            GbDepartmentGoodsStockReduceSupport.applyWxTypeAmountFields(r);
        }
        return list;
    }

    @Override
    public Double queryReduceCostSubtotal(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryReduceCostSubtotal(paramsForReduceStats(map));
    }

    @Override
    public Double queryReduceWeightSum(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryReduceWeightSum(paramsForReduceStats(map));
    }

    @Override
    public Double queryReduceByTypeTotal(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryReduceByTypeTotal(paramsForReduceStats(map));
    }

    @Override
    public Double queryReduceProduceTotal(Map<String, Object> map) {
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceCostSubtotal(
                paramsForSingleTypeReduceStats(map, GbConstants.StockReduceType.PRODUCTION)));
    }

    @Override
    public Double queryReduceProduceWeightTotal(Map<String, Object> map) {
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceWeightSum(
                paramsForSingleTypeReduceStats(map, GbConstants.StockReduceType.PRODUCTION)));
    }

    @Override
    public Double queryReduceLossTotal(Map<String, Object> map) {
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceCostSubtotal(
                paramsForSingleTypeReduceStats(map, GbConstants.StockReduceType.LOSS)));
    }

    @Override
    public Double queryReduceLossWeightTotal(Map<String, Object> map) {
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceWeightSum(
                paramsForSingleTypeReduceStats(map, GbConstants.StockReduceType.LOSS)));
    }

    @Override
    public Double queryReduceWasteTotal(Map<String, Object> map) {
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceCostSubtotal(
                paramsForSingleTypeReduceStats(map, GbConstants.StockReduceType.WASTE)));
    }

    @Override
    public Double queryReduceWasteWeightTotal(Map<String, Object> map) {
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceWeightSum(
                paramsForSingleTypeReduceStats(map, GbConstants.StockReduceType.WASTE)));
    }

    @Override
    public Double queryReduceReturnTotal(Map<String, Object> map) {
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceCostSubtotal(
                paramsForSingleTypeReduceStats(map, GbConstants.StockReduceType.RETURN)));
    }

    @Override
    public Double queryReduceReturnWeightTotal(Map<String, Object> map) {
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceWeightSum(
                paramsForSingleTypeReduceStats(map, GbConstants.StockReduceType.RETURN)));
    }

    @Override
    public Double queryReduceEmployeeMealTotal(Map<String, Object> map) {
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceCostSubtotal(
                paramsForSingleTypeReduceStats(map, GbConstants.StockReduceType.EMPLOYEE_MEAL)));
    }

    @Override
    public Double queryReduceEmployeeMealWeightTotal(Map<String, Object> map) {
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceWeightSum(
                paramsForSingleTypeReduceStats(map, GbConstants.StockReduceType.EMPLOYEE_MEAL)));
    }

    @Override
    public Map<String, Object> queryReduceAllTypesTotal(Map<String, Object> map) {
        Map<String, Object> raw = gbDepartmentGoodsStockReduceMapper.queryReduceAllTypesTotal(paramsForReduceStats(map));
        return GbDepartmentGoodsStockReduceSupport.enrichAllTypesTotalWithEmployeeMeal(
                raw, queryReduceEmployeeMealTotal(map));
    }

    @Override
    public Map<String, Object> queryReduceTypeWeightTotalsByScope(Map<String, Object> map) {
        Map<String, Object> raw = gbDepartmentGoodsStockReduceMapper.queryReduceTypeWeightTotalsByScope(map);
        return GbDepartmentGoodsStockReduceSupport.enrichTypeWeightTotalsWithEmployeeMeal(
                raw, queryReduceEmployeeMealWeightTotal(map));
    }

    @Override
    public Map<String, Object> queryReduceAllTypesTotalForRetailDepartmentFathers(Map<String, Object> map) {
        Map<String, Object> raw =
                gbDepartmentGoodsStockReduceMapper.queryReduceAllTypesTotalForRetailDepartmentFathers(paramsForReduceStats(map));
        return GbDepartmentGoodsStockReduceSupport.enrichAllTypesTotalWithEmployeeMeal(
                raw, queryReduceEmployeeMealTotal(map));
    }

    @Override
    public Map<String, Object> queryReduceAllTypesTotalOnDailyRevenueDays(Map<String, Object> map) {
        Map<String, Object> raw =
                gbDepartmentGoodsStockReduceMapper.queryReduceAllTypesTotalOnDailyRevenueDays(paramsForReduceStats(map));
        return GbDepartmentGoodsStockReduceSupport.enrichAllTypesTotalWithEmployeeMeal(
                raw, queryReduceEmployeeMealTotal(map));
    }

    @Override
    public List<GbDistributerGoodsEntity> queryStockSubtotalTopTimes(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryStockSubtotalTopTimes(map);
    }

    @Override
    public List<Map<String, Object>> queryStockOutboundTimesTopForRetailFathers(Map<String, Object> map) {
        List<Map<String, Object>> list = gbDepartmentGoodsStockReduceMapper.queryStockOutboundTimesTopForRetailFathers(map);
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> queryGbPurchaseGoodsTopDay(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryGbPurchaseGoodsTopDay(map);
    }

    @Override
    public Integer queryReduceDistinctGoodsCount(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryReduceDistinctGoodsCount(map);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryGoodsCostGoodsPageByReduce(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryGoodsCostGoodsPageByReduce(map);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryGoodsCostGoodsPageWithDetails(Map<String, Object> map) {
        List<GbDistributerGoodsEntity> page = queryGoodsCostGoodsPageByReduce(map);
        if (page == null || page.isEmpty()) {
            return page;
        }
        List<Integer> goodsIds = page.stream()
                .map(GbDistributerGoodsEntity::getGbDistributerGoodsId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (goodsIds.isEmpty()) {
            return page;
        }

        Map<String, Object> detailParams = new HashMap<>();
        detailParams.put("disGoodsIds", goodsIds);
        detailParams.put("disId", map.get("disId"));
        detailParams.put("depId", map.get("depId"));
        detailParams.put("depType", map.get("depType"));
        detailParams.put("startDate", map.get("startDate"));
        detailParams.put("stopDate", map.get("stopDate"));
        detailParams.put("disGoodsGreatId", map.get("disGoodsGreatId"));
        detailParams.put("reduceTypeFilter", map.get("reduceTypeFilter"));

        List<GbDepartmentGoodsStockReduceEntity> rows = gbDepartmentGoodsStockReduceMapper.queryReduceCostDetailRows(detailParams);
        if (rows == null || rows.isEmpty()) {
            for (GbDistributerGoodsEntity g : page) {
                g.setShowCostList(true);
                g.setWasteDepartmentEntities(Collections.emptyList());
            }
            return page;
        }

        List<Integer> stockIds = rows.stream()
                .map(GbDepartmentGoodsStockReduceEntity::getGbDgsrGbGoodsStockId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, GbDepartmentGoodsStockEntity> stockById = loadStocksByIds(stockIds);
        Map<Integer, GbDistributerPurchaseGoodsEntity> purCache = new HashMap<>();
        Map<Integer, BigDecimal> remainingByReduceId = loadRemainingBeforeOutboundForStockIds(stockIds, stockById);
        attachStockPurchaseAndBatchInfo(rows, stockById, purCache, remainingByReduceId, null);

        Set<Integer> depIds = rows.stream()
                .map(GbDepartmentGoodsStockReduceEntity::getGbDgsrGbDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> depNameById = new HashMap<>();
        for (Integer depId : depIds) {
            GbDepartmentEntity dep = gbDepartmentService.getById(depId);
            if (dep != null) {
                depNameById.put(depId, dep.getGbDepartmentName() != null ? dep.getGbDepartmentName() : "");
            }
        }

        Map<Integer, TreeMap<Integer, List<GbDepartmentGoodsStockReduceEntity>>> byGoods = new LinkedHashMap<>();
        for (GbDepartmentGoodsStockReduceEntity row : rows) {
            Integer gid = row.getGbDgsrGbDisGoodsId();
            Integer depId = row.getGbDgsrGbDepartmentId();
            if (gid == null || depId == null) {
                continue;
            }
            byGoods.computeIfAbsent(gid, k -> new TreeMap<>())
                    .computeIfAbsent(depId, k -> new ArrayList<>())
                    .add(row);
        }

        for (GbDistributerGoodsEntity g : page) {
            g.setShowCostList(true);
            Integer gid = g.getGbDistributerGoodsId();
            TreeMap<Integer, List<GbDepartmentGoodsStockReduceEntity>> depMap = gid == null ? null : byGoods.get(gid);
            if (depMap == null || depMap.isEmpty()) {
                g.setWasteDepartmentEntities(Collections.emptyList());
                continue;
            }
            List<GbDepartmentEntity> deps = new ArrayList<>();
            for (Map.Entry<Integer, List<GbDepartmentGoodsStockReduceEntity>> e : depMap.entrySet()) {
                GbDepartmentEntity d = new GbDepartmentEntity();
                d.setGbDepartmentId(e.getKey());
                d.setGbDepartmentName(depNameById.getOrDefault(e.getKey(), ""));
                d.setWasteReduceList(e.getValue());
                deps.add(d);
            }
            g.setWasteDepartmentEntities(deps);
        }
        return page;
    }

    @Override
    public void enrichReducesWithStockAndPurchaseBatch(List<GbDepartmentGoodsStockReduceEntity> rows, String unit) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (GbDepartmentGoodsStockReduceEntity row : rows) {
            GbDepartmentGoodsStockReduceSupport.applyWxTypeAmountFields(row);
        }
        List<Integer> stockIds = rows.stream()
                .map(GbDepartmentGoodsStockReduceEntity::getGbDgsrGbGoodsStockId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, GbDepartmentGoodsStockEntity> stockById = loadStocksByIds(stockIds);
        Map<Integer, GbDistributerPurchaseGoodsEntity> purCache = new HashMap<>();
        Map<Integer, BigDecimal> remainingByReduceId = loadRemainingBeforeOutboundForStockIds(stockIds, stockById);
        attachStockPurchaseAndBatchInfo(rows, stockById, purCache, remainingByReduceId, unit);
    }

    private Map<Integer, GbDepartmentGoodsStockEntity> loadStocksByIds(List<Integer> stockIds) {
        Map<Integer, GbDepartmentGoodsStockEntity> stockById = new HashMap<>();
        if (stockIds == null || stockIds.isEmpty()) {
            return stockById;
        }
        for (GbDepartmentGoodsStockEntity s : gbDepartmentGoodsStockService.listByIds(stockIds)) {
            if (s != null && s.getGbDepartmentGoodsStockId() != null) {
                stockById.put(s.getGbDepartmentGoodsStockId(), s);
            }
        }
        Set<Integer> purUserIds = stockById.values().stream()
                .map(GbDepartmentGoodsStockEntity::getGbDgsPurUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, GbDepartmentUserEntity> userById = new HashMap<>();
        for (Integer uid : purUserIds) {
            GbDepartmentUserEntity u = gbDepartmentUserService.getById(uid);
            if (u != null) {
                userById.put(uid, u);
            }
        }
        for (GbDepartmentGoodsStockEntity stock : stockById.values()) {
            if (stock.getGbDgsPurUserId() != null) {
                stock.setStockUserEntity(userById.get(stock.getGbDgsPurUserId()));
            }
        }
        return stockById;
    }

    private Map<Integer, BigDecimal> loadRemainingBeforeOutboundForStockIds(List<Integer> stockIds,
            Map<Integer, GbDepartmentGoodsStockEntity> stockById) {
        if (stockIds == null || stockIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<GbDepartmentGoodsStockReduceEntity> allOnStock = list(new LambdaQueryWrapper<GbDepartmentGoodsStockReduceEntity>()
                .in(GbDepartmentGoodsStockReduceEntity::getGbDgsrGbGoodsStockId, stockIds)
                .in(GbDepartmentGoodsStockReduceEntity::getGbDgsrType, GbConstants.StockReduceType.PRODUCTION,
                        GbConstants.StockReduceType.WASTE, GbConstants.StockReduceType.LOSS,
                        GbConstants.StockReduceType.RETURN, GbConstants.StockReduceType.EMPLOYEE_MEAL));
        if (allOnStock == null || allOnStock.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, List<GbDepartmentGoodsStockReduceEntity>> byStock = new HashMap<>();
        for (GbDepartmentGoodsStockReduceEntity row : allOnStock) {
            Integer sid = row.getGbDgsrGbGoodsStockId();
            if (sid == null) {
                continue;
            }
            byStock.computeIfAbsent(sid, k -> new ArrayList<>()).add(row);
        }
        return GbDepartmentGoodsStockReduceSupport.computeRemainingBeforeOutboundByReduceId(byStock, stockById);
    }

    private void attachStockPurchaseAndBatchInfo(List<GbDepartmentGoodsStockReduceEntity> rows,
            Map<Integer, GbDepartmentGoodsStockEntity> stockById,
            Map<Integer, GbDistributerPurchaseGoodsEntity> purCache,
            Map<Integer, BigDecimal> remainingByReduceId,
            String unit) {
        for (GbDepartmentGoodsStockReduceEntity row : rows) {
            Integer sid = row.getGbDgsrGbGoodsStockId();
            GbDepartmentGoodsStockEntity stock = sid != null ? stockById.get(sid) : null;
            GbDistributerPurchaseGoodsEntity pur = resolvePurchaseGoodsForReduce(row, stock, purCache);
            if (stock != null && pur != null) {
                stock.setPurchaseGoodsEntity(pur);
                row.setGbDepartmentGoodsStockEntity(stock);
            } else if (stock != null) {
                row.setGbDepartmentGoodsStockEntity(stock);
            }
            BigDecimal remaining = row.getGbDepartmentGoodsStockReduceId() != null
                    ? remainingByReduceId.get(row.getGbDepartmentGoodsStockReduceId())
                    : null;
            row.setPurchaseBatchInfo(GbDepartmentGoodsStockReduceSupport.buildPurchaseBatchInfo(
                    stock, pur, remaining, unit));
        }
    }

    private GbDistributerPurchaseGoodsEntity resolvePurchaseGoodsForReduce(GbDepartmentGoodsStockReduceEntity row,
            GbDepartmentGoodsStockEntity stock,
            Map<Integer, GbDistributerPurchaseGoodsEntity> purCache) {
        Integer purId = null;
        if (stock != null && stock.getGbDgsGbPurGoodsId() != null && stock.getGbDgsGbPurGoodsId() != -1) {
            purId = stock.getGbDgsGbPurGoodsId();
        } else if (row.getGbDgsrGbPurGoodsId() != null && row.getGbDgsrGbPurGoodsId() != -1) {
            purId = row.getGbDgsrGbPurGoodsId();
        }
        if (purId == null) {
            return null;
        }
        return purCache.computeIfAbsent(purId, this::loadPurchaseGoodsWithExtras);
    }

    private GbDistributerPurchaseGoodsEntity loadPurchaseGoodsWithExtras(Integer purId) {
        GbDistributerPurchaseGoodsEntity pur = gbDistributerPurchaseGoodsService.getById(purId);
        if (pur == null) {
            return null;
        }
        Integer supId = pur.getGbDpgPurchaseNxSupplierId();
        if (supId != null && supId != -1) {
            NxJrdhSupplierEntity sup = nxJrdhSupplierService.getById(supId);
            pur.setNxJrdhSupplierEntity(sup);
        }
        Integer purUserId = pur.getGbDpgPurUserId();
        if (purUserId != null) {
            GbDepartmentUserEntity u = gbDepartmentUserService.getById(purUserId);
            pur.setPurchaseDepartmentUser(u);
        }
        return pur;
    }

    @Override
    public List<Map<String, Object>> queryProductionReduceAggByDisGoods(Map<String, Object> map) {
        List<Map<String, Object>> list =
                gbDepartmentGoodsStockReduceMapper.queryProductionReduceAggByDisGoods(paramsForReduceStats(map));
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> queryProduceLossWasteReduceAggByDisGoods(Map<String, Object> map) {
        List<Map<String, Object>> list =
                gbDepartmentGoodsStockReduceMapper.queryProduceLossWasteReduceAggByDisGoods(paramsForReduceStats(map));
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> queryProduceLossWasteReduceAggByDisGoodsOnDailyRevenueDays(Map<String, Object> map) {
        List<Map<String, Object>> list = gbDepartmentGoodsStockReduceMapper
                .queryProduceLossWasteReduceAggByDisGoodsOnDailyRevenueDays(paramsForReduceStats(map));
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> queryProduceLossWasteReduceAggByDisGoodsAndDate(Map<String, Object> map) {
        List<Map<String, Object>> list = gbDepartmentGoodsStockReduceMapper
                .queryProduceLossWasteReduceAggByDisGoodsAndDate(paramsForReduceStats(map));
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> queryReduceAggByDisGoodsByType(Map<String, Object> map, Integer stockReduceType) {
        if (stockReduceType == null) {
            return Collections.emptyList();
        }
        Map<String, Object> p = new HashMap<>(paramsForReduceStats(map));
        p.put("reduceType", stockReduceType);
        List<Map<String, Object>> list = gbDepartmentGoodsStockReduceMapper.queryReduceAggByDisGoodsByType(p);
        return list != null ? list : Collections.emptyList();
    }
}
