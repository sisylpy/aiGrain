package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOverviewToolPurchaseSourceFacetTest {

    @Test
    void applyPurchaseSourceFacet_supplierPurchase_usesSupplierBuyAndBatchDayuStatus() {
        Map<String, Object> base = new HashMap<>();
        base.put("legacyPurchaseMethodFocus", "supplier_channel");

        PurchaseOverviewTool.applyPurchaseSourceFacetToQueryParams(
                base, AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);

        assertThat(base).doesNotContainKey("legacyPurchaseMethodFocus");
        assertThat(base.get("supplierBuy")).isEqualTo(1);
        assertThat(base.get("batchDayuStatus")).isEqualTo(2);
    }

    @Test
    void applyPurchaseSourceFacet_selfPurchase_usesSupplierBuyMinusOne() {
        Map<String, Object> base = new HashMap<>();

        PurchaseOverviewTool.applyPurchaseSourceFacetToQueryParams(
                base, AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE);

        assertThat(base.get("supplierBuy")).isEqualTo(-1);
        assertThat(base).doesNotContainKey("batchDayuStatus");
    }

    @Test
    void buildPurchaseGoodsSqlQueryBase_supplierFocus_setsSupplierBuyFacet() {
        var tool = new PurchaseOverviewTool(null, null);
        Map<String, Object> args = new HashMap<>();
        args.put(AiBusinessToolIds.ARG_DIS_ID, 1L);
        args.put(AiBusinessToolIds.ARG_START_DATE, "2026-06-01");
        args.put(AiBusinessToolIds.ARG_STOP_DATE, "2026-06-03");
        args.put(AiBusinessToolIds.ARG_PURCHASE_SOURCE_FOCUS, AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);

        Map<String, Object> base = tool.buildPurchaseGoodsSqlQueryBase(args);

        assertThat(base.get("supplierBuy")).isEqualTo(1);
        assertThat(base.get("batchDayuStatus")).isEqualTo(2);
        assertThat(base.get("dayuStatus")).isEqualTo(2);
        assertThat(base).doesNotContainKey("legacyPurchaseMethodFocus");
    }
}
