package com.nongxinle.utils;

/**
 * GB 模块类型常量（历史入口）。
 *
 * @deprecated 请改用 {@link GbConstants} 及其嵌套类，命名更清晰；本类仅保留旧字段名与 getter 以兼容现有代码。
 */
@Deprecated(since = "1.0")
public final class GbTypeUtils {

    private GbTypeUtils() {
    }

    public static final Integer GB_DEPARTMENT_TYPE_MENDIAN = GbConstants.DepartmentType.STORE;
    public static final Integer GB_DEPARTMENT_TYPE_JICAI = GbConstants.DepartmentType.GROUP_PURCHASE;
    public static final Integer GB_DEPARTMENT_TYPE_KUFANG = GbConstants.DepartmentType.WAREHOUSE;
    public static final Integer GB_DEPARTMENT_TYPE_KITCHEN = GbConstants.DepartmentType.CENTRAL_KITCHEN;
    public static final Integer GB_DEPARTMENT_TYPE_APP_SUPPLIER = GbConstants.DepartmentType.DELIVERY_SUPPLIER;
    public static final Integer GB_DEPARTMENT_TYPE_JIAMENG = GbConstants.DepartmentType.FRANCHISE;

    public static final Integer GB_DEP_USER_ADMIN_MENDIANDCAIGOU = GbConstants.DepartmentUserRole.STORE_PURCHASER_APP;
    public static final Integer GB_DEP_USER_ADMIN_MENDIAN = GbConstants.DepartmentUserRole.STORE_MANAGER_APP;
    public static final Integer GB_DEP_USER_ADMIN_MENDIANDINGHUO = GbConstants.DepartmentUserRole.STORE_ORDER_APP;
    public static final Integer GB_DEP_USER_ADMIN_WINDOWDINGHUO = GbConstants.DepartmentUserRole.WINDOW_ORDER_APP;
    public static final Integer GB_DEP_USER_ADMIN_JICAI = GbConstants.DepartmentUserRole.GROUP_PURCHASER_APP;
    public static final Integer GB_DEP_USER_ADMIN_KUFANG = GbConstants.DepartmentUserRole.WAREHOUSE_APP;
    public static final Integer GB_DEP_USER_ADMIN_KUFANGCAIGOUYUAN = GbConstants.DepartmentUserRole.WAREHOUSE_PURCHASER;
    public static final Integer GB_DEP_USER_ADMIN_KITCHEN = GbConstants.DepartmentUserRole.CENTRAL_KITCHEN_APP;
    public static final Integer GB_DEP_USER_ADMIN_KITCHENCAIGOUYUAN = GbConstants.DepartmentUserRole.CENTRAL_KITCHEN_PURCHASER;
    public static final Integer GB_DEP_USER_ADMIN_APP_SUPPLIER = GbConstants.DepartmentUserRole.DELIVERY_SUPPLIER_APP;
    public static final Integer GB_DEP_USER_ADMIN_PEISONGYUAN = GbConstants.DepartmentUserRole.DELIVERY_DRIVER_APP;
    public static final Integer GB_DEP_USER_ADMIN_YOUHUIJUAN = GbConstants.DepartmentUserRole.COUPON_APP;

    public static final Integer GB_ORDER_STATUS_NEW = GbConstants.DepartmentOrderStatus.NEW;
    public static final Integer GB_ORDER_STATUS_PROCUREMENT = GbConstants.DepartmentOrderStatus.WEIGHT_CAPTURED;
    public static final Integer GB_ORDER_STATUS_HAS_FINISHED = GbConstants.DepartmentOrderStatus.FINISHED;
    public static final Integer GB_ORDER_STATUS_HAS_BILL = GbConstants.DepartmentOrderStatus.DELIVERY_NOTE_ISSUED;
    public static final Integer GB_ORDER_STATUS_RECEIVED = GbConstants.DepartmentOrderStatus.RECEIVED;

    public static final Integer GB_DIS_PURCHASE_BATCH_UN_Send = GbConstants.DistributorPurchaseBatchStatus.SELLER_PENDING;
    public static final Integer GB_DIS_PURCHASE_BATCH_UN_READ = GbConstants.DistributorPurchaseBatchStatus.SELLER_UNREAD;
    public static final Integer GB_DIS_PURCHASE_BATCH_HAVE_READ = GbConstants.DistributorPurchaseBatchStatus.SELLER_READ;
    public static final Integer GB_DIS_PURCHASE_BATCH_SELLER_REPLY = GbConstants.DistributorPurchaseBatchStatus.SELLER_REPLIED;
    public static final Integer GB_DIS_PURCHASE_BATCH_DIS_USER_WAIT_RECEIVE = GbConstants.DistributorPurchaseBatchStatus.AWAITING_RECEIPT;
    public static final Integer GB_DIS_PURCHASE_BATCH_DEP_USER_RECEIVE_FINISH = GbConstants.DistributorPurchaseBatchStatus.RECEIPT_FINISHED;
    public static final Integer GB_DIS_PURCHASE_BATCH_DIS_USER_FINISH_PAY = GbConstants.DistributorPurchaseBatchStatus.PAYMENT_FINISHED;

    public static final Integer GB_DEP_BILL_NEW = GbConstants.DepartmentBillStatus.NEW;
    public static final Integer GB_DEP_BILL_DELIVERY_FINISH = GbConstants.DepartmentBillStatus.DELIVERY_FINISHED;
    public static final Integer GB_DEP_BILL_HAVE_PAY = GbConstants.DepartmentBillStatus.HAS_PAYMENT;
    public static final Integer GB_DEP_BILL_WAITING_PAY = GbConstants.DepartmentBillStatus.AWAITING_RECEIPT;
    public static final Integer GB_DEP_BILL_RECEIVE_FINISH = GbConstants.DepartmentBillStatus.RECEIPT_FINISHED;

    public static final Integer GB_DIS_PAY_BATCH_FINISH = GbConstants.DistributorPayRecordType.BATCH_COMPLETED;
    public static final Integer GB_DIS_PAY_LIST_RECORD = GbConstants.DistributorPayRecordType.VOICE_ORDER;
    public static final Integer GB_DIS_PAY_GOODS_ADD = GbConstants.DistributorPayRecordType.WEB_GOODS_ADD;

    public static final Integer GB_PURCHASE_BATCH_PAY_TYPE_CASH = GbConstants.PurchaseBatchPayType.CASH;
    public static final Integer GB_PURCHASE_BATCH_PAY_TYPE_ACCOUNT = GbConstants.PurchaseBatchPayType.ON_ACCOUNT;

    public static final Integer GB_DIS_GOODS_TYPE_ZICAI = GbConstants.DistributorGoodsType.SELF_PURCHASE;
    public static final Integer GB_DIS_GOODS_TYPE_JICAI = GbConstants.DistributorGoodsType.GROUP_PURCHASE;
    public static final Integer GB_DIS_GOODS_TYPE_SUPPLIER = GbConstants.DistributorGoodsType.AUTO_SUPPLIER;
    public static final Integer GB_DIS_GOODS_TYPE_CHUKU = GbConstants.DistributorGoodsType.OUTBOUND;
    public static final Integer GB_DIS_GOODS_TYPE_KITCHEN = GbConstants.DistributorGoodsType.CENTRAL_KITCHEN;
    public static final Integer GB_DIS_GOODS_TYPE_APP_SUPPLIER = GbConstants.DistributorGoodsType.DELIVERY_SUPPLIER;
    public static final Integer GB_DIS_GOODS_TYPE_WINDOW = GbConstants.DistributorGoodsType.WINDOW;

    public static final Integer GB_DIS_WEIGHT_TOTAL_TYPE_STOCK = GbConstants.WeightTotalCategory.STOCK;
    public static final Integer GB_DIS_WEIGHT_TOTAL_TYPE_WINDOW = GbConstants.WeightTotalCategory.WINDOW;

    public static final Integer GB_DIS_GOODS_INVENTORY_TYPE_MONTH = GbConstants.InventoryCycleType.MONTHLY;
    public static final Integer GB_DIS_GOODS_INVENTORY_TYPE_WEEK = GbConstants.InventoryCycleType.WEEKLY;
    public static final Integer GB_DIS_GOODS_INVENTORY_TYPE_DAILY = GbConstants.InventoryCycleType.DAILY;

    public static final Integer GB_ORDER_TYPE_ZICAI = GbConstants.PurchaseOrderType.SELF_PURCHASE;
    public static final Integer GB_ORDER_TYPE_JICAI = GbConstants.PurchaseOrderType.GROUP_PURCHASE;
    public static final Integer GB_ORDER_TYPE_CHUKU = GbConstants.PurchaseOrderType.OUTBOUND;
    public static final Integer GB_ORDER_TYPE_CHUKU_CAIGOU = GbConstants.PurchaseOrderType.WAREHOUSE_PURCHASE;
    public static final Integer GB_ORDER_TYPE_KITCHEN = GbConstants.PurchaseOrderType.CENTRAL_KITCHEN;
    public static final Integer GB_ORDER_TYPE_KITCHEN_CAIGOU = GbConstants.PurchaseOrderType.CENTRAL_KITCHEN_PURCHASE;
    public static final Integer GB_ORDER_TYPE_APP_SUPPLIER = GbConstants.PurchaseOrderType.DELIVERY_SUPPLIER;
    public static final Integer GB_ORDER_TYPE_TUIHUO = GbConstants.PurchaseOrderType.RETURN;

    public static final Integer GB_DEPART_GOODS_STOCK_REDUCE_TYPE_PRODUCE = GbConstants.StockReduceType.PRODUCTION;
    public static final Integer GB_DEPART_GOODS_STOCK_REDUCE_TYPE_WASTE = GbConstants.StockReduceType.WASTE;
    public static final Integer GB_DEPART_GOODS_STOCK_REDUCE_TYPE_LOSS = GbConstants.StockReduceType.LOSS;
    public static final Integer GB_DEPART_GOODS_STOCK_REDUCE_TYPE_RETURN = GbConstants.StockReduceType.RETURN;
    public static final Integer GB_DEPART_GOODS_STOCK_REDUCE_TYPE_STARS = GbConstants.StockReduceType.STARS;
    public static final Integer GB_DEPART_GOODS_STOCK_REDUCE_TYPE_EMPLOYEE_MEAL = GbConstants.StockReduceType.EMPLOYEE_MEAL;

    public static final Integer GB_ORDER_BUY_STATUS_NEW = GbConstants.OrderBuyStatus.NEW;
    public static final Integer GB_ORDER_BUY_STATUS_PROCUREMENT = GbConstants.OrderBuyStatus.SHARED_TO_SUPPLIER;
    public static final Integer GB_ORDER_BUY_STATUS_HAS_PRINTED = GbConstants.OrderBuyStatus.PICK_LIST_PRINTED;
    public static final Integer GB_ORDER_BUY_STATUS_HAS_WEIGHT_AND_PRICE = GbConstants.OrderBuyStatus.HAS_WEIGHT_AND_PRICE;
    public static final Integer GB_ORDER_BUY_STATUS_HAS_FINISH_PUR_GOODS = GbConstants.OrderBuyStatus.PURCHASE_LINE_FINISHED;
    public static final Integer GB_ORDER_BUY_STATUS_UN_PAY_FINISH = GbConstants.OrderBuyStatus.UNPAID_FINISHED;
    public static final Integer GB_ORDER_BUY_STATUS_HAVE_PAY_FINISH = GbConstants.OrderBuyStatus.PAID_FINISHED;

    public static final Integer GB_PURCHASE_GOODS_STATUS_NEW = GbConstants.PurchaseGoodsStatus.NEW;
    public static final Integer GB_PURCHASE_GOODS_STATUS_PROCUREMENT = GbConstants.PurchaseGoodsStatus.SHARED_TO_SUPPLIER;
    public static final Integer GB_PURCHASE_GOODS_STATUS_WEIGHT_FINISHED = GbConstants.PurchaseGoodsStatus.WEIGHT_FINISHED;
    public static final Integer GB_PURCHASE_GOODS_STATUS_WAIT_RECEIVE = GbConstants.PurchaseGoodsStatus.AWAITING_RECEIPT;
    public static final Integer GB_PURCHASE_GOODS_STATUS_STOCK_FINISH = GbConstants.PurchaseGoodsStatus.STOCK_FINISHED;
    public static final Integer GB_PURCHASE_GOODS_STATUS_PAY_FINISH = GbConstants.PurchaseGoodsStatus.PAY_FINISHED;

    public static final Integer GB_PURCHASE_GOODS_TYPE_SHELF_DIAOCHU = GbConstants.PurchaseGoodsLineType.SHELF_TRANSFER;
    public static final Integer GB_PURCHASE_GOODS_TYPE_FOR_SELF = GbConstants.PurchaseGoodsLineType.FOR_SELF;
    public static final Integer GB_PURCHASE_GOODS_TYPE_FOR_ORDER = GbConstants.PurchaseGoodsLineType.FOR_ORDER;

    public static final Integer GB_WEIGHT_GOODS_STATUS_PREPARE = GbConstants.WeightGoodsStatus.PREPARING;
    public static final Integer GB_WEIGHT_GOODS_STATUS_PRINTED = GbConstants.WeightGoodsStatus.PRINTED;

    public static final Integer GB_WEIGHT_TOTAL_STATUS_UN_FINISHED = GbConstants.WeightSheetStatus.OPEN;
    public static final Integer GB_WEIGHT_TOTAL_STATUS_FINISHED = GbConstants.WeightSheetStatus.CLOSED;

    public static Integer getGbOrderBuyStatusHavePayFinish() {
        return GbConstants.OrderBuyStatus.PAID_FINISHED;
    }

    public static Integer getGbOrderStatusReceived() {
        return GbConstants.DepartmentOrderStatus.RECEIVED;
    }

    public static Integer getGbDepartmentTypeMendian() {
        return GbConstants.DepartmentType.STORE;
    }

    public static Integer getGbDepUserAdminMendiancaigouyuan() {
        return GbConstants.DepartmentUserRole.STORE_PURCHASER_APP;
    }

    public static Integer getGbDepUserAdminJicaiyuan() {
        return GbConstants.DepartmentUserRole.GROUP_PURCHASER_APP;
    }

    public static Integer getGbDepUserAdminKufangcaigouyuan() {
        return GbConstants.DepartmentUserRole.WAREHOUSE_PURCHASER;
    }

    public static Integer getGbDepUserAdminKitchencaigouyuan() {
        return GbConstants.DepartmentUserRole.CENTRAL_KITCHEN_PURCHASER;
    }

    public static Integer getGbDepUserAdminMendiandinghuoyuan() {
        return GbConstants.DepartmentUserRole.STORE_ORDER_APP;
    }

    public static Integer getGbDepUserAdminKufangguanliyuan() {
        return GbConstants.DepartmentUserRole.WAREHOUSE_APP;
    }

    public static Integer getGbOrderStatusProcurement() {
        return GbConstants.DepartmentOrderStatus.WEIGHT_CAPTURED;
    }

    public static Integer getGbOrderStatusNew() {
        return GbConstants.DepartmentOrderStatus.NEW;
    }

    public static Integer getGbOrderStatusHasFinished() {
        return GbConstants.DepartmentOrderStatus.FINISHED;
    }

    public static Integer getGbOrderStatusHasBill() {
        return GbConstants.DepartmentOrderStatus.DELIVERY_NOTE_ISSUED;
    }

    public static Integer getGbPurchaseGoodsStatusWeightFinished() {
        return GbConstants.PurchaseGoodsStatus.WEIGHT_FINISHED;
    }

    public static Integer getGbOrderBuyStatusHasWeightAndPrice() {
        return GbConstants.OrderBuyStatus.HAS_WEIGHT_AND_PRICE;
    }

    public static Integer getGbOrderBuyStatusPrepareing() {
        return GbConstants.OrderBuyStatus.SHARED_TO_SUPPLIER;
    }

    public static Integer getGbOrderBuyStatusUnPayFinish() {
        return GbConstants.OrderBuyStatus.UNPAID_FINISHED;
    }

    public static Integer getGbPurchaseGoodsStatusNew() {
        return GbConstants.PurchaseGoodsStatus.NEW;
    }

    public static Integer getGbPurchaseGoodsStatusProcurement() {
        return GbConstants.PurchaseGoodsStatus.SHARED_TO_SUPPLIER;
    }

    public static Integer getGbPurchaseGoodsStatusWaitReceive() {
        return GbConstants.PurchaseGoodsStatus.AWAITING_RECEIPT;
    }

    public static Integer getGbPurchaseGoodsStatusStockFinish() {
        return GbConstants.PurchaseGoodsStatus.STOCK_FINISHED;
    }

    public static Integer getGbPurchaseGoodsStatusPayFinish() {
        return GbConstants.PurchaseGoodsStatus.PAY_FINISHED;
    }

    public static Integer getGbDisPurchaseBatchDisUserFinishPay() {
        return GbConstants.DistributorPurchaseBatchStatus.PAYMENT_FINISHED;
    }

    public static Integer getGbDisPurchaseBatchDepUserReceiveFinish() {
        return GbConstants.DistributorPurchaseBatchStatus.RECEIPT_FINISHED;
    }

    public static Integer getGbDisPurchaseBatchUnSend() {
        return GbConstants.DistributorPurchaseBatchStatus.SELLER_PENDING;
    }

    public static Integer getGbDisPurchaseBatchUnRead() {
        return GbConstants.DistributorPurchaseBatchStatus.SELLER_UNREAD;
    }

    public static Integer getGbDisPurchaseBatchHaveRead() {
        return GbConstants.DistributorPurchaseBatchStatus.SELLER_READ;
    }

    public static Integer getGbDisPurchaseBatchSellerReply() {
        return GbConstants.DistributorPurchaseBatchStatus.SELLER_REPLIED;
    }

    public static Integer getGbDisPayBatchFinish() {
        return GbConstants.DistributorPayRecordType.BATCH_COMPLETED;
    }

    public static Integer getGbDisPayListRecord() {
        return GbConstants.DistributorPayRecordType.VOICE_ORDER;
    }

    public static Integer getGbDisPayGoodsAdd() {
        return GbConstants.DistributorPayRecordType.WEB_GOODS_ADD;
    }

    public static Integer getGbOrderBuyStatusNew() {
        return GbConstants.OrderBuyStatus.NEW;
    }

    public static Integer getGbOrderBuyStatusProcurement() {
        return GbConstants.OrderBuyStatus.SHARED_TO_SUPPLIER;
    }

    public static Integer getGbOrderBuyStatusHasPrinted() {
        return GbConstants.OrderBuyStatus.PICK_LIST_PRINTED;
    }

    public static Integer getGbOrderBuyStatusHasFinishPurGoods() {
        return GbConstants.OrderBuyStatus.PURCHASE_LINE_FINISHED;
    }
}
