package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GOODS 锚逐笔采购行：Entity → AnswerPlan / Card 共用 wire（Tool 层调用，Card 只读 Plan）。 */
public final class PurchaseGoodsAnchorLineRowSupport {

    public static final String SUPPLIER_DISPLAY_HAS_NAME = "HAS_SUPPLIER_NAME";
    public static final String SUPPLIER_DISPLAY_SELF_PURCHASE = "SELF_PURCHASE";
    public static final String SUPPLIER_DISPLAY_NO_SUPPLIER_NAME = "NO_SUPPLIER_NAME";
    public static final String SUPPLIER_DISPLAY_NO_SUPPLIER_RECORD = "NO_SUPPLIER_RECORD";

    private PurchaseGoodsAnchorLineRowSupport() {}

    public static List<Map<String, Object>> mapDistinctPurchaseLines(
            List<GbDistributerPurchaseGoodsEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<Integer, GbDistributerPurchaseGoodsEntity> byPurchaseId = new LinkedHashMap<>();
        for (GbDistributerPurchaseGoodsEntity entity : entities) {
            if (entity == null || entity.getGbDistributerPurchaseGoodsId() == null) {
                continue;
            }
            byPurchaseId.putIfAbsent(entity.getGbDistributerPurchaseGoodsId(), entity);
        }
        List<Map<String, Object>> out = new ArrayList<>(byPurchaseId.size());
        for (GbDistributerPurchaseGoodsEntity entity : byPurchaseId.values()) {
            Map<String, Object> row = mapPurchaseLine(entity);
            if (!row.isEmpty()) {
                out.add(row);
            }
        }
        return out;
    }

    public static String resolveDefaultUnit(List<Map<String, Object>> lineRows) {
        if (lineRows == null || lineRows.isEmpty()) {
            return null;
        }
        for (Map<String, Object> row : lineRows) {
            if (row == null) {
                continue;
            }
            Object unit = row.get("unit");
            if (unit != null && StringUtils.hasText(unit.toString())) {
                return unit.toString().trim();
            }
        }
        return null;
    }

    private static Map<String, Object> mapPurchaseLine(GbDistributerPurchaseGoodsEntity entity) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        if (entity.getGbDistributerPurchaseGoodsId() != null) {
            row.put("purchaseGoodsId", entity.getGbDistributerPurchaseGoodsId());
        }
        String purchaseDate = firstNonBlank(entity.getGbDpgStockFinishDate(), entity.getGbDpgPurchaseDate());
        if (StringUtils.hasText(purchaseDate)) {
            row.put("purchaseDate", purchaseDate.trim());
        }
        putIfHasText(row, "quantity", entity.getGbDpgBuyQuantity());
        putIfHasText(row, "unit", entity.getGbDpgStandard());
        putIfHasText(row, "unitPrice", entity.getGbDpgBuyPrice());
        putIfHasText(row, "amount", entity.getGbDpgBuySubtotal());
        applySourceAndPartyFields(row, entity);
        return row;
    }

    private static void applySourceAndPartyFields(
            LinkedHashMap<String, Object> row, GbDistributerPurchaseGoodsEntity entity) {
        Integer supplierId = entity.getGbDpgPurchaseNxSupplierId();
        if (supplierId != null && supplierId == -1) {
            row.put("purchaseSourceType", AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE);
            row.put("supplierDisplayStatus", SUPPLIER_DISPLAY_SELF_PURCHASE);
            row.put("supplierName", null);
            String purchaser = purchaserName(entity);
            if (StringUtils.hasText(purchaser)) {
                row.put("purchaserName", purchaser);
            } else {
                row.put("purchaserName", null);
                row.put("purchaserDisplayStatus", "NO_PURCHASER_NAME");
            }
            return;
        }
        if (supplierId != null && supplierId > 0) {
            row.put("purchaseSourceType", AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
            row.put("supplierId", supplierId);
            String supplierName = supplierName(entity);
            if (StringUtils.hasText(supplierName)) {
                row.put("supplierName", supplierName);
                row.put("supplierDisplayStatus", SUPPLIER_DISPLAY_HAS_NAME);
            } else {
                row.put("supplierName", null);
                row.put("supplierDisplayStatus", SUPPLIER_DISPLAY_NO_SUPPLIER_NAME);
            }
            String purchaser = purchaserName(entity);
            row.put("purchaserName", StringUtils.hasText(purchaser) ? purchaser : null);
            return;
        }
        row.put("purchaseSourceType", AiQuerySemanticLexicon.SOURCE_ALL);
        row.put("supplierName", null);
        row.put("supplierDisplayStatus", SUPPLIER_DISPLAY_NO_SUPPLIER_RECORD);
        String purchaser = purchaserName(entity);
        row.put("purchaserName", StringUtils.hasText(purchaser) ? purchaser : null);
    }

    private static String purchaserName(GbDistributerPurchaseGoodsEntity entity) {
        GbDepartmentUserEntity user = entity.getPurchaseDepartmentUser();
        if (user == null) {
            return null;
        }
        return firstNonBlank(user.getGbDuWxNickName());
    }

    private static String supplierName(GbDistributerPurchaseGoodsEntity entity) {
        NxJrdhSupplierEntity supplier = entity.getNxJrdhSupplierEntity();
        if (supplier == null) {
            return null;
        }
        return firstNonBlank(supplier.getNxJrdhsSupplierName());
    }

    private static void putIfHasText(Map<String, Object> row, String key, String value) {
        if (StringUtils.hasText(value)) {
            row.put(key, value.trim());
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
