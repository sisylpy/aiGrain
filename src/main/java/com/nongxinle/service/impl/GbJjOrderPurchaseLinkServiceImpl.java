package com.nongxinle.service.impl;

import com.nongxinle.entity.*;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerFatherGoodsService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.service.GbJjOrderPurchaseLinkService;
import com.nongxinle.service.NxJrdhSupplierService;
import com.nongxinle.utils.GbConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.*;
import static com.nongxinle.utils.GbTypeUtils.getGbPurchaseGoodsStatusNew;
import static com.nongxinle.utils.GbTypeUtils.getGbPurchaseGoodsStatusProcurement;

/**
 * {@link GbJjOrderPurchaseLinkService} 实现。
 */
@Service
public class GbJjOrderPurchaseLinkServiceImpl implements GbJjOrderPurchaseLinkService {

    @Autowired
    private GbDistributerFatherGoodsService gbDistributerFatherGoodsService;
    @Autowired
    private GbDistributerPurchaseGoodsService gbDistributerPurchaseGoodsService;
    @Autowired
    private GbDepartmentOrdersService gbDepartmentOrdersService;
    @Autowired
    private GbDistributerPurchaseBatchService gbDPBService;
    @Autowired
    private NxJrdhSupplierService jrdhSupplierService;

    @Override
    public void applyJjOrderTimestamps(GbDepartmentOrdersEntity order) {
        order.setGbDoApplyDate(formatWhatDay(0));
        order.setGbDoApplyFullTime(formatWhatYearDayTime(0));
        order.setGbDoApplyOnlyTime(formatWhatTime(0));
        order.setGbDoArriveOnlyDate(formatWhatDate(0));
        order.setGbDoArriveWeeksYear(getWeekOfYear(0));
    }

    @Override
    public void applyDisGoodsCategoryHierarchyToOrder(GbDepartmentOrdersEntity order, Integer dgDfgGoodsFatherId) {
        GbDistributerFatherGoodsEntity fatherGoodsEntity = gbDistributerFatherGoodsService.queryObject(dgDfgGoodsFatherId);
        Integer gbDfgFathersFatherId = fatherGoodsEntity.getGbDfgFathersFatherId();
        GbDistributerFatherGoodsEntity grandFather = gbDistributerFatherGoodsService.queryObject(gbDfgFathersFatherId);
        Integer greatFatherId = grandFather.getGbDfgFathersFatherId();
        GbDistributerFatherGoodsEntity greatFather = gbDistributerFatherGoodsService.queryObject(greatFatherId);

        order.setGbDoDisGoodsFatherId(fatherGoodsEntity.getGbDistributerFatherGoodsId());
        order.setGbDoDisGoodsGrandId(fatherGoodsEntity.getGbDfgFathersFatherId());
        order.setGbDoDisGoodsGreatId(greatFatherId);
        order.setGbDoNxGoodsGrandId(grandFather.getGbDfgNxGoodsId());
        order.setGbDoNxGoodsGreatId(greatFather.getGbDfgNxGoodsId());
    }

    @Override
    public GbDistributerPurchaseGoodsEntity resolvePurchaseGoodsLineForJjOrder(
            GbDepartmentOrdersEntity order,
            GbDistributerGoodsEntity disGoods,
            PurchaseGoodsLinkMode mode) {

        if (mode == PurchaseGoodsLinkMode.ALWAYS_NEW) {
            return linkAlwaysNew(order, disGoods);
        }
        if (mode == PurchaseGoodsLinkMode.MERGE_BY_PUR_DEPARTMENT) {
            return linkMergeByPurDepartment(order, disGoods);
        }
        return linkMergeBySupplierOrStatus(order, disGoods);
    }

    private GbDistributerPurchaseGoodsEntity linkAlwaysNew(GbDepartmentOrdersEntity order, GbDistributerGoodsEntity disGoods) {
        GbDistributerPurchaseGoodsEntity gbPurchaseGoodsEntity = new GbDistributerPurchaseGoodsEntity();
        gbPurchaseGoodsEntity.setGbDpgDisGoodsFatherId(order.getGbDoDisGoodsFatherId());
        gbPurchaseGoodsEntity.setGbDpgDisGoodsGrandId(order.getGbDoDisGoodsGrandId());
        gbPurchaseGoodsEntity.setGbDpgDisGoodsGreatId(order.getGbDoDisGoodsGreatId());
        gbPurchaseGoodsEntity.setGbDpgDisGoodsId(order.getGbDoDisGoodsId());
        gbPurchaseGoodsEntity.setGbDpgDistributerId(order.getGbDoDistributerId());
        gbPurchaseGoodsEntity.setGbDpgApplyDate(formatWhatDay(0));
        gbPurchaseGoodsEntity.setGbDpgStatus(0);
        gbPurchaseGoodsEntity.setGbDpgOrdersAmount(1);
        gbPurchaseGoodsEntity.setGbDpgOrdersFinishAmount(0);
        gbPurchaseGoodsEntity.setGbDpgOrdersWeightAmount(0);
        gbPurchaseGoodsEntity.setGbDpgOrdersBillAmount(0);
        gbPurchaseGoodsEntity.setGbDpgStandard(order.getGbDoStandard());
        gbPurchaseGoodsEntity.setGbDpgQuantity(order.getGbDoQuantity());
        gbPurchaseGoodsEntity.setGbDpgBuyScale(order.getGbDoDsStandardScale());
        gbPurchaseGoodsEntity.setGbDpgPurchaseDepartmentId(order.getGbDoToDepartmentId());
        gbPurchaseGoodsEntity.setGbDpgPurchaseType(GbConstants.PurchaseOrderType.UN_DETERMINED);
        gbPurchaseGoodsEntity.setGbDpgPurchaseNxSupplierId(-1);
        if (disGoods.getGbDgGoodsStandardname().equals(order.getGbDoStandard())) {
            order.setGbDoWeight(order.getGbDoQuantity());
            gbPurchaseGoodsEntity.setGbDpgBuyQuantity(order.getGbDoQuantity());
        }
        gbDistributerPurchaseGoodsService.save(gbPurchaseGoodsEntity);
        order.setGbDoPurchaseGoodsId(gbPurchaseGoodsEntity.getGbDistributerPurchaseGoodsId());
        gbDepartmentOrdersService.update(order);
        return gbPurchaseGoodsEntity;
    }

    private GbDistributerPurchaseGoodsEntity linkMergeByPurDepartment(
            GbDepartmentOrdersEntity order,
            GbDistributerGoodsEntity disGoods) {

        Map<String, Object> map = new HashMap<>();
        map.put("purDepId", order.getGbDoToDepartmentId());
        map.put("standard", order.getGbDoStandard());
        map.put("disGoodsId", order.getGbDoDisGoodsId());
        if (disGoods.getGbDgGbSupplierId() != null && disGoods.getGbDgGbSupplierId() != -1) {
            map.put("status", 2);
        } else {
            map.put("equalStatus", 0);
        }

        List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntities = gbDistributerPurchaseGoodsService.queryOnlyPurGoods(map);
        GbDistributerPurchaseGoodsEntity gbPurchaseGoodsEntity = new GbDistributerPurchaseGoodsEntity();

        if (purchaseGoodsEntities.isEmpty()) {
            gbPurchaseGoodsEntity.setGbDpgPurchaseType(order.getGbDoGoodsType());
            gbPurchaseGoodsEntity.setGbDpgDisGoodsFatherId(order.getGbDoDisGoodsFatherId());
            gbPurchaseGoodsEntity.setGbDpgDisGoodsGrandId(order.getGbDoDisGoodsGrandId());
            gbPurchaseGoodsEntity.setGbDpgDisGoodsGreatId(order.getGbDoDisGoodsGreatId());
            gbPurchaseGoodsEntity.setGbDpgPurchaseNxSupplierId(-1);
            gbPurchaseGoodsEntity.setGbDpgDisGoodsId(order.getGbDoDisGoodsId());
            gbPurchaseGoodsEntity.setGbDpgDistributerId(order.getGbDoDistributerId());
            gbPurchaseGoodsEntity.setGbDpgApplyDate(formatWhatDay(0));
            gbPurchaseGoodsEntity.setGbDpgStatus(getGbPurchaseGoodsStatusNew());
            gbPurchaseGoodsEntity.setGbDpgOrdersAmount(1);
            gbPurchaseGoodsEntity.setGbDpgOrdersFinishAmount(0);
            gbPurchaseGoodsEntity.setGbDpgOrdersWeightAmount(0);
            gbPurchaseGoodsEntity.setGbDpgOrdersBillAmount(0);
            gbPurchaseGoodsEntity.setGbDpgStandard(order.getGbDoStandard());
            gbPurchaseGoodsEntity.setGbDpgQuantity(order.getGbDoQuantity());
            gbPurchaseGoodsEntity.setGbDpgBuyScale(order.getGbDoDsStandardScale());
            gbPurchaseGoodsEntity.setGbDpgPurchaseDepartmentId(order.getGbDoToDepartmentId());
            if (disGoods.getGbDgGoodsStandardname().equals(order.getGbDoStandard())) {
                order.setGbDoWeight(order.getGbDoQuantity());
                gbPurchaseGoodsEntity.setGbDpgBuyQuantity(order.getGbDoQuantity());
            }
            gbDistributerPurchaseGoodsService.save(gbPurchaseGoodsEntity);
            order.setGbDoPurchaseGoodsId(gbPurchaseGoodsEntity.getGbDistributerPurchaseGoodsId());
            gbDepartmentOrdersService.update(order);
        } else {
            gbPurchaseGoodsEntity = purchaseGoodsEntities.get(0);
            order.setGbDoPurchaseGoodsId(gbPurchaseGoodsEntity.getGbDistributerPurchaseGoodsId());
            Integer gbDpgOrdersAmount = gbPurchaseGoodsEntity.getGbDpgOrdersAmount();
            gbPurchaseGoodsEntity.setGbDpgOrdersAmount(gbDpgOrdersAmount + 1);
            BigDecimal purQuantity = new BigDecimal(gbPurchaseGoodsEntity.getGbDpgQuantity());
            BigDecimal orderQuantity = new BigDecimal(order.getGbDoQuantity());
            BigDecimal add = purQuantity.add(orderQuantity).setScale(1, BigDecimal.ROUND_HALF_UP);
            gbPurchaseGoodsEntity.setGbDpgQuantity(add.toString());
            if (disGoods.getGbDgGoodsStandardname().equals(order.getGbDoStandard())) {
                order.setGbDoWeight(order.getGbDoQuantity());
                gbPurchaseGoodsEntity.setGbDpgBuyQuantity(add.toString());
            }
            order.setGbDoPurchaseGoodsId(gbPurchaseGoodsEntity.getGbDistributerPurchaseGoodsId());
            gbDepartmentOrdersService.update(order);
            gbDistributerPurchaseGoodsService.updateById(gbPurchaseGoodsEntity);
        }
        return gbPurchaseGoodsEntity;
    }

    private GbDistributerPurchaseGoodsEntity linkMergeBySupplierOrStatus(
            GbDepartmentOrdersEntity order,
            GbDistributerGoodsEntity disGoods) {

        Map<String, Object> map = new HashMap<>();
        map.put("standard", order.getGbDoStandard());
        map.put("disGoodsId", order.getGbDoDisGoodsId());
        if (disGoods.getGbDgGbSupplierId() != null && disGoods.getGbDgGbSupplierId() != -1) {
            map.put("supplierId", disGoods.getGbDgGbSupplierId());
            map.put("status", 2);
        } else {
            map.put("equalStatus", 0);
        }

        List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntities = gbDistributerPurchaseGoodsService.queryOnlyPurGoods(map);
        GbDistributerPurchaseGoodsEntity gbPurchaseGoodsEntity = new GbDistributerPurchaseGoodsEntity();

        if (purchaseGoodsEntities.isEmpty()) {
            gbPurchaseGoodsEntity.setGbDpgPurchaseType(order.getGbDoGoodsType());
            gbPurchaseGoodsEntity.setGbDpgDisGoodsFatherId(order.getGbDoDisGoodsFatherId());
            gbPurchaseGoodsEntity.setGbDpgDisGoodsId(order.getGbDoDisGoodsId());
            gbPurchaseGoodsEntity.setGbDpgDistributerId(order.getGbDoDistributerId());
            gbPurchaseGoodsEntity.setGbDpgApplyDate(formatWhatDay(0));
            gbPurchaseGoodsEntity.setGbDpgStatus(getGbPurchaseGoodsStatusNew());
            gbPurchaseGoodsEntity.setGbDpgOrdersAmount(1);
            gbPurchaseGoodsEntity.setGbDpgOrdersFinishAmount(0);
            gbPurchaseGoodsEntity.setGbDpgOrdersWeightAmount(0);
            gbPurchaseGoodsEntity.setGbDpgOrdersBillAmount(0);
            gbPurchaseGoodsEntity.setGbDpgStandard(order.getGbDoStandard());
            gbPurchaseGoodsEntity.setGbDpgQuantity(order.getGbDoQuantity());
            gbPurchaseGoodsEntity.setGbDpgBuyQuantity(order.getGbDoQuantity());
            gbPurchaseGoodsEntity.setGbDpgBuyScale(order.getGbDoDsStandardScale());
            gbPurchaseGoodsEntity.setGbDpgPurchaseDepartmentId(disGoods.getGbDgGbDepartmentId());
            gbPurchaseGoodsEntity.setGbDpgPurchaseNxSupplierId(disGoods.getGbDgGbSupplierId());
            gbPurchaseGoodsEntity.setGbDpgDisGoodsGrandId(disGoods.getGbDgDfgGoodsGrandId());
            gbPurchaseGoodsEntity.setGbDpgDisGoodsGreatId(disGoods.getGbDgDfgGoodsGreatId());
            if (disGoods.getGbDgGoodsStandardname().equals(order.getGbDoStandard())) {
                order.setGbDoWeight(order.getGbDoQuantity());
                gbPurchaseGoodsEntity.setGbDpgBuyQuantity(order.getGbDoQuantity());
            }
            gbDistributerPurchaseGoodsService.save(gbPurchaseGoodsEntity);
            order.setGbDoPurchaseGoodsId(gbPurchaseGoodsEntity.getGbDistributerPurchaseGoodsId());
        } else {
            gbPurchaseGoodsEntity = purchaseGoodsEntities.get(0);
            order.setGbDoPurchaseGoodsId(gbPurchaseGoodsEntity.getGbDistributerPurchaseGoodsId());
            Integer gbDpgOrdersAmount = gbPurchaseGoodsEntity.getGbDpgOrdersAmount();
            gbPurchaseGoodsEntity.setGbDpgOrdersAmount(gbDpgOrdersAmount + 1);
            BigDecimal purQuantity = new BigDecimal(gbPurchaseGoodsEntity.getGbDpgQuantity());
            BigDecimal orderQuantity = new BigDecimal(order.getGbDoQuantity());
            BigDecimal add = purQuantity.add(orderQuantity).setScale(1, BigDecimal.ROUND_HALF_UP);
            gbPurchaseGoodsEntity.setGbDpgQuantity(add.toString());
            if (disGoods.getGbDgGoodsStandardname().equals(order.getGbDoStandard())) {
                order.setGbDoWeight(order.getGbDoQuantity());
                BigDecimal bigDecimal = new BigDecimal(order.getGbDoQuantity())
                        .add(new BigDecimal(gbPurchaseGoodsEntity.getGbDpgBuyQuantity()))
                        .setScale(1, BigDecimal.ROUND_HALF_UP);
                gbPurchaseGoodsEntity.setGbDpgBuyQuantity(bigDecimal.toString());
            }
            gbDistributerPurchaseGoodsService.updateById(gbPurchaseGoodsEntity);
        }
        return gbPurchaseGoodsEntity;
    }

    @Override
    public Map<String, Object> ensureSupplierPurchaseBatchForJjOrder(
            GbDepartmentOrdersEntity ordersEntity,
            GbDistributerGoodsEntity goodsEntity) {
        Map<String, Object> mapData = new HashMap<>();
        Integer gbDgGbSupplierId = goodsEntity.getGbDgGbSupplierId();
        NxJrdhSupplierEntity nxJrdhSupplierEntity = jrdhSupplierService.getById(gbDgGbSupplierId);
        Integer nxJrdhsUserId = nxJrdhSupplierEntity.getNxJrdhsUserId();
        Integer nxJrdhsGbDepartmentId = nxJrdhSupplierEntity.getNxJrdhsGbDepartmentId();
        Map<String, Object> map = new HashMap<>();
        map.put("supplierId", gbDgGbSupplierId);
        map.put("status", 1);
        map.put("notEqualPurchaseType", 9);
        List<GbDistributerPurchaseBatchEntity> entities = gbDPBService.queryDisPurchaseBatchInfo(map);

        if (entities.isEmpty()) {

            GbDistributerPurchaseBatchEntity batchEntity = new GbDistributerPurchaseBatchEntity();
            batchEntity.setGbDpbDate(formatWhatDay(0));
            batchEntity.setGbDpbHour(formatWhatHour(0));
            batchEntity.setGbDpbMinute(formatWhatMinute(0));
            batchEntity.setGbDpbTime(formatWhatTime(0));
            batchEntity.setGbDpbPurchaseMonth(formatWhatMonth(0));
            batchEntity.setGbDpbPurchaseWeek(getWeek(0));
            batchEntity.setGbDpbPurchaseYear(formatWhatYear(0));
            batchEntity.setGbDpbPurchaseFullTime(formatWhatYearDayTime(0));
            batchEntity.setGbDpbStatus(-1);
            batchEntity.setGbDpbDistributerId(ordersEntity.getGbDoDistributerId());
            batchEntity.setGbDpbPurDepartmentId(nxJrdhsGbDepartmentId);
            batchEntity.setGbDpbBuyUserId(-1);
            batchEntity.setGbDpbBuyUserOpenId("-1");
            batchEntity.setGbDpbUserAdminType(-1);
            batchEntity.setGbDpbSupplierId(gbDgGbSupplierId);
            batchEntity.setGbDpbSellUserId(nxJrdhsUserId);
            batchEntity.setGbDpbPurchaseType(21);
            batchEntity.setGbDpbSubtotal("0");
            batchEntity.setGbDpbPayType(1);
            gbDPBService.save(batchEntity);

            Integer gbDoPurchaseGoodsId = ordersEntity.getGbDoPurchaseGoodsId();
            GbDistributerPurchaseGoodsEntity gbDistributerPurchaseGoodsEntity =
                    gbDistributerPurchaseGoodsService.getById(gbDoPurchaseGoodsId);
            gbDistributerPurchaseGoodsEntity.setGbDpgBatchId(batchEntity.getGbDistributerPurchaseBatchId());
            gbDistributerPurchaseGoodsEntity.setGbDpgStatus(getGbPurchaseGoodsStatusProcurement());
            gbDistributerPurchaseGoodsEntity.setGbDpgPurchaseDate(formatWhatDay(0));
            gbDistributerPurchaseGoodsEntity.setGbDpgPurchaseMonth(formatWhatMonth(0));
            gbDistributerPurchaseGoodsEntity.setGbDpgPurchaseYear(formatWhatYear(0));
            gbDistributerPurchaseGoodsEntity.setGbDpgPurchaseFullTime(formatWhatYearDayTime(0));
            gbDistributerPurchaseGoodsEntity.setGbDpgPurchaseWeek(getWeek(0));
            gbDistributerPurchaseGoodsEntity.setGbDpgPurchaseWeekYear(getWeekOfYear(0).toString());
            gbDistributerPurchaseGoodsEntity.setGbDpgPurchaseDepartmentId(batchEntity.getGbDpbPurDepartmentId());
            gbDistributerPurchaseGoodsEntity.setGbDpgTime(formatWhatTime(0));
            gbDistributerPurchaseGoodsEntity.setGbDpgPurUserId(-1);
            gbDistributerPurchaseGoodsEntity.setGbDpgPurchaseNxSupplierId(gbDgGbSupplierId);

            Map<String, Object> mapItem = new HashMap<>();
            mapItem.put("disGoodsId", gbDistributerPurchaseGoodsEntity.getGbDpgDisGoodsId());
            mapItem.put("supplierId", gbDgGbSupplierId);
            mapItem.put("dayuStatus", 2);
            GbDistributerPurchaseGoodsEntity lastItem = gbDistributerPurchaseGoodsService.queryPurchaseGoodsLastItem(mapItem);
            if (lastItem != null) {

                ordersEntity.setGbDoBuyStatus(1);
                ordersEntity.setGbDoPrice(lastItem.getGbDpgBuyPrice());
                gbDistributerPurchaseGoodsEntity.setGbDpgBuyPrice(lastItem.getGbDpgBuyPrice());
                if (gbDistributerPurchaseGoodsEntity.getGbDpgStandard().equals(goodsEntity.getGbDgGoodsStandardname())) {
                    BigDecimal multiply = new BigDecimal(ordersEntity.getGbDoQuantity()).multiply(new BigDecimal(lastItem.getGbDpgBuyPrice()));
                    ordersEntity.setGbDoSubtotal(multiply.setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                    ordersEntity.setGbDoWeight(ordersEntity.getGbDoQuantity());
                    BigDecimal add = new BigDecimal(gbDistributerPurchaseGoodsEntity.getGbDpgBuyQuantity()).add(new BigDecimal(ordersEntity.getGbDoQuantity()));
                    BigDecimal bigDecimal = new BigDecimal(lastItem.getGbDpgBuyPrice()).multiply(add).setScale(1, BigDecimal.ROUND_HALF_UP);
                    gbDistributerPurchaseGoodsEntity.setGbDpgBuyQuantity(add.toString());
                    gbDistributerPurchaseGoodsEntity.setGbDpgBuySubtotal(bigDecimal.toString());
                }
            }

            gbDepartmentOrdersService.update(ordersEntity);
            gbDistributerPurchaseGoodsService.updateById(gbDistributerPurchaseGoodsEntity);
            mapData.put("batchId", batchEntity.getGbDistributerPurchaseBatchId());
            return mapData;
        }

        GbDistributerPurchaseBatchEntity batchEntity = entities.get(0);
        Integer gbDoPurchaseGoodsId = ordersEntity.getGbDoPurchaseGoodsId();
        GbDistributerPurchaseGoodsEntity gbDistributerPurchaseGoodsEntity1 =
                gbDistributerPurchaseGoodsService.getById(gbDoPurchaseGoodsId);
        gbDistributerPurchaseGoodsEntity1.setGbDpgBatchId(batchEntity.getGbDistributerPurchaseBatchId());
        gbDistributerPurchaseGoodsEntity1.setGbDpgStatus(getGbPurchaseGoodsStatusProcurement());
        gbDistributerPurchaseGoodsEntity1.setGbDpgPurchaseDate(formatWhatDay(0));
        gbDistributerPurchaseGoodsEntity1.setGbDpgPurchaseMonth(formatWhatMonth(0));
        gbDistributerPurchaseGoodsEntity1.setGbDpgPurchaseYear(formatWhatYear(0));
        gbDistributerPurchaseGoodsEntity1.setGbDpgPurchaseFullTime(formatWhatYearDayTime(0));
        gbDistributerPurchaseGoodsEntity1.setGbDpgPurchaseWeek(getWeek(0));
        gbDistributerPurchaseGoodsEntity1.setGbDpgPurchaseWeekYear(getWeekOfYear(0).toString());
        gbDistributerPurchaseGoodsEntity1.setGbDpgPurchaseDepartmentId(batchEntity.getGbDpbPurDepartmentId());
        gbDistributerPurchaseGoodsEntity1.setGbDpgTime(formatWhatTime(0));

        Map<String, Object> mapItem = new HashMap<>();
        mapItem.put("disGoodsId", gbDistributerPurchaseGoodsEntity1.getGbDpgDisGoodsId());
        mapItem.put("supplierId", gbDgGbSupplierId);
        mapItem.put("dayuStatus", 2);
        GbDistributerPurchaseGoodsEntity lastItem = gbDistributerPurchaseGoodsService.queryPurchaseGoodsLastItem(mapItem);
        if (lastItem != null) {

            ordersEntity.setGbDoBuyStatus(1);
            ordersEntity.setGbDoPrice(lastItem.getGbDpgBuyPrice());
            gbDistributerPurchaseGoodsEntity1.setGbDpgBuyPrice(lastItem.getGbDpgBuyPrice());
            if (gbDistributerPurchaseGoodsEntity1.getGbDpgStandard().equals(goodsEntity.getGbDgGoodsStandardname())) {
                BigDecimal multiply = new BigDecimal(ordersEntity.getGbDoQuantity()).multiply(new BigDecimal(lastItem.getGbDpgBuyPrice()));
                ordersEntity.setGbDoWeight(ordersEntity.getGbDoQuantity());
                ordersEntity.setGbDoSubtotal(multiply.setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                BigDecimal add = new BigDecimal(gbDistributerPurchaseGoodsEntity1.getGbDpgBuyQuantity()).add(new BigDecimal(ordersEntity.getGbDoQuantity()));
                BigDecimal bigDecimal = new BigDecimal(lastItem.getGbDpgBuyPrice()).multiply(add).setScale(1, BigDecimal.ROUND_HALF_UP);
                gbDistributerPurchaseGoodsEntity1.setGbDpgBuyQuantity(add.toString());
                gbDistributerPurchaseGoodsEntity1.setGbDpgBuySubtotal(bigDecimal.toString());
            }
        }

        gbDepartmentOrdersService.update(ordersEntity);

        gbDistributerPurchaseGoodsService.updateById(gbDistributerPurchaseGoodsEntity1);
        mapData.put("batchId", batchEntity.getGbDistributerPurchaseBatchId());
        return mapData;
    }

    @Override
    public void moveUnconfirmedOrdersToNewPurchaseGoods(
            GbDistributerPurchaseGoodsEntity finishedPurchaseTemplate,
            List<GbDepartmentOrdersEntity> unChoiceOrderList,
            GbDistributerGoodsEntity disGoods) {
        if (unChoiceOrderList == null || unChoiceOrderList.isEmpty()) {
            return;
        }
        Integer gbDepartmentOrdersId = unChoiceOrderList.get(0).getGbDepartmentOrdersId();
        GbDepartmentOrdersEntity ordersEntity = gbDepartmentOrdersService.getById(gbDepartmentOrdersId);
        GbDistributerPurchaseGoodsEntity disGoodsRow = new GbDistributerPurchaseGoodsEntity();
        disGoodsRow.setGbDpgDistributerId(finishedPurchaseTemplate.getGbDpgDistributerId());
        disGoodsRow.setGbDpgPayType(0);
        disGoodsRow.setGbDpgDisGoodsGrandId(finishedPurchaseTemplate.getGbDpgDisGoodsGrandId());
        disGoodsRow.setGbDpgDisGoodsGreatId(finishedPurchaseTemplate.getGbDpgDisGoodsGreatId());
        disGoodsRow.setGbDpgDisGoodsFatherId(ordersEntity.getGbDoDisGoodsFatherId());
        disGoodsRow.setGbDpgDisGoodsId(ordersEntity.getGbDoDisGoodsId());
        disGoodsRow.setGbDpgApplyDate(formatWhatDay(0));
        disGoodsRow.setGbDpgStatus(0);
        disGoodsRow.setGbDpgBuyScale("-1");
        disGoodsRow.setGbDpgStandard(ordersEntity.getGbDoStandard());
        disGoodsRow.setGbDpgOrdersAmount(unChoiceOrderList.size());
        disGoodsRow.setGbDpgOrdersFinishAmount(0);
        disGoodsRow.setGbDpgOrdersWeightAmount(0);
        disGoodsRow.setGbDpgOrdersBillAmount(0);
        disGoodsRow.setGbDpgPurchaseWeek(getWeek(0));
        disGoodsRow.setGbDpgPurchaseWeekYear(getWeekOfYear(0).toString());
        disGoodsRow.setGbDpgIsCheck(0);
        disGoodsRow.setGbDpgPurchaseType(1);
        disGoodsRow.setGbDpgPurchaseNxSupplierId(-1);
        disGoodsRow.setGbDpgQuantity("0");
        gbDistributerPurchaseGoodsService.save(disGoodsRow);
        for (GbDepartmentOrdersEntity unChoiceOrder : unChoiceOrderList) {
            Integer gbDistributerPurchaseGoodsId = disGoodsRow.getGbDistributerPurchaseGoodsId();
            Integer gbDepartmentOrdersId1 = unChoiceOrder.getGbDepartmentOrdersId();
            GbDepartmentOrdersEntity ordersEntity1 = gbDepartmentOrdersService.getById(gbDepartmentOrdersId1);
            ordersEntity1.setGbDoPurchaseGoodsId(gbDistributerPurchaseGoodsId);
            gbDepartmentOrdersService.update(ordersEntity1);

            BigDecimal purQuantity = new BigDecimal(disGoodsRow.getGbDpgQuantity());
            BigDecimal orderQuantity = new BigDecimal(ordersEntity1.getGbDoQuantity());
            BigDecimal add = purQuantity.add(orderQuantity).setScale(2, BigDecimal.ROUND_HALF_UP);
            disGoodsRow.setGbDpgQuantity(add.toString());
            if (disGoods.getGbDgGoodsStandardname().equals(ordersEntity.getGbDoStandard())) {
                disGoodsRow.setGbDpgBuyQuantity(add.toString());
            }
            gbDistributerPurchaseGoodsService.updateById(disGoodsRow);
        }
    }
}
