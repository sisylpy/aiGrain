package com.nongxinle.service.impl;

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

    private static Double nzDouble(Double d) {
        return d == null ? 0.0 : d;
    }

    @Override
    public Integer queryReduceTypeCount(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryReduceTypeCount(paramsForReduceStats(map));
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
    public Map<String, Object> queryReduceTypeWeightTotalsByScope(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryReduceTypeWeightTotalsByScope(map);
    }

    @Override
    public Double queryReduceByTypeTotal(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryReduceByTypeTotal(paramsForReduceStats(map));
    }

    @Override
    public Double queryReduceProduceTotal(Map<String, Object> map) {
        Map<String, Object> p = paramsForReduceStats(map);
        p.put("type", GbConstants.StockReduceType.PRODUCTION);
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceCostSubtotal(p));
    }

    @Override
    public Double queryReduceProduceWeightTotal(Map<String, Object> map) {
        Map<String, Object> p = paramsForReduceStats(map);
        p.put("type", GbConstants.StockReduceType.PRODUCTION);
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceWeightSum(p));
    }

    @Override
    public Double queryReduceLossTotal(Map<String, Object> map) {
        Map<String, Object> p = paramsForReduceStats(map);
        p.put("type", GbConstants.StockReduceType.LOSS);
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceCostSubtotal(p));
    }

    @Override
    public Double queryReduceLossWeightTotal(Map<String, Object> map) {
        Map<String, Object> p = paramsForReduceStats(map);
        p.put("type", GbConstants.StockReduceType.LOSS);
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceWeightSum(p));
    }

    @Override
    public Double queryReduceWasteTotal(Map<String, Object> map) {
        Map<String, Object> p = paramsForReduceStats(map);
        p.put("type", GbConstants.StockReduceType.WASTE);
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceCostSubtotal(p));
    }

    @Override
    public Double queryReduceWasteWeightTotal(Map<String, Object> map) {
        Map<String, Object> p = paramsForReduceStats(map);
        p.put("type", GbConstants.StockReduceType.WASTE);
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceWeightSum(p));
    }

    @Override
    public Double queryReduceReturnTotal(Map<String, Object> map) {
        Map<String, Object> p = paramsForReduceStats(map);
        p.put("type", GbConstants.StockReduceType.RETURN);
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceCostSubtotal(p));
    }

    @Override
    public Double queryReduceReturnWeightTotal(Map<String, Object> map) {
        Map<String, Object> p = paramsForReduceStats(map);
        p.put("type", GbConstants.StockReduceType.RETURN);
        return nzDouble(gbDepartmentGoodsStockReduceMapper.queryReduceWeightSum(p));
    }

    @Override
    public Map<String, Object> queryReduceAllTypesTotal(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryReduceAllTypesTotal(map);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryStockSubtotalTopTimes(Map<String, Object> map) {
        return gbDepartmentGoodsStockReduceMapper.queryStockSubtotalTopTimes(map);
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
        Map<Integer, GbDepartmentGoodsStockEntity> stockById = new HashMap<>();
        if (!stockIds.isEmpty()) {
            for (GbDepartmentGoodsStockEntity s : gbDepartmentGoodsStockService.listByIds(stockIds)) {
                stockById.put(s.getGbDepartmentGoodsStockId(), s);
            }
        }

        Map<Integer, GbDistributerPurchaseGoodsEntity> purCache = new HashMap<>();
        for (GbDepartmentGoodsStockReduceEntity row : rows) {
            applyWxTypeAmountFields(row);
            Integer sid = row.getGbDgsrGbGoodsStockId();
            if (sid == null) {
                continue;
            }
            GbDepartmentGoodsStockEntity stock = stockById.get(sid);
            if (stock == null) {
                continue;
            }
            Integer purId = stock.getGbDgsGbPurGoodsId();
            if (purId != null && purId != -1) {
                GbDistributerPurchaseGoodsEntity pur = purCache.computeIfAbsent(purId, this::loadPurchaseGoodsWithExtras);
                stock.setPurchaseGoodsEntity(pur);
            }
            row.setGbDepartmentGoodsStockEntity(stock);
        }

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

    private static void applyWxTypeAmountFields(GbDepartmentGoodsStockReduceEntity r) {
        if (r.getGbDgsrType() == null) {
            return;
        }
        String w = r.getGbDgsrWeight() != null ? r.getGbDgsrWeight() : "0";
        String s = r.getGbDgsrSubtotal() != null ? r.getGbDgsrSubtotal() : "0";
        Integer t = r.getGbDgsrType();
        if (Objects.equals(t, GbConstants.StockReduceType.PRODUCTION)) {
            r.setGbDgsrProduceWeight(w);
            r.setGbDgsrProduceSubtotal(s);
        } else if (Objects.equals(t, GbConstants.StockReduceType.WASTE)) {
            r.setGbDgsrWasteWeight(w);
            r.setGbDgsrWasteSubtotal(s);
        } else if (Objects.equals(t, GbConstants.StockReduceType.LOSS)) {
            r.setGbDgsrLossWeight(w);
            r.setGbDgsrLossSubtotal(s);
        } else if (Objects.equals(t, GbConstants.StockReduceType.RETURN)) {
            r.setGbDgsrReturnWeight(w);
            r.setGbDgsrReturnSubtotal(s);
        }
    }

    @Override
    public List<Map<String, Object>> queryProductionReduceAggByDisGoods(Map<String, Object> map) {
        List<Map<String, Object>> list =
                gbDepartmentGoodsStockReduceMapper.queryProductionReduceAggByDisGoods(paramsForReduceStats(map));
        return list != null ? list : Collections.emptyList();
    }
}
