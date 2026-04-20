package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import com.nongxinle.mapper.GbDistributerPurchaseGoodsMapper;
import com.nongxinle.service.GbDepartmentDisGoodsService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.nongxinle.utils.DateUtils.formatFullTime;
import static com.nongxinle.utils.DateUtils.formatWhatDay;
import static com.nongxinle.utils.DateUtils.formatWhatMonth;
import static com.nongxinle.utils.DateUtils.formatWhatYear;
import static com.nongxinle.utils.DateUtils.getTimeStamp;
import static com.nongxinle.utils.DateUtils.getWeek;

/**
 * 批发商采购商品Service实现
 */
@Service
public class GbDistributerPurchaseGoodsServiceImpl extends ServiceImpl<GbDistributerPurchaseGoodsMapper, GbDistributerPurchaseGoodsEntity> implements GbDistributerPurchaseGoodsService {

    @Autowired
    private GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    @Autowired
    private GbDepartmentDisGoodsService gbDepartmentDisGoodsService;
    @Autowired
    private GbDistributerGoodsService gbDistributerGoodsService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDepartmentStockEntriesByPurchase(List<GbDepartmentOrdersEntity> ordersEntityList, Integer purGoodsId) {
        if (purGoodsId == null) {
            throw new IllegalArgumentException("purGoodsId 不能为空");
        }
        if (ordersEntityList == null || ordersEntityList.isEmpty()) {
            return;
        }

        GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = getById(purGoodsId);
        if (purchaseGoodsEntity == null) {
            throw new IllegalStateException("采购商品不存在: purGoodsId=" + purGoodsId);
        }

        List<Integer> depDisGoodsIds = ordersEntityList.stream()
                .map(GbDepartmentOrdersEntity::getGbDoDepDisGoodsId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<Integer> disGoodsIds = ordersEntityList.stream()
                .map(GbDepartmentOrdersEntity::getGbDoDisGoodsId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Integer, GbDepartmentDisGoodsEntity> depDisGoodsById = depDisGoodsIds.isEmpty()
                ? Collections.emptyMap()
                : gbDepartmentDisGoodsService.listByIds(depDisGoodsIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(GbDepartmentDisGoodsEntity::getGbDepartmentDisGoodsId, Function.identity(), (a, b) -> a));
        Map<Integer, GbDistributerGoodsEntity> disGoodsById = disGoodsIds.isEmpty()
                ? Collections.emptyMap()
                : gbDistributerGoodsService.listByIds(disGoodsIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(GbDistributerGoodsEntity::getGbDistributerGoodsId, Function.identity(), (a, b) -> a));

        String stockDate = formatWhatDay(0);
        String timeStamp = getTimeStamp();
        String week = getWeek(0);
        String month = formatWhatMonth(0);
        String year = formatWhatYear(0);
        String fullTime = formatFullTime();

        List<GbDepartmentGoodsStockEntity> stockBatch = new ArrayList<>(ordersEntityList.size());
        for (GbDepartmentOrdersEntity order : ordersEntityList) {
            Integer gbDoDepDisGoodsId = order.getGbDoDepDisGoodsId();
            if (gbDoDepDisGoodsId == null) {
                throw new IllegalStateException("部门订单缺少部门分销商品ID，无法写入库存");
            }
            GbDepartmentDisGoodsEntity departmentDisGoodsEntity = depDisGoodsById.get(gbDoDepDisGoodsId);
            if (departmentDisGoodsEntity == null) {
                throw new IllegalStateException("部门分销商品不存在: gbDepartmentDisGoodsId=" + gbDoDepDisGoodsId);
            }
            if (departmentDisGoodsEntity.getGbDdgOrderDate() != null && !departmentDisGoodsEntity.getGbDdgOrderDate().trim().isEmpty()) {
                if (order.getGbDoPrice() != null && !order.getGbDoPrice().trim().isEmpty()) {
                    BigDecimal decimal = new BigDecimal(departmentDisGoodsEntity.getGbDdgOrderPrice());
                    BigDecimal decimal1 = new BigDecimal(order.getGbDoPrice());
                    BigDecimal subtract1 = decimal1.subtract(decimal);
                    order.setGbDoPriceDifferent(subtract1.toString());
                } else {
                    order.setGbDoPriceDifferent("0");
                }
            }

            Integer gbDoDisGoodsId = order.getGbDoDisGoodsId();
            if (gbDoDisGoodsId == null) {
                throw new IllegalStateException("部门订单缺少批发商商品ID，无法写入库存");
            }
            GbDistributerGoodsEntity goodsEntity = disGoodsById.get(gbDoDisGoodsId);
            if (goodsEntity == null) {
                throw new IllegalStateException("批发商商品不存在: gbDistributerGoodsId=" + gbDoDisGoodsId);
            }

            GbDepartmentGoodsStockEntity stockEntity = new GbDepartmentGoodsStockEntity();
            stockEntity.setGbDgsGbDepartmentId(order.getGbDoDepartmentId());
            stockEntity.setGbDgsGbDepartmentFatherId(order.getGbDoDepartmentFatherId());
            stockEntity.setGbDgsGbPurGoodsId(order.getGbDoPurchaseGoodsId());
            stockEntity.setGbDgsGbDistributerId(order.getGbDoDistributerId());
            stockEntity.setGbDgsWeight(order.getGbDoWeight());
            stockEntity.setGbDgsPrice(order.getGbDoPrice());
            stockEntity.setGbDgsSubtotal(order.getGbDoSubtotal());
            stockEntity.setGbDgsRestWeight(order.getGbDoWeight());
            stockEntity.setGbDgsRestSubtotal(order.getGbDoSubtotal());
            stockEntity.setGbDgsGbDisGoodsId(order.getGbDoDisGoodsId());
            stockEntity.setGbDgsNxSupplierId(-1);
            stockEntity.setGbDgsStatus(0);
            stockEntity.setGbDgsPurUserId(purchaseGoodsEntity.getGbDpgPurUserId());
            stockEntity.setGbDgsGbDisGoodsFatherId(goodsEntity.getGbDgDfgGoodsFatherId());
            stockEntity.setGbDgsGbDisGoodsGrandId(goodsEntity.getGbDgDfgGoodsGrandId());
            stockEntity.setGbDgsGbDisGoodsGreatId(goodsEntity.getGbDgDfgGoodsGreatId());
            stockEntity.setGbDgsGbDepDisGoodsId(order.getGbDoDepDisGoodsId());
            stockEntity.setGbDgsDate(stockDate);
            stockEntity.setGbDgsTimeStamp(timeStamp);
            stockEntity.setGbDgsWeek(week);
            stockEntity.setGbDgsMonth(month);
            stockEntity.setGbDgsYear(year);
            stockEntity.setGbDgsFullTime(fullTime);
            stockBatch.add(stockEntity);
        }

        gbDepartmentGoodsStockService.saveBatch(stockBatch);
    }

    @Override
    public GbDistributerPurchaseGoodsEntity annotatePurchaseGoodsPriceReason(
            GbDistributerPurchaseGoodsEntity purchaseGoodsEntity) {
        Integer gbDpgDisGoodsId = purchaseGoodsEntity.getGbDpgDisGoodsId();
        BigDecimal buyPrice = new BigDecimal(purchaseGoodsEntity.getGbDpgBuyPrice());
        GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.getById(gbDpgDisGoodsId);

        BigDecimal weight = new BigDecimal(purchaseGoodsEntity.getGbDpgBuyQuantity());
        BigDecimal goodsHighest = new BigDecimal(gbDistributerGoodsEntity.getGbDgGoodsHighestPrice());
        BigDecimal goodsLowest = new BigDecimal(gbDistributerGoodsEntity.getGbDgGoodsLowestPrice());

        if (buyPrice.compareTo(goodsHighest) == 1
                && purchaseGoodsEntity.getGbDpgBuyQuantity() != null
                && !purchaseGoodsEntity.getGbDpgBuyQuantity().trim().isEmpty()) {
            purchaseGoodsEntity.setGbDpgBuyPriceReason("价格偏高");
        } else if (buyPrice.compareTo(goodsLowest) == -1) {
            purchaseGoodsEntity.setGbDpgBuyPriceReason("价格偏低");
        } else {
            purchaseGoodsEntity.setGbDpgBuyPriceReason("价格正常");
        }
        return purchaseGoodsEntity;
    }

    @Override
    public List<GbDistributerPurchaseGoodsEntity> querySimplePurGoods(Map<String, Object> map) {
        return baseMapper.querySimplePurGoods(map);
    }

    @Override
    public GbDistributerPurchaseGoodsEntity queryPurchaseGoodsLastItem(Map<String, Object> map) {
        return baseMapper.queryPurchaseGoodsLastItem(map);
    }

    @Override
    public List<NxJrdhSupplierEntity> queryDisPurGoodsSupplierList(Map<String, Object> map) {
        return baseMapper.queryDisPurGoodsSupplierList(map);
    }

    @Override
    public Integer queryPurchaseGoodsCount(Map<String, Object> map) {
        return baseMapper.queryPurchaseGoodsCount(map);
    }

    @Override
    public Double queryPurchaseGoodsSubTotal(Map<String, Object> map) {
        return baseMapper.queryPurchaseGoodsSubTotal(map);
    }

    @Override
    public Integer queryGbPurchaseGoodsCount(Map<String, Object> map) {
        return baseMapper.queryGbPurchaseGoodsCount(map);
    }

    @Override
    public Double queryPurchaseGoodsWeightTotal(Map<String, Object> map) {
        return baseMapper.queryPurchaseGoodsWeightTotal(map);
    }

    @Override
    public String queryPurGoodsMaxPrice(Map<String, Object> map) {
        return baseMapper.queryPurGoodsMaxPrice(map);
    }

    @Override
    public String queryPurGoodsMinPrice(Map<String, Object> map) {
        return baseMapper.queryPurGoodsMinPrice(map);
    }

    @Override
    public String queryPurchaseGoodsPrice(Map<String, Object> map) {
        return baseMapper.queryPurchaseGoodsPrice(map);
    }

    @Override
    public String queryPurchaseGoodsWeight(Map<String, Object> map) {
        return baseMapper.queryPurchaseGoodsWeight(map);
    }

    @Override
    public Integer queryGbGoodsCount(Map<String, Object> map) {
        return baseMapper.queryGbGoodsCount(map);
    }

    @Override
    public List<GbDepartmentUserEntity> queryPurUserList(Map<String, Object> map) {
        return baseMapper.queryPurUserList(map);
    }

    @Override
    public Integer queryGbDisGoodsTreeCount(Map<String, Object> queryMap) {
        return baseMapper.queryGbDisGoodsTreeCount(queryMap);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryDisTreeGoodsWithPurList(Map<String, Object> queryMap) {
        return baseMapper.queryDisTreeGoodsWithPurList(queryMap);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryPurchaseGoodsFatherTypeByParams(Map<String, Object> queryMap) {
        return baseMapper.queryPurchaseGoodsFatherTypeByParams(queryMap);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopTimes(Map<String, Object> map) {
        return baseMapper.queryGbPurchaseGoodsTopTimes(map);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopSubtotal(Map<String, Object> map) {
        return baseMapper.queryGbPurchaseGoodsTopSubtotal(map);
    }

    @Override
    public Double queryGbPurchaseSubtotalTopSubtotal(Map<String, Object> map) {
        return baseMapper.queryGbPurchaseSubtotalTopSubtotal(map);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopPriceFluctuation(Map<String, Object> map) {
        return baseMapper.queryGbPurchaseGoodsTopPriceFluctuation(map);
    }

    @Override
    public List<GbDistributerPurchaseGoodsEntity> queryPurchaseGoodsWithDetailByParams(Map<String, Object> map) {
        return baseMapper.queryPurchaseGoodsWithDetailByParams(map);
    }

    @Override
    public List<GbDistributerPurchaseGoodsEntity> queryPurchaseGoodsWithOrdersByBatch(Map<String, Object> map) {
        return baseMapper.queryPurchaseGoodsWithOrdersByBatch(map);
    }

    @Override
    public List<GbDistributerPurchaseGoodsEntity> queryOnlyPurGoods(Map<String, Object> map) {
        return baseMapper.queryOnlyPurGoods(map);
    }

    @Override
    public void fillWastePurGoodsForDisTreeGoods(List<GbDistributerGoodsEntity> goodsList, Map<String, Object> queryMap) {
        if (goodsList == null || goodsList.isEmpty()) {
            return;
        }
        List<Integer> disGoodsIds = goodsList.stream()
                .map(GbDistributerGoodsEntity::getGbDistributerGoodsId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (disGoodsIds.isEmpty()) {
            for (GbDistributerGoodsEntity g : goodsList) {
                g.setWastePurGoodsEntities(Collections.emptyList());
            }
            return;
        }
        Map<String, Object> q = new HashMap<>(queryMap);
        q.remove("offset");
        q.remove("limit");
        q.remove("money");
        q.remove("times");
        q.put("disGoodsIds", disGoodsIds);
        List<GbDistributerPurchaseGoodsEntity> rows = baseMapper.queryPurchaseGoodsWithStocksDetailForGoodsIds(q);
        Map<Integer, List<GbDistributerPurchaseGoodsEntity>> byDisGoodsId = new LinkedHashMap<>();
        for (GbDistributerPurchaseGoodsEntity row : rows) {
            Integer dgId = row.getGbDpgDisGoodsId();
            if (dgId == null) {
                continue;
            }
            byDisGoodsId.computeIfAbsent(dgId, k -> new ArrayList<>()).add(row);
        }
        for (GbDistributerGoodsEntity g : goodsList) {
            Integer gid = g.getGbDistributerGoodsId();
            if (gid == null) {
                g.setWastePurGoodsEntities(Collections.emptyList());
            } else {
                g.setWastePurGoodsEntities(new ArrayList<>(byDisGoodsId.getOrDefault(gid, Collections.emptyList())));
            }
        }
    }

    @Override
    public List<Map<String, Object>> queryPurchaseBuyQtyAggByDisGoods(Map<String, Object> map) {
        List<Map<String, Object>> list = baseMapper.queryPurchaseBuyQtyAggByDisGoods(map);
        return list != null ? list : Collections.emptyList();
    }

}
