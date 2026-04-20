package com.nongxinle.service;

import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;

/**
 * 供应商编辑进货批次（supplierEditBatchGb）：撤销待收货状态并回退关联采购行与订单。
 */
public interface GbDistributerPurchaseBatchSupplierEditBatchService {

    final class SupplierEditBatchGbResult {
        private final boolean success;
        private final String errorMessage;
        private final GbDistributerPurchaseBatchEntity entity;

        private SupplierEditBatchGbResult(boolean success, String errorMessage, GbDistributerPurchaseBatchEntity entity) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.entity = entity;
        }

        public static SupplierEditBatchGbResult ok(GbDistributerPurchaseBatchEntity entity) {
            return new SupplierEditBatchGbResult(true, null, entity);
        }

        public static SupplierEditBatchGbResult alreadyReceived() {
            return new SupplierEditBatchGbResult(false, "已有收货", null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public GbDistributerPurchaseBatchEntity getEntity() {
            return entity;
        }
    }

    SupplierEditBatchGbResult supplierEditBatchGb(Integer batchId);
}
