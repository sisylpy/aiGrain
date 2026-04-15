package com.nongxinle.utils;

/**
 * GB模块常量
 */
public class GbTypeUtils {

    public final static Integer GB_DEPARTMENT_TYPE_MENDIAN = 1;  //门店部门
    public final static Integer GB_DEPARTMENT_TYPE_JICAI = 2; //集采部门
    public final static Integer GB_DEPARTMENT_TYPE_KUFANG = 3; //库房部门
    public final static Integer GB_DEPARTMENT_TYPE_KITCHEN = 4; //中央厨房部门
    public final static Integer GB_DEPARTMENT_TYPE_APP_SUPPLIER = 5; //配送商部门
    public final static Integer GB_DEPARTMENT_TYPE_JIAMENG = 11;  //加盟店部门

    public final static Integer GB_DEP_USER_ADMIN_MENDIANDCAIGOU = 1; //门店采购端
    public final static Integer GB_DEP_USER_ADMIN_MENDIAN = 11;  //门店管理端
    public final static Integer GB_DEP_USER_ADMIN_MENDIANDINGHUO = 12; //门店订货端
    public final static Integer GB_DEP_USER_ADMIN_WINDOWDINGHUO = 13; //订货端
    public final static Integer GB_DEP_USER_ADMIN_JICAI = 2; //采购端
    public final static Integer GB_DEP_USER_ADMIN_KUFANG = 3; //库房端
    public final static Integer GB_DEP_USER_ADMIN_KUFANGCAIGOUYUAN = 31; //库房采购员
    public final static Integer GB_DEP_USER_ADMIN_KITCHEN = 4; // 中央厨房端
    public final static Integer GB_DEP_USER_ADMIN_KITCHENCAIGOUYUAN = 41; // 中央厨房采购员
    public final static Integer GB_DEP_USER_ADMIN_APP_SUPPLIER = 5; // 配送商端
    public final static Integer GB_DEP_USER_ADMIN_PEISONGYUAN = 6; // 配送员
    public final static Integer GB_DEP_USER_ADMIN_YOUHUIJUAN = 7; // 优惠券端

    public final static Integer GB_ORDER_STATUS_NEW = 0;  //新订单
    public final static Integer GB_ORDER_STATUS_PROCUREMENT = 1;  //有重量
    public final static Integer GB_ORDER_STATUS_HAS_FINISHED = 2;
    public final static Integer GB_ORDER_STATUS_HAS_BILL = 3;  // 生成送货单
    public final static Integer GB_ORDER_STATUS_RECEIVED = 4;  // 完成收货

    public final static Integer GB_DIS_PURCHASE_BATCH_UN_Send = -2; //卖方未读
    public final static Integer GB_DIS_PURCHASE_BATCH_UN_READ = -1; //卖方未读
    public final static Integer GB_DIS_PURCHASE_BATCH_HAVE_READ = 0; //卖方已读
    public final static Integer GB_DIS_PURCHASE_BATCH_SELLER_REPLY = 1; //卖方回复
    public final static Integer GB_DIS_PURCHASE_BATCH_DIS_USER_WAIT_RECEIVE = 2; //等待收货
    public final static Integer GB_DIS_PURCHASE_BATCH_DEP_USER_RECEIVE_FINISH = 3; //收货完成
    public final static Integer GB_DIS_PURCHASE_BATCH_DIS_USER_FINISH_PAY = 4; //结账完成

    public final static Integer GB_DEP_BILL_NEW = 0; //订单生成
    public final static Integer GB_DEP_BILL_DELIVERY_FINISH = 1; //配送完成
    public final static Integer GB_DEP_BILL_HAVE_PAY = 2; //有支付行为
    public final static Integer GB_DEP_BILL_WAITING_PAY = 3; //等待收货
    public final static Integer GB_DEP_BILL_RECEIVE_FINISH = 4; //收货完成

    public final static Integer GB_DIS_PAY_BATCH_FINISH = 0; //采购订货批次完成
    public final static Integer GB_DIS_PAY_LIST_RECORD = 1; //语音下单
    public final static Integer GB_DIS_PAY_GOODS_ADD = 2; //web

    public final static Integer GB_PURCHASE_BATCH_PAY_TYPE_CASH = 0;
    public final static Integer GB_PURCHASE_BATCH_PAY_TYPE_ACCOUNT = 1;

    public final static Integer GB_DIS_GOODS_TYPE_ZICAI = 1; //自采商品
    public final static Integer GB_DIS_GOODS_TYPE_JICAI = 2; //集采商品
    public final static Integer GB_DIS_GOODS_TYPE_SUPPLIER = 21; //自动采购供货商商品
    public final static Integer GB_DIS_GOODS_TYPE_CHUKU = 3; //出库商品
    public final static Integer GB_DIS_GOODS_TYPE_KITCHEN = 4; //中央厨房商品
    public final static Integer GB_DIS_GOODS_TYPE_APP_SUPPLIER = 5; //配送商商品
    public final static Integer GB_DIS_GOODS_TYPE_WINDOW = 23; //窗口商品

    public final static Integer GB_DIS_WEIGHT_TOTAL_TYPE_STOCK = 3;
    public final static Integer GB_DIS_WEIGHT_TOTAL_TYPE_WINDOW = 23;

    public final static Integer GB_DIS_GOODS_INVENTORY_TYPE_MONTH = 3;
    public final static Integer GB_DIS_GOODS_INVENTORY_TYPE_WEEK = 2;
    public final static Integer GB_DIS_GOODS_INVENTORY_TYPE_DAILY = 1;

    public final static Integer GB_ORDER_TYPE_ZICAI = 1;  //自采订单
    public final static Integer GB_ORDER_TYPE_JICAI = 2; // 集采订单
    public final static Integer GB_ORDER_TYPE_CHUKU = 3;  //出库订单
    public final static Integer GB_ORDER_TYPE_CHUKU_CAIGOU = 31; // 库房采购订单
    public final static Integer GB_ORDER_TYPE_KITCHEN = 4; // 中央厨房订单
    public final static Integer GB_ORDER_TYPE_KITCHEN_CAIGOU = 41; // 中央厨房订单
    public final static Integer GB_ORDER_TYPE_APP_SUPPLIER = 5; // 配送商订单
    public final static Integer GB_ORDER_TYPE_TUIHUO = 9;  // 退货订单

    public final static Integer GB_DEPART_GOODS_STOCK_REDUCE_TYPE_PRODUCE = 1;
    public final static Integer GB_DEPART_GOODS_STOCK_REDUCE_TYPE_WASTE = 2;
    public final static Integer GB_DEPART_GOODS_STOCK_REDUCE_TYPE_LOSS = 3;
    public final static Integer GB_DEPART_GOODS_STOCK_REDUCE_TYPE_RETURN = 4;
    public final static Integer GB_DEPART_GOODS_STOCK_REDUCE_TYPE_STARS = 5;

    public final static Integer GB_ORDER_BUY_STATUS_NEW = 0;  //新订单
    public final static Integer GB_ORDER_BUY_STATUS_PROCUREMENT = 1;  //采购员分享给供货商订单
    public final static Integer GB_ORDER_BUY_STATUS_HAS_PRINTED = 2;  //打印拣货单
    public final static Integer GB_ORDER_BUY_STATUS_HAS_WEIGHT_AND_PRICE = 3;
    public final static Integer GB_ORDER_BUY_STATUS_HAS_FINISH_PUR_GOODS = 4;
    public final static Integer GB_ORDER_BUY_STATUS_UN_PAY_FINISH = 5;
    public final static Integer GB_ORDER_BUY_STATUS_HAVE_PAY_FINISH = 6;

    public final static Integer GB_PURCHASE_GOODS_STATUS_NEW = 0;  //新采购商品
    public final static Integer GB_PURCHASE_GOODS_STATUS_PROCUREMENT = 1;  //采购员分享给供货商订单
    public final static Integer GB_PURCHASE_GOODS_STATUS_WEIGHT_FINISHED = 2;  //出库完成
    public final static Integer GB_PURCHASE_GOODS_STATUS_WAIT_RECEIVE = 3;  //等待收货
    public final static Integer GB_PURCHASE_GOODS_STATUS_STOCK_FINISH = 4;  //完成收货
    public final static Integer GB_PURCHASE_GOODS_STATUS_PAY_FINISH = 5;  //完成支付

    public final static Integer GB_PURCHASE_GOODS_TYPE_SHELF_DIAOCHU = -2;  //货架调货入库
    public final static Integer GB_PURCHASE_GOODS_TYPE_FOR_SELF = 0;
    public final static Integer GB_PURCHASE_GOODS_TYPE_FOR_ORDER = 1;

    public final static Integer GB_WEIGHT_GOODS_STATUS_PREPARE = -1;
    public final static Integer GB_WEIGHT_GOODS_STATUS_PRINTED = 0;

    public final static Integer GB_WEIGHT_TOTAL_STATUS_UN_FINISHED = 0;
    public final static Integer GB_WEIGHT_TOTAL_STATUS_FINISHED = 1;

    public static Integer getGbOrderBuyStatusHavePayFinish() {
        return GB_ORDER_BUY_STATUS_HAVE_PAY_FINISH;
    }

    public static Integer getGbOrderStatusReceived() {
        return GB_ORDER_STATUS_RECEIVED;
    }

    public static Integer getGbDepartmentTypeMendian() {
        return GB_DEPARTMENT_TYPE_MENDIAN;
    }

    public static Integer getGbDepUserAdminMendiancaigouyuan() {
        return GB_DEP_USER_ADMIN_MENDIANDCAIGOU;
    }

    public static Integer getGbDepUserAdminJicaiyuan() {
        return GB_DEP_USER_ADMIN_JICAI;
    }

    public static Integer getGbDepUserAdminKufangcaigouyuan() {
        return GB_DEP_USER_ADMIN_KUFANGCAIGOUYUAN;
    }

    public static Integer getGbDepUserAdminKitchencaigouyuan() {
        return GB_DEP_USER_ADMIN_KITCHENCAIGOUYUAN;
    }

    public static Integer getGbDepUserAdminMendiandinghuoyuan() {
        return GB_DEP_USER_ADMIN_MENDIANDINGHUO;
    }

    public static Integer getGbDepUserAdminKufangguanliyuan() {
        return GB_DEP_USER_ADMIN_KUFANG;
    }

    public static Integer getGbOrderStatusProcurement() {
        return GB_ORDER_STATUS_PROCUREMENT;
    }

    public static Integer getGbPurchaseGoodsStatusWeightFinished() {
        return GB_PURCHASE_GOODS_STATUS_WEIGHT_FINISHED;
    }

    public static Integer getGbOrderBuyStatusHasWeightAndPrice() {
        return GB_ORDER_BUY_STATUS_HAS_WEIGHT_AND_PRICE;
    }

    public static Integer getGbOrderBuyStatusPrepareing() {
        return GB_ORDER_BUY_STATUS_PROCUREMENT;
    }

    public static Integer getGbPurchaseGoodsStatusNew() {
        return GB_PURCHASE_GOODS_STATUS_NEW;
    }

    public static Integer getGbPurchaseGoodsStatusProcurement() {
        return GB_PURCHASE_GOODS_STATUS_PROCUREMENT;
    }

    public static Integer getGbPurchaseGoodsStatusWaitReceive() {
        return GB_PURCHASE_GOODS_STATUS_WAIT_RECEIVE;
    }

    public static Integer getGbPurchaseGoodsStatusStockFinish() {
        return GB_PURCHASE_GOODS_STATUS_STOCK_FINISH;
    }

    public static Integer getGbPurchaseGoodsStatusPayFinish() {
        return GB_PURCHASE_GOODS_STATUS_PAY_FINISH;
    }

    public static Integer getGbDisPurchaseBatchDisUserFinishPay() {
        return GB_DIS_PURCHASE_BATCH_DIS_USER_FINISH_PAY;
    }

    public static Integer getGbDisPurchaseBatchDepUserReceiveFinish() {
        return GB_DIS_PURCHASE_BATCH_DEP_USER_RECEIVE_FINISH;
    }
}
