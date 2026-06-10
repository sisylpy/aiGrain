package com.nongxinle.service.impl;

import com.nongxinle.dto.GbDepGoodsStockAdjustRequest;
import com.nongxinle.dto.GbDepGoodsStockAdjustResult;
import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockReduceEntity;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.service.GbDepartmentDisGoodsService;
import com.nongxinle.service.GbDepartmentGoodsStockLedgerService;
import com.nongxinle.service.GbDepartmentGoodsStockQueryService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.utils.DateUtils;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.GbDepGoodsStockAdjustKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.nongxinle.utils.DateUtils.formatWhatDay;

/**
 * 部门库存与 reduce 编排实现。
 */
@Service
public class GbDepartmentGoodsStockLedgerServiceImpl implements GbDepartmentGoodsStockLedgerService {

    private static final Logger log = LoggerFactory.getLogger(GbDepartmentGoodsStockLedgerServiceImpl.class);

    @Autowired
    private GbDepartmentGoodsStockService stockService;
    @Autowired
    private GbDepartmentGoodsStockReduceService gbDepartmentStockReduceService;
    @Autowired
    private GbDepartmentDisGoodsService gbDepartmentDisGoodsService;
    @Autowired
    private GbDistributerGoodsService disGoodsService;
    @Autowired
    private GbDepartmentOrdersService gbDepartmentOrdersService;
    @Autowired
    private GbDistributerPurchaseGoodsService gbDistributerPurchaseGoodsService;
    @Autowired
    private GbDistributerPurchaseBatchService gbDistributerPurchaseBatchService;
    @Autowired
    private GbDepartmentGoodsStockQueryService gbDepartmentGoodsStockQueryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GbDepGoodsStockAdjustResult adjustDepGoodsStock(GbDepGoodsStockAdjustRequest request) {
        if (request == null || request.getStock() == null) {
            return GbDepGoodsStockAdjustResult.error(500, "参数无效");
        }
        GbDepartmentGoodsStockEntity stock = request.getStock();
        Integer stockId = stock.getGbDepartmentGoodsStockId();
        if (stockId == null) {
            return GbDepGoodsStockAdjustResult.error(500, "参数无效");
        }
        String what = GbDepGoodsStockAdjustKind.resolveCanonicalKind(request.getKind());
        if (what == null) {
            return GbDepGoodsStockAdjustResult.error(500, GbDepGoodsStockAdjustKind.invalidKindMessage());
        }

        GbDepartmentGoodsStockEntity fromDb = stockService.getById(stockId);
        if (fromDb == null) {
            return GbDepGoodsStockAdjustResult.error(-1, "请刷新数据");
        }
        if (bd(fromDb.getGbDgsRestWeight()).compareTo(BigDecimal.ZERO) == 0) {
            return GbDepGoodsStockAdjustResult.error(-1, "请刷新数据");
        }

        if (GbDepGoodsStockAdjustKind.RETURN.equals(what)) {
            Integer fromDep = stock.getGbDgsGbFromDepartmentId();
            Integer disGoodsId = stock.getGbDgsGbDisGoodsId();
            if (disGoodsId == null) {
                return GbDepGoodsStockAdjustResult.error(-1, "这个批次已修改出货部门，不能退货");
            }
            GbDistributerGoodsEntity goods = disGoodsService.getById(disGoodsId);
            Integer goodsDepId = goods != null ? goods.getGbDgGbDepartmentId() : null;
            if (!Objects.equals(fromDep, goodsDepId)) {
                return GbDepGoodsStockAdjustResult.error(-1, "这个批次已修改出货部门，不能退货");
            }
        }

        GbDepGoodsStockAdjustResult quantityError = validateOutboundQuantity(stock, what, fromDb);
        if (quantityError != null) {
            return quantityError;
        }

        try {
            GbDepartmentGoodsStockReduceEntity reduceEntity = changeDepartmentStock(stock, what, fromDb);

            GbDepartmentDisGoodsEntity disGoods = loadDepDisGoodsForDepGoodsPageShape(
                    fromDb.getGbDgsGbDepDisGoodsId(), stock.getGbDgsGbDepartmentId());

            GbDepartmentGoodsStockEntity refreshedStock = stockService.getById(stockId);
            if (refreshedStock != null) {
                gbDepartmentGoodsStockQueryService.enrichStockBatchReduceLists(
                        Collections.singletonList(refreshedStock));
            }

            Map<String, Object> data = new HashMap<>();
            data.put("disGoods", disGoods);
            if (refreshedStock != null) {
                data.put("stock", refreshedStock);
            }
            if (GbDepGoodsStockAdjustKind.LOSS.equals(what)
                    || GbDepGoodsStockAdjustKind.RETURN.equals(what)
                    || GbDepGoodsStockAdjustKind.EMPLOYEE_MEAL.equals(what)) {
                data.put("id", reduceEntity.getGbDepartmentGoodsStockReduceId());
            }
            return GbDepGoodsStockAdjustResult.success(data);
        } catch (IllegalStateException e) {
            return GbDepGoodsStockAdjustResult.error(-1, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GbDepGoodsStockAdjustResult removeReduceAndRevert(Integer reduceId) {
        GbDepartmentGoodsStockReduceEntity reduceEntity = gbDepartmentStockReduceService.queryObject(reduceId);
        if (reduceEntity == null) {
            return GbDepGoodsStockAdjustResult.error(-1, "记录不存在");
        }
        Integer gbDgsrType = reduceEntity.getGbDgsrType();
        Integer gbDgsrGbGoodsStockId = reduceEntity.getGbDgsrGbGoodsStockId();
        GbDepartmentGoodsStockEntity stockEntity = stockService.getById(gbDgsrGbGoodsStockId);
        if (stockEntity == null) {
            return GbDepGoodsStockAdjustResult.error(-1, "库存批次不存在");
        }

        BigDecimal sRestWeight = bd(stockEntity.getGbDgsRestWeight());
        BigDecimal sRestSubtotal = bd(stockEntity.getGbDgsRestSubtotal());
        BigDecimal reduceBusinessWeight = BigDecimal.ZERO;
        BigDecimal reduceBusinessSubtotal = BigDecimal.ZERO;

        if (Objects.equals(gbDgsrType, GbConstants.StockReduceType.PRODUCTION)) {
            reduceBusinessWeight = bd(reduceEntity.getGbDgsrWeight());
            reduceBusinessSubtotal = bd(reduceEntity.getGbDgsrSubtotal());
            BigDecimal gbDgsProduceWeight = bd(stockEntity.getGbDgsProduceWeight());
            BigDecimal gbDgsProduceSubtotal = bd(stockEntity.getGbDgsProduceSubtotal());
            BigDecimal newProduceWeight = scaleWeightValue(gbDgsProduceWeight.subtract(reduceBusinessWeight));
            BigDecimal newProduceSubtotal = scaleSubtotalValue(gbDgsProduceSubtotal.subtract(reduceBusinessSubtotal));
            stockEntity.setGbDgsProduceWeight(weightToStore(newProduceWeight));
            stockEntity.setGbDgsProduceSubtotal(subtotalToStore(newProduceSubtotal));
        }
        if (Objects.equals(gbDgsrType, GbConstants.StockReduceType.LOSS)) {
            reduceBusinessWeight = bd(reduceEntity.getGbDgsrWeight());
            reduceBusinessSubtotal = bd(reduceEntity.getGbDgsrSubtotal());
            BigDecimal gbDgsLossWeight = bd(stockEntity.getGbDgsLossWeight());
            BigDecimal gbDgsLossSubtotal = bd(stockEntity.getGbDgsLossSubtotal());
            BigDecimal newLossWeight = scaleWeightValue(gbDgsLossWeight.subtract(reduceBusinessWeight));
            BigDecimal newLossSubtotal = scaleSubtotalValue(gbDgsLossSubtotal.subtract(reduceBusinessSubtotal));
            stockEntity.setGbDgsLossWeight(weightToStore(newLossWeight));
            stockEntity.setGbDgsLossSubtotal(subtotalToStore(newLossSubtotal));
        }
        if (Objects.equals(gbDgsrType, GbConstants.StockReduceType.WASTE)) {
            reduceBusinessWeight = bd(reduceEntity.getGbDgsrWeight());
            reduceBusinessSubtotal = bd(reduceEntity.getGbDgsrSubtotal());
            stockEntity.setGbDgsWasteWeight("0");
            stockEntity.setGbDgsWasteSubtotal("0");
        }
        if (Objects.equals(gbDgsrType, GbConstants.StockReduceType.RETURN)) {
            reduceBusinessWeight = bd(reduceEntity.getGbDgsrWeight());
            reduceBusinessSubtotal = bd(reduceEntity.getGbDgsrSubtotal());
            BigDecimal gbDgsReturnWeight = bd(stockEntity.getGbDgsReturnWeight());
            BigDecimal gbDgsReturnSubtotal = bd(stockEntity.getGbDgsReturnSubtotal());
            BigDecimal newReturnWeight = scaleWeightValue(gbDgsReturnWeight.subtract(reduceBusinessWeight));
            BigDecimal newReturnSubtotal = scaleSubtotalValue(gbDgsReturnSubtotal.subtract(reduceBusinessSubtotal));
            stockEntity.setGbDgsReturnWeight(weightToStore(newReturnWeight));
            stockEntity.setGbDgsReturnSubtotal(subtotalToStore(newReturnSubtotal));

            GbDepartmentOrdersEntity ordersEntity = gbDepartmentOrdersService.queryReturnOrderByReduceId(
                    reduceEntity.getGbDepartmentGoodsStockReduceId());
            if (ordersEntity == null) {
                return GbDepGoodsStockAdjustResult.error(-1, "未找到退货关联订单");
            }
            Integer orderId = ordersEntity.getGbDepartmentOrdersId();
            GbDepartmentGoodsStockEntity stockEntityReturn = null;
            if (stockEntity.getGbDgsGbGoodsStockId() != null && stockEntity.getGbDgsGbGoodsStockId() != -1) {
                stockEntityReturn = stockService.queryReturnStockItemByOrderId(orderId);
            }

            Integer purchaseGoodsId = ordersEntity.getGbDoPurchaseGoodsId();
            if (purchaseGoodsId != null) {
                GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDistributerPurchaseGoodsService.getById(purchaseGoodsId);
                if (purchaseGoodsEntity != null) {
                    Integer gbDpgBatchId = purchaseGoodsEntity.getGbDpgBatchId();
                    if (gbDpgBatchId != null) {
                        gbDistributerPurchaseBatchService.removeById(gbDpgBatchId);
                    }
                    gbDistributerPurchaseGoodsService.removeById(purchaseGoodsId);
                }
            }
            gbDepartmentOrdersService.removeById(orderId);

            if (stockEntityReturn != null && stockEntityReturn.getGbDepartmentGoodsStockId() != null) {
                stockService.removeById(stockEntityReturn.getGbDepartmentGoodsStockId());
            }
        }
        if (Objects.equals(gbDgsrType, GbConstants.StockReduceType.EMPLOYEE_MEAL)) {
            reduceBusinessWeight = bd(reduceEntity.getGbDgsrWeight());
            reduceBusinessSubtotal = bd(reduceEntity.getGbDgsrSubtotal());
            restoreBatchRestWeightShowStandard(stockEntity, reduceBusinessWeight);
        }

        BigDecimal newRestWeight = scaleWeightValue(sRestWeight.add(reduceBusinessWeight));
        BigDecimal newRestSubtotal = scaleSubtotalValue(sRestSubtotal.add(reduceBusinessSubtotal));
        stockEntity.setGbDgsRestWeight(weightToStore(newRestWeight));
        stockEntity.setGbDgsRestSubtotal(subtotalToStore(newRestSubtotal));
        stockService.updateById(stockEntity);

        addDepDisGoodsTotal(reduceBusinessWeight, reduceBusinessSubtotal, stockEntity.getGbDgsGbDepDisGoodsId());

        String betweentPrice = "0";
        if (stockEntity.getGbDgsBetweenPrice() != null && !stockEntity.getGbDgsBetweenPrice().trim().isEmpty()) {
            betweentPrice = stockEntity.getGbDgsBetweenPrice();
        }


        gbDepartmentStockReduceService.removeById(reduceEntity.getGbDepartmentGoodsStockReduceId());

        GbDepartmentDisGoodsEntity gbDepartmentDisGoodsEntity =
                loadDepDisGoodsForDepGoodsPageShape(stockEntity.getGbDgsGbDepDisGoodsId(), stockEntity.getGbDgsGbDepartmentId());
        Map<String, Object> data = new HashMap<>();
        data.put("data", gbDepartmentDisGoodsEntity);
        return GbDepGoodsStockAdjustResult.success(data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearDepGoodsStockWhenJjStockIsZero(GbDepartmentOrdersEntity orders) {
        if (orders == null || orders.getGbDoDepDisGoodsId() == null) {
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("depGoodsId", orders.getGbDoDepDisGoodsId());
        map.put("restWeight", 0);
        log.debug("clearDepGoodsStockWhenJjStockIsZero map={}", map);
        List<GbDepartmentGoodsStockEntity> stocks = stockService.queryGoodsStockByParams(map);
        Integer orderUserId = orders.getGbDoOrderUserId();
        for (GbDepartmentGoodsStockEntity stock : stocks) {
            stock.setGbDgsMyProduceWeight(stock.getGbDgsRestWeight());
            applyJjOrderProduceClearForStock(stock, orderUserId);
        }
    }

    /**
     * 原 {@code GbDepartmentOrdersController#changeDepartmentStock} 中「produce」分支（Jj 零库存清批次）。
     */
    private void applyJjOrderProduceClearForStock(GbDepartmentGoodsStockEntity stock, Integer orderUserId) {
        BigDecimal myChangeWeight = scaleWeightInput(stock.getGbDgsMyProduceWeight());
        BigDecimal costPrice = toBigDecimal(stock.getGbDgsPrice(), "0");
        BigDecimal myChangeSubtotal = scaleSubtotalValue(myChangeWeight.multiply(costPrice));

        BigDecimal allWeight = scaleWeightValue(toBigDecimal(stock.getGbDgsProduceWeight(), "0").add(myChangeWeight));
        BigDecimal allSubtotal = scaleSubtotalValue(toBigDecimal(stock.getGbDgsProduceSubtotal(), "0").add(myChangeSubtotal));
        stock.setGbDgsProduceWeight(weightToStore(allWeight));
        stock.setGbDgsProduceSubtotal(subtotalToStore(allSubtotal));

        String sellingPrice = stock.getGbDgsSellingPrice();
        if (sellingPrice != null && !sellingPrice.equals("-1")) {
            BigDecimal gbDgsBetweenPrice = toBigDecimal(stock.getGbDgsBetweenPrice(), "0");
            BigDecimal newProfitSubtotal = scaleProfitAmount(gbDgsBetweenPrice.multiply(myChangeWeight));
            BigDecimal profitSubtotal = scaleProfitAmount(
                    toBigDecimal(stock.getGbDgsProfitSubtotal(), "0").add(newProfitSubtotal));
            stock.setGbDgsProfitSubtotal(profitSubtotal.toString());
            BigDecimal stockAfterProfitSubtotal = toBigDecimal(stock.getGbDgsAfterProfitSubtotal(), "0");
            BigDecimal newAfterProfitSubtotal = scaleProfitAmount(stockAfterProfitSubtotal.add(newProfitSubtotal));
            stock.setGbDgsAfterProfitSubtotal(newAfterProfitSubtotal.toString());

            BigDecimal newSellingSubtotal = toBigDecimal(stock.getGbDgsSellingPrice(), "0").multiply(myChangeWeight);
            BigDecimal salesSubtotal = newSellingSubtotal.add(toBigDecimal(stock.getGbDgsProduceSellingSubtotal(), "0"));
            stock.setGbDgsProduceSellingSubtotal(salesSubtotal.toString());
            BigDecimal add = scaleWeightValue(toBigDecimal(stock.getGbDgsProfitWeight(), "0").add(myChangeWeight));
            stock.setGbDgsProfitWeight(weightToStore(add));
        }

        saveJjProduceClearReduceRow(stock, myChangeWeight, myChangeSubtotal, orderUserId);
        subscribeDepDisGoodsTotal(myChangeWeight, myChangeSubtotal, stock.getGbDgsGbDepDisGoodsId());

        BigDecimal restWeight = toBigDecimal(stock.getGbDgsRestWeight(), "0");
        BigDecimal newRestWeight = scaleWeightValue(restWeight.subtract(myChangeWeight));
        BigDecimal newRestSubtotal = scaleSubtotalValue(newRestWeight.multiply(costPrice));
        stock.setGbDgsRestWeight(weightToStore(newRestWeight));
        stock.setGbDgsRestSubtotal(subtotalToStore(newRestSubtotal));
        stock.setGbDgsInventoryFullTime(DateUtils.formatWhatFullTime(0));
        stock.setGbDgsInventoryDate(formatWhatDay(0));
        stock.setGbDgsInventoryWeek(DateUtils.getWeekOfYear(0).toString());
        stock.setGbDgsInventoryMonth(DateUtils.formatWhatMonth(0));
        stock.setGbDgsInventoryYear(DateUtils.formatWhatYear(0));

        if (stock.getGbDgsRestWeightShowStandard() != null && !stock.getGbDgsRestWeightShowStandard().trim().isEmpty()) {
            if (toBigDecimal(stock.getGbDgsRestWeightShowStandard(), "0").compareTo(BigDecimal.ZERO) > 0) {
                Integer gbDgsGbDepDisGoodsId = stock.getGbDgsGbDepDisGoodsId();
                GbDepartmentDisGoodsEntity departmentDisGoodsEntity = gbDepartmentDisGoodsService.getById(gbDgsGbDepDisGoodsId);
                if (departmentDisGoodsEntity != null) {
                    BigDecimal decimal = toBigDecimal(departmentDisGoodsEntity.getGbDdgShowStandardScale(), "1");
                    BigDecimal myChangeWeightScale = myChangeWeight.divide(
                            decimal, GbConstants.StockLedger.WEIGHT_SCALE, RoundingMode.HALF_UP);
                    BigDecimal decimal1 = scaleWeightValue(
                            toBigDecimal(stock.getGbDgsRestWeightShowStandard(), "0").subtract(myChangeWeightScale));
                    stock.setGbDgsRestWeightShowStandard(weightToStore(decimal1));
                    stock.setGbDgsRestWeightShowStandardName(departmentDisGoodsEntity.getGbDdgShowStandardName());
                }
            }
        }

        stockService.updateById(stock);
    }

    private void saveJjProduceClearReduceRow(GbDepartmentGoodsStockEntity stock, BigDecimal myChangeWeight, BigDecimal myChangeSubtotal,
            Integer orderUserId) {
        GbDepartmentGoodsStockReduceEntity reduceEntity = new GbDepartmentGoodsStockReduceEntity();
        reduceEntity.setGbDgsrUserId(orderUserId);
        reduceEntity.setGbDgsrGbDistributerId(stock.getGbDgsGbDistributerId());
        reduceEntity.setGbDgsrGbDepartmentId(stock.getGbDgsGbDepartmentId());
        reduceEntity.setGbDgsrGbDepartmentFatherId(stock.getGbDgsGbDepartmentFatherId());
        reduceEntity.setGbDgsrGbDisGoodsId(stock.getGbDgsGbDisGoodsId());
        reduceEntity.setGbDgsrGbDisGoodsGrandId(stock.getGbDgsGbDisGoodsGrandId());
        reduceEntity.setGbDgsrGbDisGoodsGreatId(stock.getGbDgsGbDisGoodsGreatId());
        reduceEntity.setGbDgsrGbDisGoodsFatherId(stock.getGbDgsGbDisGoodsFatherId());
        reduceEntity.setGbDgsrGbDepDisGoodsId(stock.getGbDgsGbDepDisGoodsId());
        reduceEntity.setGbDgsrGbGoodsStockId(stock.getGbDepartmentGoodsStockId());
        reduceEntity.setGbDgsrFullTime(DateUtils.formatFullTime());
        reduceEntity.setGbDgsrDate(formatWhatDay(0));
        reduceEntity.setGbDgsrStockNxSupplierId(stock.getGbDgsNxSupplierId());
        reduceEntity.setGbDgsrWeek(DateUtils.getWeekOfYear(0).toString());
        reduceEntity.setGbDgsrMonth(DateUtils.formatWhatMonth(0));
        reduceEntity.setGbDgsrGbPurGoodsId(stock.getGbDgsGbPurGoodsId());
        reduceEntity.setGbDgsrWeight(weightToStore(myChangeWeight));
        reduceEntity.setGbDgsrSubtotal(subtotalToStore(myChangeSubtotal));
        reduceEntity.setGbDgsrType(GbConstants.StockReduceType.PRODUCTION);
        reduceEntity.setGbDgsrStatus(0);
        gbDepartmentStockReduceService.save(reduceEntity);
    }

    private static BigDecimal bd(String s) {
        if (s == null || s.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private GbDepGoodsStockAdjustResult validateOutboundQuantity(
            GbDepartmentGoodsStockEntity stock, String what, GbDepartmentGoodsStockEntity fromDb) {
        if (GbDepGoodsStockAdjustKind.WASTE.equals(what)) {
            return null;
        }
        BigDecimal usage;
        if (GbDepGoodsStockAdjustKind.PRODUCE.equals(what)) {
            usage = scaleWeightInput(stock.getGbDgsMyProduceWeight());
        } else if (GbDepGoodsStockAdjustKind.LOSS.equals(what)) {
            usage = scaleWeightInput(stock.getGbDgsMyLossWeight());
        } else if (GbDepGoodsStockAdjustKind.RETURN.equals(what)) {
            usage = scaleWeightInput(stock.getGbDgsMyReturnWeight());
        } else if (GbDepGoodsStockAdjustKind.EMPLOYEE_MEAL.equals(what)) {
            usage = scaleWeightInput(stock.getGbDgsMyEmployeeMealWeight());
        } else {
            return null;
        }
        if (usage.compareTo(BigDecimal.ZERO) <= 0) {
            return GbDepGoodsStockAdjustResult.error(-1, "出库数量必须大于0");
        }
        BigDecimal rest = scaleWeightValue(bd(fromDb.getGbDgsRestWeight()));
        if (usage.compareTo(rest) > 0) {
            if (GbDepGoodsStockAdjustKind.EMPLOYEE_MEAL.equals(what)) {
                return GbDepGoodsStockAdjustResult.error(-1, "库存不足，无法登记员工餐");
            }
            return GbDepGoodsStockAdjustResult.error(-1, "库存不足");
        }
        return null;
    }

    private BigDecimal scaleWeightInput(String raw) {
        return scaleWeightValue(toBigDecimal(raw, "0"));
    }

    private static BigDecimal scaleWeightValue(BigDecimal weight) {
        return weight.setScale(GbConstants.StockLedger.WEIGHT_SCALE, RoundingMode.HALF_UP);
    }

    private static String weightToStore(BigDecimal weight) {
        return scaleWeightValue(weight).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal scaleSubtotalValue(BigDecimal subtotal) {
        return subtotal.setScale(GbConstants.StockLedger.SUBTOTAL_SCALE, RoundingMode.HALF_UP);
    }

    private static String subtotalToStore(BigDecimal subtotal) {
        return scaleSubtotalValue(subtotal).toPlainString();
    }

    private static BigDecimal scaleProfitAmount(BigDecimal amount) {
        return amount.setScale(GbConstants.StockLedger.PROFIT_AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private void addDepDisGoodsTotal(BigDecimal weight, BigDecimal subtotal, Integer depDisGoodsId) {
        if (depDisGoodsId == null) {
            return;
        }
        GbDepartmentDisGoodsEntity depDisGoodsEntity = gbDepartmentDisGoodsService.getById(depDisGoodsId);
        if (depDisGoodsEntity == null) {
            return;
        }
        BigDecimal weightB = bd(depDisGoodsEntity.getGbDdgStockTotalWeight()).add(weight);
        BigDecimal subtotalB = bd(depDisGoodsEntity.getGbDdgStockTotalSubtotal()).add(subtotal);
        depDisGoodsEntity.setGbDdgStockTotalSubtotal(
                subtotalB.setScale(GbConstants.StockLedger.DEP_AGG_SUBTOTAL_SCALE, RoundingMode.HALF_UP).toString());
        depDisGoodsEntity.setGbDdgStockTotalWeight(weightToStore(weightB));
        gbDepartmentDisGoodsService.updateById(depDisGoodsEntity);
    }

    private static String fullTimeSeconds() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private GbDepartmentGoodsStockReduceEntity changeDepartmentStock(
            GbDepartmentGoodsStockEntity stock, String what, GbDepartmentGoodsStockEntity fromDb) {
        GbDepartmentGoodsStockReduceEntity reduceEntity = new GbDepartmentGoodsStockReduceEntity();
        log.debug("changeDepartmentStock what={}", what);
        BigDecimal myChangeWeight = new BigDecimal("0");
        BigDecimal myChangeSubtotal = new BigDecimal(0);

        BigDecimal newAfterProfitSubtotal = new BigDecimal(0);
        BigDecimal salesSubtotal = new BigDecimal(0);
        BigDecimal profitSubtotal = new BigDecimal(0);

        String priceStr = stock.getGbDgsPrice();
        BigDecimal costPrice = (priceStr != null && !priceStr.trim().isEmpty()) ? new BigDecimal(priceStr) : BigDecimal.ZERO;

        if (GbDepGoodsStockAdjustKind.LOSS.equals(what)) {
            myChangeWeight = scaleWeightInput(stock.getGbDgsMyLossWeight());
            myChangeSubtotal = scaleSubtotalValue(myChangeWeight.multiply(costPrice));

            if (stock.getGbDgsSellingPrice() != null && !stock.getGbDgsSellingPrice().trim().isEmpty() && !stock.getGbDgsSellingPrice().equals("-1")) {
                BigDecimal stockAfterProfitSubtotal = toBigDecimal(stock.getGbDgsAfterProfitSubtotal(), "0");
                newAfterProfitSubtotal = scaleProfitAmount(stockAfterProfitSubtotal.subtract(myChangeSubtotal));
                stock.setGbDgsAfterProfitSubtotal(newAfterProfitSubtotal.toString());
            }

            BigDecimal allWeight = scaleWeightValue(toBigDecimal(stock.getGbDgsLossWeight(), "0").add(myChangeWeight));
            BigDecimal allSubtotal = scaleSubtotalValue(toBigDecimal(stock.getGbDgsLossSubtotal(), "0").add(myChangeSubtotal));
            stock.setGbDgsLossWeight(weightToStore(allWeight));
            stock.setGbDgsLossSubtotal(subtotalToStore(allSubtotal));

            reduceEntity = addDepGoodsStockReduceEntity(stock, what, myChangeWeight, myChangeSubtotal);
            subscribeDepDisGoodsTotal(myChangeWeight, myChangeSubtotal, stock.getGbDgsGbDepDisGoodsId());
        }

        if (GbDepGoodsStockAdjustKind.PRODUCE.equals(what)) {
            myChangeWeight = scaleWeightInput(stock.getGbDgsMyProduceWeight());
            myChangeSubtotal = scaleSubtotalValue(myChangeWeight.multiply(costPrice));

            BigDecimal allWeight = scaleWeightValue(toBigDecimal(stock.getGbDgsProduceWeight(), "0").add(myChangeWeight));
            BigDecimal allSubtotal = scaleSubtotalValue(toBigDecimal(stock.getGbDgsProduceSubtotal(), "0").add(myChangeSubtotal));
            stock.setGbDgsProduceWeight(weightToStore(allWeight));
            stock.setGbDgsProduceSubtotal(subtotalToStore(allSubtotal));

            if (!"-1".equals(stock.getGbDgsSellingPrice())) {
                BigDecimal gbDgsBetweenPrice = toBigDecimal(stock.getGbDgsBetweenPrice(), "0");
                BigDecimal newProfitSubtotal = scaleProfitAmount(gbDgsBetweenPrice.multiply(myChangeWeight));
                profitSubtotal = scaleProfitAmount(toBigDecimal(stock.getGbDgsProfitSubtotal(), "0").add(newProfitSubtotal));
                stock.setGbDgsProfitSubtotal(profitSubtotal.toString());
                BigDecimal stockAfterProfitSubtotal = toBigDecimal(stock.getGbDgsAfterProfitSubtotal(), "0");
                newAfterProfitSubtotal = scaleProfitAmount(stockAfterProfitSubtotal.add(newProfitSubtotal));
                stock.setGbDgsAfterProfitSubtotal(newAfterProfitSubtotal.toString());

                BigDecimal newSellingSubtotal = toBigDecimal(stock.getGbDgsSellingPrice(), "0").multiply(myChangeWeight);
                salesSubtotal = newSellingSubtotal.add(toBigDecimal(stock.getGbDgsProduceSellingSubtotal(), "0"));
                stock.setGbDgsProduceSellingSubtotal(salesSubtotal.toString());
                BigDecimal add = scaleWeightValue(toBigDecimal(stock.getGbDgsProfitWeight(), "0").add(myChangeWeight));
                stock.setGbDgsProfitWeight(weightToStore(add));
            }

            reduceEntity = addDepGoodsStockReduceEntity(stock, what, myChangeWeight, myChangeSubtotal);
            subscribeDepDisGoodsTotal(myChangeWeight, myChangeSubtotal, stock.getGbDgsGbDepDisGoodsId());
        }

        if (GbDepGoodsStockAdjustKind.RETURN.equals(what)) {
            myChangeWeight = scaleWeightInput(stock.getGbDgsMyReturnWeight());
            myChangeSubtotal = scaleSubtotalValue(myChangeWeight.multiply(costPrice));

            if (!"-1".equals(stock.getGbDgsSellingPrice())) {
                BigDecimal stockAfterProfitSubtotal = toBigDecimal(stock.getGbDgsAfterProfitSubtotal(), "0");
                profitSubtotal = scaleProfitAmount(stockAfterProfitSubtotal.subtract(myChangeSubtotal));
                stock.setGbDgsAfterProfitSubtotal(profitSubtotal.toString());
            }

            BigDecimal allWeight = scaleWeightValue(toBigDecimal(stock.getGbDgsReturnWeight(), "0").add(myChangeWeight));
            BigDecimal allSubtotal = scaleSubtotalValue(toBigDecimal(stock.getGbDgsReturnSubtotal(), "0").add(myChangeSubtotal));
            stock.setGbDgsReturnWeight(weightToStore(allWeight));
            stock.setGbDgsReturnSubtotal(subtotalToStore(allSubtotal));

            reduceEntity = addDepGoodsStockReduceEntity(stock, what, myChangeWeight, myChangeSubtotal);
            subscribeDepDisGoodsTotal(myChangeWeight, myChangeSubtotal, stock.getGbDgsGbDepDisGoodsId());
        }

        if (GbDepGoodsStockAdjustKind.EMPLOYEE_MEAL.equals(what)) {
            myChangeWeight = scaleWeightInput(stock.getGbDgsMyEmployeeMealWeight());
            myChangeSubtotal = scaleSubtotalValue(myChangeWeight.multiply(costPrice));

            reduceEntity = addDepGoodsStockReduceEntity(stock, what, myChangeWeight, myChangeSubtotal);
            subscribeDepDisGoodsTotal(myChangeWeight, myChangeSubtotal, stock.getGbDgsGbDepDisGoodsId());
        }

        if (!GbDepGoodsStockAdjustKind.WASTE.equals(what)) {
            String restWeightStr = stock.getGbDgsRestWeight();
            BigDecimal restWeight = (restWeightStr != null && !restWeightStr.trim().isEmpty())
                    ? new BigDecimal(restWeightStr) : BigDecimal.ZERO;
            BigDecimal newRestWeight = scaleWeightValue(restWeight.subtract(myChangeWeight));
            if (newRestWeight.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException("库存不足");
            }
            BigDecimal newRestSubtotal = scaleSubtotalValue(newRestWeight.multiply(costPrice));
            stock.setGbDgsRestWeight(weightToStore(newRestWeight));
            stock.setGbDgsRestSubtotal(subtotalToStore(newRestSubtotal));
        }

        if (GbDepGoodsStockAdjustKind.WASTE.equals(what)) {
            BigDecimal wasteWeight = scaleWeightInput(stock.getGbDgsMyWasteWeight());
            BigDecimal wasteSubtotal = scaleSubtotalValue(wasteWeight.multiply(costPrice));

            BigDecimal produceWeight = scaleWeightInput(stock.getGbDgsMyProduceWeight());
            BigDecimal produceSubtotal = scaleSubtotalValue(produceWeight.multiply(costPrice));
            BigDecimal allWeightProduce = scaleWeightValue(toBigDecimal(stock.getGbDgsProduceWeight(), "0").add(produceWeight));
            BigDecimal allSubtotalProduce = scaleSubtotalValue(toBigDecimal(stock.getGbDgsProduceSubtotal(), "0").add(produceSubtotal));
            myChangeWeight = scaleWeightValue(wasteWeight.add(produceWeight));
            myChangeSubtotal = scaleSubtotalValue(wasteSubtotal.add(produceSubtotal));
            if (!"-1".equals(stock.getGbDgsSellingPrice())) {
                BigDecimal gbDgsBetweenPrice = toBigDecimal(stock.getGbDgsBetweenPrice(), "0");
                BigDecimal newProfitSubtotal = scaleProfitAmount(gbDgsBetweenPrice.multiply(produceWeight));
                profitSubtotal = scaleProfitAmount(toBigDecimal(stock.getGbDgsProfitSubtotal(), "0").add(newProfitSubtotal));
                stock.setGbDgsProfitSubtotal(profitSubtotal.toString());
                BigDecimal stockAfterProfitSubtotal = toBigDecimal(stock.getGbDgsAfterProfitSubtotal(), "0");
                newAfterProfitSubtotal = scaleProfitAmount(stockAfterProfitSubtotal.add(newProfitSubtotal).subtract(wasteSubtotal));
                stock.setGbDgsAfterProfitSubtotal(newAfterProfitSubtotal.toString());

                BigDecimal newSellingSubtotal = toBigDecimal(stock.getGbDgsSellingPrice(), "0").multiply(produceWeight);
                salesSubtotal = newSellingSubtotal.add(toBigDecimal(stock.getGbDgsProduceSellingSubtotal(), "0"));
                stock.setGbDgsProduceSellingSubtotal(salesSubtotal.toString());
                BigDecimal add = scaleWeightValue(toBigDecimal(stock.getGbDgsProfitWeight(), "0").add(produceWeight));
                stock.setGbDgsProfitWeight(weightToStore(add));
            }

            stock.setGbDgsWasteWeight(weightToStore(wasteWeight));
            stock.setGbDgsWasteSubtotal(subtotalToStore(wasteSubtotal));
            stock.setGbDgsProduceWeight(weightToStore(allWeightProduce));
            stock.setGbDgsProduceSubtotal(subtotalToStore(allSubtotalProduce));
            stock.setGbDgsRestWeight("0");
            stock.setGbDgsRestSubtotal("0.0");

            long nowTimestamp = System.currentTimeMillis();
            stock.setGbDgsDoWasteFullTime(String.valueOf(nowTimestamp));

            subscribeDepDisGoodsTotal(myChangeWeight, myChangeSubtotal, stock.getGbDgsGbDepDisGoodsId());

            addDepGoodsStockReduceEntity(stock, what, wasteWeight, wasteSubtotal);

            if (produceWeight.compareTo(BigDecimal.ZERO) > 0) {
                addDepGoodsStockReduceEntity(stock, GbDepGoodsStockAdjustKind.PRODUCE, produceWeight, produceSubtotal);
            }
        }

        stock.setGbDgsInventoryFullTime(fullTimeSeconds());
        stock.setGbDgsInventoryDate(formatWhatDay(0));
        stock.setGbDgsInventoryWeek(getWeekOfYear(0).toString());
        stock.setGbDgsInventoryMonth(formatWhatMonth(0));
        stock.setGbDgsInventoryYear(formatWhatYear(0));

        if (stock.getGbDgsRestWeightShowStandard() != null && !stock.getGbDgsRestWeightShowStandard().trim().isEmpty()) {
            if (toBigDecimal(stock.getGbDgsRestWeightShowStandard(), "0").compareTo(BigDecimal.ZERO) > 0) {
                Integer gbDgsGbDepDisGoodsId = stock.getGbDgsGbDepDisGoodsId();
                GbDepartmentDisGoodsEntity departmentDisGoodsEntity = gbDepartmentDisGoodsService.getById(gbDgsGbDepDisGoodsId);
                BigDecimal decimal = toBigDecimal(departmentDisGoodsEntity.getGbDdgShowStandardScale(), "1");
                BigDecimal myChangeWeightScale = myChangeWeight.divide(
                        decimal, GbConstants.StockLedger.WEIGHT_SCALE, RoundingMode.HALF_UP);
                BigDecimal decimal1 = scaleWeightValue(
                        toBigDecimal(stock.getGbDgsRestWeightShowStandard(), "0").subtract(myChangeWeightScale));
                stock.setGbDgsRestWeightShowStandard(weightToStore(decimal1));
                stock.setGbDgsRestWeightShowStandardName(departmentDisGoodsEntity.getGbDdgShowStandardName());
            }
        }

        stockService.updateById(stock);

        if (stock.getGbDgsWeightGoodsId() != null && !GbDepGoodsStockAdjustKind.PRODUCE.equals(what)) {
            updateWeightGoodsData(stock, what, myChangeWeight);
        }

        return reduceEntity;
    }

    private void restoreBatchRestWeightShowStandard(GbDepartmentGoodsStockEntity stockEntity, BigDecimal revertWeight) {
        if (revertWeight == null || revertWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (stockEntity.getGbDgsRestWeightShowStandard() == null
                || stockEntity.getGbDgsRestWeightShowStandard().trim().isEmpty()) {
            return;
        }
        Integer depDisGoodsId = stockEntity.getGbDgsGbDepDisGoodsId();
        if (depDisGoodsId == null) {
            return;
        }
        GbDepartmentDisGoodsEntity depDisGoods = gbDepartmentDisGoodsService.getById(depDisGoodsId);
        if (depDisGoods == null) {
            return;
        }
        BigDecimal scale = toBigDecimal(depDisGoods.getGbDdgShowStandardScale(), "1");
        BigDecimal scaleDelta = revertWeight.divide(scale, GbConstants.StockLedger.WEIGHT_SCALE, RoundingMode.HALF_UP);
        BigDecimal restored = scaleWeightValue(
                toBigDecimal(stockEntity.getGbDgsRestWeightShowStandard(), "0").add(scaleDelta));
        stockEntity.setGbDgsRestWeightShowStandard(weightToStore(restored));
        stockEntity.setGbDgsRestWeightShowStandardName(depDisGoods.getGbDdgShowStandardName());
    }

    private BigDecimal toBigDecimal(String value, String defaultVal) {
        if (value == null || value.trim().isEmpty()) {
            return new BigDecimal(defaultVal);
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return new BigDecimal(defaultVal);
        }
    }

    private void updateWeightGoodsData(GbDepartmentGoodsStockEntity stock, String what, BigDecimal myChangeWeight) {
        // TODO: 实现重量商品数据更新
    }



    private GbDepartmentGoodsStockReduceEntity addDepGoodsStockReduceEntity(
            GbDepartmentGoodsStockEntity stock, String canonicalKind,
            BigDecimal myChangeWeight, BigDecimal myChangeSubtotal) {

        GbDepartmentGoodsStockReduceEntity reduceEntity = new GbDepartmentGoodsStockReduceEntity();
        reduceEntity.setGbDgsrGbDistributerId(stock.getGbDgsGbDistributerId());
        reduceEntity.setGbDgsrGbDepartmentId(stock.getGbDgsGbDepartmentId());
        reduceEntity.setGbDgsrGbDepartmentFatherId(stock.getGbDgsGbDepartmentFatherId());
        reduceEntity.setGbDgsrGbDisGoodsId(stock.getGbDgsGbDisGoodsId());
        reduceEntity.setGbDgsrGbDisGoodsFatherId(stock.getGbDgsGbDisGoodsFatherId());
        reduceEntity.setGbDgsrGbDisGoodsGrandId(stock.getGbDgsGbDisGoodsGrandId());
        reduceEntity.setGbDgsrGbDisGoodsGreatId(stock.getGbDgsGbDisGoodsGreatId());
        reduceEntity.setGbDgsrGbDepDisGoodsId(stock.getGbDgsGbDepDisGoodsId());
        reduceEntity.setGbDgsrGbGoodsStockId(stock.getGbDepartmentGoodsStockId());
        reduceEntity.setGbDgsrStatus(0);
        reduceEntity.setGbDgsrFullTime(fullTimeSeconds());
        reduceEntity.setGbDgsrDate(formatWhatDay(0));
        reduceEntity.setGbDgsrWeek(getWeekOfYear(0).toString());
        reduceEntity.setGbDgsrMonth(formatWhatMonth(0));
        reduceEntity.setGbDgsrUserId(stock.getGbDgsReduceWeightUserId());
        reduceEntity.setGbDgsrStockNxSupplierId(stock.getGbDgsNxSupplierId());
        reduceEntity.setGbDgsrGbPurGoodsId(stock.getGbDgsGbPurGoodsId());
        reduceEntity.setGbDgsrStockPurUserId(stock.getGbDgsPurUserId());

        reduceEntity.setGbDgsrWeight(weightToStore(myChangeWeight));
        reduceEntity.setGbDgsrSubtotal(subtotalToStore(myChangeSubtotal));

        Integer reduceType = GbDepGoodsStockAdjustKind.toStockReduceType(canonicalKind);
        if (reduceType == null) {
            throw new IllegalStateException(GbDepGoodsStockAdjustKind.invalidKindMessage());
        }
        reduceEntity.setGbDgsrType(reduceType);
        if (GbDepGoodsStockAdjustKind.RETURN.equals(canonicalKind)) {
            reduceEntity.setGbDgsrUserId(stock.getGbDgsReturnUserId());
        } else if (GbDepGoodsStockAdjustKind.WASTE.equals(canonicalKind)) {
            reduceEntity.setGbDgsrUserId(stock.getGbDgsReturnUserId());
        }

        gbDepartmentStockReduceService.save(reduceEntity);
        return reduceEntity;
    }

    private GbDepartmentDisGoodsEntity subscribeDepDisGoodsTotal(BigDecimal weight, BigDecimal subtotal, Integer depDisGoodsId) {
        GbDepartmentDisGoodsEntity depDisGoodsEntity = gbDepartmentDisGoodsService.getById(depDisGoodsId);
        BigDecimal weightB = new BigDecimal(depDisGoodsEntity.getGbDdgStockTotalWeight()).subtract(weight);
        BigDecimal subtotalB = new BigDecimal(depDisGoodsEntity.getGbDdgStockTotalSubtotal()).subtract(subtotal);
        if (new BigDecimal(depDisGoodsEntity.getGbDdgShowStandardScale()).compareTo(new BigDecimal(0)) == 1) {
            BigDecimal showScale = new BigDecimal(depDisGoodsEntity.getGbDdgShowStandardScale());
            BigDecimal showWeight = weightB.divide(showScale, GbConstants.StockLedger.WEIGHT_SCALE, RoundingMode.HALF_UP);
            depDisGoodsEntity.setGbDdgShowStandardWeight(weightToStore(showWeight));
        }
        depDisGoodsEntity.setGbDdgStockTotalSubtotal(
                subtotalB.setScale(GbConstants.StockLedger.DEP_AGG_SUBTOTAL_SCALE, RoundingMode.HALF_UP).toString());
        depDisGoodsEntity.setGbDdgStockTotalWeight(weightToStore(weightB));
        if (weightB.compareTo(new BigDecimal(0)) == 0) {
            depDisGoodsEntity.setGbDdgStockTotalSubtotal("0.0");
        }

        gbDepartmentDisGoodsService.updateById(depDisGoodsEntity);
        return depDisGoodsEntity;
    }

    /** 与原 {@code GbDepartmentGoodsStockServiceImpl} 一致：按自然周取周序号。 */
    private Integer getWeekOfYear(int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, day);
        return calendar.get(Calendar.WEEK_OF_YEAR);
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

    /**
     * 与 {@code depGetDepGoodsGbPage} 相同的数据源 {@link com.nongxinle.service.GbDepartmentDisGoodsService#depQueryDepGoodsWithOrderForAi}，
     * 保证库存调整/撤销后返回的 {@link GbDepartmentDisGoodsEntity} 含订单、库存批次、规格等与分页列表一致。
     */
    private GbDepartmentDisGoodsEntity loadDepDisGoodsForDepGoodsPageShape(Integer depDisGoodsId, Integer depId) {
        if (depDisGoodsId == null || depId == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("depId", depId);
        map.put("pull", 0);
        map.put("status", 4);
        map.put("depDisGoodsId", depDisGoodsId);
        map.put("limit", 1);
        map.put("offset", 0);
        List<GbDepartmentDisGoodsEntity> list = gbDepartmentDisGoodsService.depQueryDepGoodsWithOrderForAi(map);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }
}
