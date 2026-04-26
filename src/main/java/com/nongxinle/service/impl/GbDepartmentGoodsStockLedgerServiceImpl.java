package com.nongxinle.service.impl;

import com.nongxinle.dto.GbDepGoodsStockAdjustRequest;
import com.nongxinle.dto.GbDepGoodsStockAdjustResult;
import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.entity.GbDepartmentGoodsDailyEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockReduceEntity;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.service.GbDepartmentDisGoodsService;
import com.nongxinle.service.GbDepartmentGoodsDailyService;
import com.nongxinle.service.GbDepartmentGoodsStockLedgerService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.utils.DateUtils;
import com.nongxinle.utils.GbConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
        String what = normalizeWhat(request.getKind());
        if (what.isEmpty()) {
            return GbDepGoodsStockAdjustResult.error(500, "kind 无效，应为 produce、loss、return、waste");
        }

        GbDepartmentGoodsStockEntity fromDb = stockService.getById(stockId);
        if (fromDb == null) {
            return GbDepGoodsStockAdjustResult.error(-1, "请刷新数据");
        }
        if (bd(fromDb.getGbDgsRestWeight()).compareTo(BigDecimal.ZERO) == 0) {
            return GbDepGoodsStockAdjustResult.error(-1, "请刷新数据");
        }

        if ("return".equals(what)) {
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

        GbDepartmentGoodsStockReduceEntity reduceEntity = changeDepartmentStock(stock, what);

        GbDepartmentDisGoodsEntity disGoods = loadDepDisGoodsForDepGoodsPageShape(fromDb.getGbDgsGbDepDisGoodsId(), stock.getGbDgsGbDepartmentId());

        Map<String, Object> data = new HashMap<>();
        data.put("disGoods", disGoods);
        if ("loss".equals(what) || "return".equals(what)) {
            data.put("id", reduceEntity.getGbDepartmentGoodsStockReduceId());
        }
        return GbDepGoodsStockAdjustResult.success(data);
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
            BigDecimal newProduceWeight = gbDgsProduceWeight.subtract(reduceBusinessWeight).setScale(1, RoundingMode.HALF_UP);
            BigDecimal newProduceSubtotal = gbDgsProduceSubtotal.subtract(reduceBusinessSubtotal).setScale(1, RoundingMode.HALF_UP);
            stockEntity.setGbDgsProduceWeight(newProduceWeight.toString());
            stockEntity.setGbDgsProduceSubtotal(newProduceSubtotal.toString());
        }
        if (Objects.equals(gbDgsrType, GbConstants.StockReduceType.LOSS)) {
            reduceBusinessWeight = bd(reduceEntity.getGbDgsrWeight());
            reduceBusinessSubtotal = bd(reduceEntity.getGbDgsrSubtotal());
            BigDecimal gbDgsLossWeight = bd(stockEntity.getGbDgsLossWeight());
            BigDecimal gbDgsLossSubtotal = bd(stockEntity.getGbDgsLossSubtotal());
            BigDecimal newLossWeight = gbDgsLossWeight.subtract(reduceBusinessWeight).setScale(1, RoundingMode.HALF_UP);
            BigDecimal newLossSubtotal = gbDgsLossSubtotal.subtract(reduceBusinessSubtotal).setScale(1, RoundingMode.HALF_UP);
            stockEntity.setGbDgsLossWeight(newLossWeight.toString());
            stockEntity.setGbDgsLossSubtotal(newLossSubtotal.toString());
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
            BigDecimal newReturnWeight = gbDgsReturnWeight.subtract(reduceBusinessWeight).setScale(1, RoundingMode.HALF_UP);
            BigDecimal newReturnSubtotal = gbDgsReturnSubtotal.subtract(reduceBusinessSubtotal).setScale(1, RoundingMode.HALF_UP);
            stockEntity.setGbDgsReturnWeight(newReturnWeight.toString());
            stockEntity.setGbDgsReturnSubtotal(newReturnSubtotal.toString());

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

        BigDecimal newRestWeight = sRestWeight.add(reduceBusinessWeight).setScale(1, RoundingMode.HALF_UP);
        BigDecimal newRestSubtotal = sRestSubtotal.add(reduceBusinessSubtotal).setScale(1, RoundingMode.HALF_UP);
        stockEntity.setGbDgsRestWeight(newRestWeight.toString());
        stockEntity.setGbDgsRestSubtotal(newRestSubtotal.toString());
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
        BigDecimal myChangeWeight = toBigDecimal(stock.getGbDgsMyProduceWeight(), "0").setScale(1, RoundingMode.HALF_UP);
        BigDecimal costPrice = toBigDecimal(stock.getGbDgsPrice(), "0");
        BigDecimal myChangeSubtotal = myChangeWeight.multiply(costPrice).setScale(2, RoundingMode.HALF_UP);

        BigDecimal allWeight = toBigDecimal(stock.getGbDgsProduceWeight(), "0").add(myChangeWeight).setScale(1, RoundingMode.HALF_UP);
        BigDecimal allSubtotal = toBigDecimal(stock.getGbDgsProduceSubtotal(), "0").add(myChangeSubtotal).setScale(1, RoundingMode.HALF_UP);
        stock.setGbDgsProduceWeight(allWeight.toString());
        stock.setGbDgsProduceSubtotal(allSubtotal.toString());

        String sellingPrice = stock.getGbDgsSellingPrice();
        if (sellingPrice != null && !sellingPrice.equals("-1")) {
            BigDecimal gbDgsBetweenPrice = toBigDecimal(stock.getGbDgsBetweenPrice(), "0");
            BigDecimal newProfitSubtotal = gbDgsBetweenPrice.multiply(myChangeWeight).setScale(1, RoundingMode.HALF_UP);
            BigDecimal profitSubtotal = toBigDecimal(stock.getGbDgsProfitSubtotal(), "0").add(newProfitSubtotal).setScale(1, RoundingMode.HALF_UP);
            stock.setGbDgsProfitSubtotal(profitSubtotal.toString());
            BigDecimal stockAfterProfitSubtotal = toBigDecimal(stock.getGbDgsAfterProfitSubtotal(), "0");
            BigDecimal newAfterProfitSubtotal = stockAfterProfitSubtotal.add(newProfitSubtotal).setScale(1, RoundingMode.HALF_UP);
            stock.setGbDgsAfterProfitSubtotal(newAfterProfitSubtotal.toString());

            BigDecimal newSellingSubtotal = toBigDecimal(stock.getGbDgsSellingPrice(), "0").multiply(myChangeWeight);
            BigDecimal salesSubtotal = newSellingSubtotal.add(toBigDecimal(stock.getGbDgsProduceSellingSubtotal(), "0"));
            stock.setGbDgsProduceSellingSubtotal(salesSubtotal.toString());
            BigDecimal add = toBigDecimal(stock.getGbDgsProfitWeight(), "0").add(myChangeWeight);
            stock.setGbDgsProfitWeight(add.toString());
        }

        saveJjProduceClearReduceRow(stock, myChangeWeight, myChangeSubtotal, orderUserId);
        subscribeDepDisGoodsTotal(myChangeWeight, myChangeSubtotal, stock.getGbDgsGbDepDisGoodsId());

        BigDecimal restWeight = toBigDecimal(stock.getGbDgsRestWeight(), "0");
        BigDecimal newRestWeight = restWeight.subtract(myChangeWeight).setScale(1, RoundingMode.HALF_UP);
        BigDecimal newRestSubtotal = newRestWeight.multiply(costPrice).setScale(1, RoundingMode.HALF_UP);
        stock.setGbDgsRestWeight(newRestWeight.toString());
        stock.setGbDgsRestSubtotal(newRestSubtotal.toString());
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
                    BigDecimal myChangeWeightScale = myChangeWeight.divide(decimal, 1, RoundingMode.HALF_UP);
                    BigDecimal decimal1 = toBigDecimal(stock.getGbDgsRestWeightShowStandard(), "0").subtract(myChangeWeightScale).setScale(1, RoundingMode.HALF_UP);
                    stock.setGbDgsRestWeightShowStandard(decimal1.toString());
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
        reduceEntity.setGbDgsrWeight(myChangeWeight.toString());
        reduceEntity.setGbDgsrSubtotal(myChangeSubtotal.toString());
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
        depDisGoodsEntity.setGbDdgStockTotalSubtotal(subtotalB.setScale(1, RoundingMode.HALF_UP).toString());
        depDisGoodsEntity.setGbDdgStockTotalWeight(weightB.setScale(1, RoundingMode.HALF_UP).toString());
        gbDepartmentDisGoodsService.updateById(depDisGoodsEntity);
    }

    private static String fullTimeSeconds() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private static String normalizeWhat(String kind) {
        if (kind == null || kind.trim().isEmpty()) {
            return "";
        }
        return kind.trim().toLowerCase(Locale.ROOT);
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

    private GbDepartmentGoodsStockReduceEntity changeDepartmentStock(GbDepartmentGoodsStockEntity stock, String what) {
        GbDepartmentGoodsStockReduceEntity reduceEntity = new GbDepartmentGoodsStockReduceEntity();
        log.debug("changeDepartmentStock what={}", what);
        BigDecimal myChangeWeight = new BigDecimal("0");
        BigDecimal myChangeSubtotal = new BigDecimal(0);

        BigDecimal newAfterProfitSubtotal = new BigDecimal(0);
        BigDecimal salesSubtotal = new BigDecimal(0);
        BigDecimal profitSubtotal = new BigDecimal(0);

        Integer gbDgsGbDisGoodsId = stock.getGbDgsGbDisGoodsId();
        GbDistributerGoodsEntity distributerGoodsEntity = disGoodsService.getById(gbDgsGbDisGoodsId);
        Integer gbDgGoodsInventoryType = distributerGoodsEntity.getGbDgGoodsInventoryType();

        String priceStr = stock.getGbDgsPrice();
        BigDecimal costPrice = (priceStr != null && !priceStr.trim().isEmpty()) ? new BigDecimal(priceStr) : BigDecimal.ZERO;

        if (what.equals("loss")) {
            myChangeWeight = toBigDecimal(stock.getGbDgsMyLossWeight(), "0").setScale(1, RoundingMode.HALF_UP);
            myChangeSubtotal = myChangeWeight.multiply(costPrice).setScale(2, RoundingMode.HALF_UP);

            if (stock.getGbDgsSellingPrice() != null && !stock.getGbDgsSellingPrice().trim().isEmpty() && !stock.getGbDgsSellingPrice().equals("-1")) {
                BigDecimal stockAfterProfitSubtotal = toBigDecimal(stock.getGbDgsAfterProfitSubtotal(), "0");
                newAfterProfitSubtotal = stockAfterProfitSubtotal.subtract(myChangeSubtotal).setScale(1, RoundingMode.HALF_UP);
                stock.setGbDgsAfterProfitSubtotal(newAfterProfitSubtotal.toString());
            }

            BigDecimal allWeight = toBigDecimal(stock.getGbDgsLossWeight(), "0").add(myChangeWeight).setScale(1, RoundingMode.HALF_UP);
            BigDecimal allSubtotal = toBigDecimal(stock.getGbDgsLossSubtotal(), "0").add(myChangeSubtotal).setScale(1, RoundingMode.HALF_UP);
            stock.setGbDgsLossWeight(allWeight.toString());
            stock.setGbDgsLossSubtotal(allSubtotal.toString());

            reduceEntity = addDepGoodsStockReduceEntity(stock, "loss", gbDgGoodsInventoryType, myChangeWeight, myChangeSubtotal);
            subscribeDepDisGoodsTotal(myChangeWeight, myChangeSubtotal, stock.getGbDgsGbDepDisGoodsId());
        }

        if (what.equals("produce")) {
            myChangeWeight = toBigDecimal(stock.getGbDgsMyProduceWeight(), "0").setScale(1, RoundingMode.HALF_UP);
            myChangeSubtotal = myChangeWeight.multiply(costPrice).setScale(2, RoundingMode.HALF_UP);

            BigDecimal allWeight = toBigDecimal(stock.getGbDgsProduceWeight(), "0").add(myChangeWeight).setScale(1, RoundingMode.HALF_UP);
            BigDecimal allSubtotal = toBigDecimal(stock.getGbDgsProduceSubtotal(), "0").add(myChangeSubtotal).setScale(1, RoundingMode.HALF_UP);
            stock.setGbDgsProduceWeight(allWeight.toString());
            stock.setGbDgsProduceSubtotal(allSubtotal.toString());

            if (!"-1".equals(stock.getGbDgsSellingPrice())) {
                BigDecimal gbDgsBetweenPrice = toBigDecimal(stock.getGbDgsBetweenPrice(), "0");
                BigDecimal newProfitSubtotal = gbDgsBetweenPrice.multiply(myChangeWeight).setScale(1, RoundingMode.HALF_UP);
                profitSubtotal = toBigDecimal(stock.getGbDgsProfitSubtotal(), "0").add(newProfitSubtotal).setScale(1, RoundingMode.HALF_UP);
                stock.setGbDgsProfitSubtotal(profitSubtotal.toString());
                BigDecimal stockAfterProfitSubtotal = toBigDecimal(stock.getGbDgsAfterProfitSubtotal(), "0");
                newAfterProfitSubtotal = stockAfterProfitSubtotal.add(newProfitSubtotal).setScale(1, RoundingMode.HALF_UP);
                stock.setGbDgsAfterProfitSubtotal(newAfterProfitSubtotal.toString());

                BigDecimal newSellingSubtotal = toBigDecimal(stock.getGbDgsSellingPrice(), "0").multiply(myChangeWeight);
                salesSubtotal = newSellingSubtotal.add(toBigDecimal(stock.getGbDgsProduceSellingSubtotal(), "0"));
                stock.setGbDgsProduceSellingSubtotal(salesSubtotal.toString());
                BigDecimal add = toBigDecimal(stock.getGbDgsProfitWeight(), "0").add(myChangeWeight);
                stock.setGbDgsProfitWeight(add.toString());
            }

            reduceEntity = addDepGoodsStockReduceEntity(stock, "produce", gbDgGoodsInventoryType, myChangeWeight, myChangeSubtotal);
            subscribeDepDisGoodsTotal(myChangeWeight, myChangeSubtotal, stock.getGbDgsGbDepDisGoodsId());
        }

        if (what.equals("return")) {
            myChangeWeight = toBigDecimal(stock.getGbDgsMyReturnWeight(), "0").setScale(1, RoundingMode.HALF_UP);
            myChangeSubtotal = myChangeWeight.multiply(costPrice).setScale(2, RoundingMode.HALF_UP);

            if (!"-1".equals(stock.getGbDgsSellingPrice())) {
                BigDecimal stockAfterProfitSubtotal = toBigDecimal(stock.getGbDgsAfterProfitSubtotal(), "0");
                profitSubtotal = stockAfterProfitSubtotal.subtract(myChangeSubtotal).setScale(1, RoundingMode.HALF_UP);
                stock.setGbDgsAfterProfitSubtotal(profitSubtotal.toString());
            }

            BigDecimal allWeight = toBigDecimal(stock.getGbDgsReturnWeight(), "0").add(myChangeWeight).setScale(1, RoundingMode.HALF_UP);
            BigDecimal allSubtotal = toBigDecimal(stock.getGbDgsReturnSubtotal(), "0").add(myChangeSubtotal).setScale(1, RoundingMode.HALF_UP);
            stock.setGbDgsReturnWeight(allWeight.toString());
            stock.setGbDgsReturnSubtotal(allSubtotal.toString());

            reduceEntity = addDepGoodsStockReduceEntity(stock, what, gbDgGoodsInventoryType, myChangeWeight, myChangeSubtotal);
            subscribeDepDisGoodsTotal(myChangeWeight, myChangeSubtotal, stock.getGbDgsGbDepDisGoodsId());
        }

        String restWeightStr = stock.getGbDgsRestWeight();
        BigDecimal restWeight = (restWeightStr != null && !restWeightStr.trim().isEmpty()) ? new BigDecimal(restWeightStr) : BigDecimal.ZERO;
        BigDecimal newRestWeight = restWeight.subtract(myChangeWeight).setScale(1, RoundingMode.HALF_UP);
        BigDecimal newRestSubtotal = newRestWeight.multiply(costPrice).setScale(1, RoundingMode.HALF_UP);
        stock.setGbDgsRestWeight(newRestWeight.toString());
        stock.setGbDgsRestSubtotal(newRestSubtotal.toString());

        if (what.equals("waste")) {
            BigDecimal wasteWeight = toBigDecimal(stock.getGbDgsMyWasteWeight(), "0");
            BigDecimal wasteSubtotal = wasteWeight.multiply(costPrice).setScale(1, RoundingMode.HALF_UP);

            BigDecimal produceWeight = toBigDecimal(stock.getGbDgsMyProduceWeight(), "0").setScale(1, RoundingMode.HALF_UP);
            BigDecimal produceSubtotal = produceWeight.multiply(costPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal allWeightProduce = toBigDecimal(stock.getGbDgsProduceWeight(), "0").add(produceWeight).setScale(1, RoundingMode.HALF_UP);
            BigDecimal allSubtotalProduce = toBigDecimal(stock.getGbDgsProduceSubtotal(), "0").add(produceSubtotal).setScale(1, RoundingMode.HALF_UP);
            myChangeWeight = wasteWeight.add(produceWeight);
            myChangeSubtotal = wasteSubtotal.add(produceSubtotal);
            if (!"-1".equals(stock.getGbDgsSellingPrice())) {
                BigDecimal gbDgsBetweenPrice = toBigDecimal(stock.getGbDgsBetweenPrice(), "0");
                BigDecimal newProfitSubtotal = gbDgsBetweenPrice.multiply(produceWeight).setScale(1, RoundingMode.HALF_UP);
                profitSubtotal = toBigDecimal(stock.getGbDgsProfitSubtotal(), "0").add(newProfitSubtotal).setScale(1, RoundingMode.HALF_UP);
                stock.setGbDgsProfitSubtotal(profitSubtotal.toString());
                BigDecimal stockAfterProfitSubtotal = toBigDecimal(stock.getGbDgsAfterProfitSubtotal(), "0");
                newAfterProfitSubtotal = stockAfterProfitSubtotal.add(newProfitSubtotal).subtract(wasteSubtotal).setScale(1, RoundingMode.HALF_UP);
                stock.setGbDgsAfterProfitSubtotal(newAfterProfitSubtotal.toString());

                BigDecimal newSellingSubtotal = toBigDecimal(stock.getGbDgsSellingPrice(), "0").multiply(produceWeight);
                salesSubtotal = newSellingSubtotal.add(toBigDecimal(stock.getGbDgsProduceSellingSubtotal(), "0"));
                stock.setGbDgsProduceSellingSubtotal(salesSubtotal.toString());
                BigDecimal add = toBigDecimal(stock.getGbDgsProfitWeight(), "0").add(produceWeight);
                stock.setGbDgsProfitWeight(add.toString());
            }

            stock.setGbDgsWasteWeight(wasteWeight.toString());
            stock.setGbDgsWasteSubtotal(wasteSubtotal.toString());
            stock.setGbDgsProduceWeight(allWeightProduce.toString());
            stock.setGbDgsProduceSubtotal(allSubtotalProduce.toString());
            stock.setGbDgsRestWeight("0");
            stock.setGbDgsRestSubtotal("0.0");

            long nowTimestamp = System.currentTimeMillis();
            stock.setGbDgsDoWasteFullTime(String.valueOf(nowTimestamp));

            subscribeDepDisGoodsTotal(myChangeWeight, myChangeSubtotal, stock.getGbDgsGbDepDisGoodsId());

            addDepGoodsStockReduceEntity(stock, what, gbDgGoodsInventoryType, wasteWeight, wasteSubtotal);

            if (produceWeight.compareTo(BigDecimal.ZERO) > 0) {
                addDepGoodsStockReduceEntity(stock, "produce", gbDgGoodsInventoryType, produceWeight, produceSubtotal);
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
                BigDecimal myChangeWeightScale = myChangeWeight.divide(decimal, 1, RoundingMode.HALF_UP);
                BigDecimal decimal1 = toBigDecimal(stock.getGbDgsRestWeightShowStandard(), "0").subtract(myChangeWeightScale).setScale(1, RoundingMode.HALF_UP);
                stock.setGbDgsRestWeightShowStandard(decimal1.toString());
                stock.setGbDgsRestWeightShowStandardName(departmentDisGoodsEntity.getGbDdgShowStandardName());
            }
        }

        stockService.updateById(stock);

        if (stock.getGbDgsWeightGoodsId() != null && !what.equals("produce")) {
            updateWeightGoodsData(stock, what, myChangeWeight);
        }

        return reduceEntity;
    }

    private void updateWeightGoodsData(GbDepartmentGoodsStockEntity stock, String what, BigDecimal myChangeWeight) {
        // TODO: 实现重量商品数据更新
    }



    private GbDepartmentGoodsStockReduceEntity addDepGoodsStockReduceEntity(GbDepartmentGoodsStockEntity stock, String what, Integer inventoryType,
            BigDecimal myChangeWeight, BigDecimal myChangeSubtotal) {

        GbDepartmentGoodsStockReduceEntity reduceEntity = new GbDepartmentGoodsStockReduceEntity();
        reduceEntity.setGbDgsrGbDistributerId(stock.getGbDgsGbDistributerId());
        reduceEntity.setGbDgsrGbDepartmentId(stock.getGbDgsGbDepartmentId());
        reduceEntity.setGbDgsrGbDepartmentFatherId(stock.getGbDgsGbDepartmentFatherId());
        reduceEntity.setGbDgsrGbDisGoodsId(stock.getGbDgsGbDisGoodsId());
        reduceEntity.setGbDgsrGbDepDisGoodsId(stock.getGbDgsGbDepDisGoodsId());
        reduceEntity.setGbDgsrGbGoodsStockId(stock.getGbDepartmentGoodsStockId());
        reduceEntity.setGbDgsrFullTime(fullTimeSeconds());
        reduceEntity.setGbDgsrDate(formatWhatDay(0));
        reduceEntity.setGbDgsrWeek(getWeekOfYear(0).toString());
        reduceEntity.setGbDgsrMonth(formatWhatMonth(0));
        reduceEntity.setGbDgsrUserId(stock.getGbDgsReduceWeightUserId());

        reduceEntity.setGbDgsrWeight(myChangeWeight.toString());
        reduceEntity.setGbDgsrSubtotal(myChangeSubtotal.toString());

        if (what.equals("loss")) {
            reduceEntity.setGbDgsrType(GbConstants.StockReduceType.LOSS);
        } else if (what.equals("produce")) {
            reduceEntity.setGbDgsrType(GbConstants.StockReduceType.PRODUCTION);
        } else if (what.equals("return")) {
            reduceEntity.setGbDgsrType(GbConstants.StockReduceType.RETURN);
            reduceEntity.setGbDgsrUserId(stock.getGbDgsReturnUserId());
        } else if (what.equals("waste")) {
            reduceEntity.setGbDgsrType(GbConstants.StockReduceType.WASTE);
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
            BigDecimal showWeight = weightB.divide(showScale, 1, RoundingMode.HALF_UP);
            depDisGoodsEntity.setGbDdgShowStandardWeight(showWeight.toString());
        }
        depDisGoodsEntity.setGbDdgStockTotalSubtotal(subtotalB.setScale(1, RoundingMode.HALF_UP).toString());
        depDisGoodsEntity.setGbDdgStockTotalWeight(weightB.setScale(1, RoundingMode.HALF_UP).toString());
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
