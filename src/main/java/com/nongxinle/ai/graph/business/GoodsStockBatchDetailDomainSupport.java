package com.nongxinle.ai.graph.business;

import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockReduceEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDepartmentGoodsStockQueryService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 指定商品当前仍有剩余的库存批次事实（不受销量基线/统计时间窗限制）。 */
@Service
@RequiredArgsConstructor
public class GoodsStockBatchDetailDomainSupport {

    public static final String PAYLOAD_KEY = "goodsStockBatchDetail";

    private static final BigDecimal BALANCE_TOLERANCE = new BigDecimal("0.0001");

    private final GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    private final GbDepartmentGoodsStockQueryService gbDepartmentGoodsStockQueryService;
    private final GbDistributerGoodsService gbDistributerGoodsService;

    public Map<String, Object> buildPayload(
            int disId,
            Integer depFatherId,
            Integer disGoodsId,
            String goodsNameHint,
            LinkedHashMap<String, Object> debug) {
        Integer targetGoodsId = resolveDisGoodsId(disId, disGoodsId, goodsNameHint, debug);
        if (targetGoodsId == null) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("status", "NOT_FOUND");
            out.put("goodsNameHint", goodsNameHint);
            return out;
        }

        GbDistributerGoodsEntity goodsEntity = gbDistributerGoodsService.queryObject(targetGoodsId);
        String goodsName =
                goodsEntity != null && StringUtils.hasText(goodsEntity.getGbDgGoodsName())
                        ? goodsEntity.getGbDgGoodsName().trim()
                        : (StringUtils.hasText(goodsNameHint) ? goodsNameHint.trim() : null);

        List<GbDepartmentGoodsStockEntity> batches = queryActiveBatches(disId, depFatherId, targetGoodsId);
        gbDepartmentGoodsStockQueryService.enrichStockBatchReduceLists(batches);

        List<Map<String, Object>> batchRows = new ArrayList<>();
        int balanceMismatchCount = 0;
        for (GbDepartmentGoodsStockEntity batch : batches) {
            if (batch == null || batch.getGbDepartmentGoodsStockId() == null) {
                continue;
            }
            BigDecimal rest = parseQty(batch.getGbDgsRestWeight());
            if (rest.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Map<String, Object> row = projectBatchRow(batch);
            batchRows.add(row);
            if (Boolean.FALSE.equals(row.get("balanceOk"))) {
                balanceMismatchCount++;
            }
        }

        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("status", "OK");
        out.put("disGoodsId", targetGoodsId);
        out.put("goodsName", goodsName);
        out.put("batchRows", batchRows);
        out.put("batchesByUnit", groupBatchRowsByUnit(batchRows));
        out.put("activeBatchCount", batchRows.size());
        if (batchRows.isEmpty()) {
            out.put("knownGap", "no_active_stock_batch");
        }
        if (balanceMismatchCount > 0) {
            out.put("balanceMismatchCount", balanceMismatchCount);
        }
        if (debug != null) {
            debug.put("batchDetailDisGoodsId", targetGoodsId);
            debug.put("batchDetailRowCount", batchRows.size());
        }
        return out;
    }

    private List<GbDepartmentGoodsStockEntity> queryActiveBatches(
            int disId, Integer depFatherId, int disGoodsId) {
        Map<String, Object> q = new HashMap<>();
        q.put("disId", disId);
        if (depFatherId != null) {
            q.put("depFatherId", depFatherId);
        }
        q.put("disGoodsId", disGoodsId);
        q.put("dayuStatus", -1);
        q.put("restWeight", 0);
        List<GbDepartmentGoodsStockEntity> rows = gbDepartmentGoodsStockService.queryGoodsStockByParams(q);
        return rows == null ? List.of() : rows;
    }

    private static Map<String, Object> projectBatchRow(GbDepartmentGoodsStockEntity batch) {
        BigDecimal inbound = parseQty(batch.getGbDgsWeight());
        BigDecimal produce = parseQty(batch.getGbDgsProduceWeight());
        BigDecimal waste = parseQty(batch.getGbDgsWasteWeight());
        BigDecimal loss = parseQty(batch.getGbDgsLossWeight());
        BigDecimal ret = parseQty(batch.getGbDgsReturnWeight());
        BigDecimal employeeMeal = parseQty(batch.getGbDgsEmployeeMealWeight());
        BigDecimal stars = sumReduceWeightByType(
                batch.getGoodsStockReduceEntityList(), GbConstants.StockReduceType.STARS);
        BigDecimal rest = parseQty(batch.getGbDgsRestWeight());
        BigDecimal other = loss.add(ret).add(stars);

        BigDecimal consumed =
                produce.add(waste).add(loss).add(ret).add(employeeMeal).add(stars).add(rest);
        BigDecimal balanceDifference = inbound.subtract(consumed);
        boolean balanceOk = balanceDifference.abs().compareTo(BALANCE_TOLERANCE) <= 0;

        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("stockBatchId", batch.getGbDepartmentGoodsStockId());
        row.put("inboundDate", blankToNull(batch.getGbDgsDate()));
        row.put("inboundQty", formatQty(inbound));
        row.put("produceQty", formatQty(produce));
        row.put("wasteQty", formatQty(waste));
        row.put("lossQty", formatQty(loss));
        row.put("returnQty", formatQty(ret));
        row.put("employeeMealQty", formatQty(employeeMeal));
        row.put("otherConsumedQty", formatQty(other));
        row.put("restQty", formatQty(rest));
        row.put("unitPrice", blankToNull(batch.getGbDgsPrice()));
        row.put(
                "unit",
                StringUtils.hasText(batch.getGbDgsRestWeightShowStandardName())
                        ? batch.getGbDgsRestWeightShowStandardName().trim()
                        : "");
        row.put("balanceDifference", formatQty(balanceDifference));
        row.put("balanceOk", balanceOk);
        if (batch.getGbDgsGbPurGoodsId() != null) {
            row.put("purchaseGoodsId", batch.getGbDgsGbPurGoodsId());
        }
        return row;
    }

    private static List<Map<String, Object>> groupBatchRowsByUnit(List<Map<String, Object>> batchRows) {
        Map<String, List<Map<String, Object>>> byUnit = new LinkedHashMap<>();
        for (Map<String, Object> row : batchRows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String unit = Objects.toString(row.get("unit"), "").trim();
            if (unit.isEmpty()) {
                unit = "—";
            }
            byUnit.computeIfAbsent(unit, k -> new ArrayList<>()).add(row);
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : byUnit.entrySet()) {
            LinkedHashMap<String, Object> g = new LinkedHashMap<>();
            g.put("unit", e.getKey());
            g.put("batchCount", e.getValue().size());
            g.put("batches", e.getValue());
            groups.add(g);
        }
        return groups;
    }

    private static BigDecimal sumReduceWeightByType(
            List<GbDepartmentGoodsStockReduceEntity> reduces, Integer type) {
        if (reduces == null || reduces.isEmpty() || type == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (GbDepartmentGoodsStockReduceEntity r : reduces) {
            if (r == null || !type.equals(r.getGbDgsrType())) {
                continue;
            }
            sum = sum.add(parseQty(r.getGbDgsrWeight()));
        }
        return sum;
    }

    private Integer resolveDisGoodsId(
            int disId, Integer disGoodsId, String goodsNameHint, LinkedHashMap<String, Object> debug) {
        if (disGoodsId != null && disGoodsId > 0) {
            GbDistributerGoodsEntity g = gbDistributerGoodsService.queryObject(disGoodsId);
            if (g != null) {
                return disGoodsId;
            }
            if (debug != null) {
                debug.put("batchDetailAnchorDisGoodsIdRejected", disGoodsId);
            }
        }
        if (!StringUtils.hasText(goodsNameHint)) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        String hint = goodsNameHint.trim();
        if (containsHan(hint)) {
            map.put("searchStr", hint);
        } else {
            map.put("searchPinyin", hint);
        }
        List<GbDistributerGoodsEntity> hits = gbDistributerGoodsService.queryGbDisGoodsQuickSearchStr(map);
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        if (hits.size() == 1 && hits.get(0).getGbDistributerGoodsId() != null) {
            return hits.get(0).getGbDistributerGoodsId();
        }
        return null;
    }

    private static boolean containsHan(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeScript.of(s.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal parseQty(String raw) {
        return GbDepartmentGoodsStockReduceSupport.parseGoodsAmountString(raw);
    }

    private static String formatQty(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        return v.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
