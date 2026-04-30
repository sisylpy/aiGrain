package com.nongxinle.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * GB（批发商）模块业务常量：与库表中的类型、状态整型取值一致。
 * <p>
 * 按领域拆成嵌套类，避免单类上百个平铺字段难以浏览；取值与旧 {@link GbTypeUtils} 完全一致。
 */
public final class GbConstants {

    private GbConstants() {
    }

    // -------------------------------------------------------------------------
    // 部门类型（如 gb_department.gb_department_type）
    // -------------------------------------------------------------------------
    public static final class DepartmentType {
        private DepartmentType() {
        }

        /** 门店部门 */
        public static final Integer STORE = 1;
        /** 集采部门 */
        public static final Integer GROUP_PURCHASE = 2;
        /** 库房部门 */
        public static final Integer WAREHOUSE = 3;
        /** 中央厨房部门 */
        public static final Integer CENTRAL_KITCHEN = 4;
        /** 配送商部门 */
        public static final Integer DELIVERY_SUPPLIER = 5;
        /** 加盟店部门 */
        public static final Integer FRANCHISE = 11;
    }

    // -------------------------------------------------------------------------
    // 批发商菜品 gb_distributer_food.gb_df_status
    // -------------------------------------------------------------------------
    public static final class DistributerFoodStatus {
        private DistributerFoodStatus() {
        }

        /** 正常 */
        public static final Integer ACTIVE = 0;
        /**
         * 存在部门菜品销售记录（{@code gb_dep_food_sales.gb_dfs_food_id} 对应 {@code gb_dep_food.gb_dep_food_id}）时，
         * 删除接口改为仅停用、不物理删行。
         */
        public static final Integer DISABLED_WITH_DEP_FOOD_SALES = 1;
    }

    // -------------------------------------------------------------------------
    // 部门用户端类型 / 角色（如 gb_department_user 相关 admin 取值）
    // -------------------------------------------------------------------------
    public static final class DepartmentUserRole {
        private DepartmentUserRole() {
        }

        /** 门店采购端 */
        public static final Integer STORE_PURCHASER_APP = 1;
        /** 集采 / 采购端 */
        public static final Integer GROUP_PURCHASER_APP = 2;
        /** 库房端 */
        public static final Integer WAREHOUSE_APP = 3;
        /** 中央厨房端 */
        public static final Integer CENTRAL_KITCHEN_APP = 4;
        /** 配送商端 */
        public static final Integer DELIVERY_SUPPLIER_APP = 5;
        /** 配送员端 */
        public static final Integer DELIVERY_DRIVER_APP = 6;
        /** 优惠券端 */
        public static final Integer COUPON_APP = 7;
        /** 门店管理端 */
        public static final Integer STORE_MANAGER_APP = 11;
        /** 门店订货端 */
        public static final Integer STORE_ORDER_APP = 12;
        /** 窗口订货端 */
        public static final Integer WINDOW_ORDER_APP = 13;
        /** 库房采购员 */
        public static final Integer WAREHOUSE_PURCHASER = 31;
        /** 中央厨房采购员 */
        public static final Integer CENTRAL_KITCHEN_PURCHASER = 41;
    }

    // -------------------------------------------------------------------------
    // NxJrdh 用户管理端类型（与 nx_jrdh 侧 admin 取值约定一致，参见 {@link NxJrdhTypeUtils}）
    // -------------------------------------------------------------------------
    public static final class NxJrdhUserAdminType {
        private NxJrdhUserAdminType() {
        }

        /** nx 采购员 */
        public static final Integer NX_PURCHASER = 1;
        /** gb 采购员 */
        public static final Integer GB_PURCHASER = 2;
        /** nx 与 gb 卖家 */
        public static final Integer NX_SELLER = 3;
        /** gb 卖家 */
        public static final Integer GB_SELLER = 4;
    }

    // -------------------------------------------------------------------------
    // 部门订单状态 gb_department_orders.gb_do_status
    // -------------------------------------------------------------------------
    public static final class DepartmentOrderStatus {
        private DepartmentOrderStatus() {
        }

        /** 新订单 */
        public static final Integer NEW = 0;
        /** 已录入重量等 */
        public static final Integer WEIGHT_CAPTURED = 1;
        /** 订单处理完成 */
        public static final Integer FINISHED = 2;
        /** 已生成送货单 */
        public static final Integer DELIVERY_NOTE_ISSUED = 3;
        /** 收货完成 */
        public static final Integer RECEIVED = 4;
    }

    // -------------------------------------------------------------------------
    // 批发商采购批次状态 gb_distributer_purchase_batch.gb_dpb_status
    // -------------------------------------------------------------------------
    public static final class DistributorPurchaseBatchStatus {
        private DistributorPurchaseBatchStatus() {
        }

        /** 原注释：卖方未读（历史字段名 UN_Send） */
        public static final Integer SELLER_PENDING = -2;
        /** 卖方未读 */
        public static final Integer SELLER_UNREAD = -1;
        /** 卖方已读 */
        public static final Integer SELLER_READ = 0;
        /** 卖方已回复 */
        public static final Integer SELLER_REPLIED = 1;
        /** 等待收货 */
        public static final Integer AWAITING_RECEIPT = 2;
        /** 收货完成 */
        public static final Integer RECEIPT_FINISHED = 3;
        /** 结账完成 */
        public static final Integer PAYMENT_FINISHED = 4;
    }

    // -------------------------------------------------------------------------
    // 部门账单状态
    // -------------------------------------------------------------------------
    public static final class DepartmentBillStatus {
        private DepartmentBillStatus() {
        }

        /** 订单生成 */
        public static final Integer NEW = 0;
        /** 配送完成 */
        public static final Integer DELIVERY_FINISHED = 1;
        /** 有支付行为 */
        public static final Integer HAS_PAYMENT = 2;
        /** 等待收货（历史字段名含 PAY，与库表一致） */
        public static final Integer AWAITING_RECEIPT = 3;
        /** 收货完成 */
        public static final Integer RECEIPT_FINISHED = 4;
    }

    // -------------------------------------------------------------------------
    // 批发商支付批次 / 清单类型
    // -------------------------------------------------------------------------
    public static final class DistributorPayRecordType {
        private DistributorPayRecordType() {
        }

        /** 采购订货批次完成 */
        public static final Integer BATCH_COMPLETED = 0;
        /** 语音下单 */
        public static final Integer VOICE_ORDER = 1;
        /** Web 端加货 */
        public static final Integer WEB_GOODS_ADD = 2;
    }

    /** 采购批次支付方式 */
    public static final class PurchaseBatchPayType {
        private PurchaseBatchPayType() {
        }

        /** 现金 */
        public static final Integer CASH = 0;
        /** 挂账 / 记账 */
        public static final Integer ON_ACCOUNT = 1;
    }

    // -------------------------------------------------------------------------
    // 批发商商品类型
    // -------------------------------------------------------------------------
    public static final class DistributorGoodsType {
        private DistributorGoodsType() {
        }

        /** 自采 */
        public static final Integer SELF_PURCHASE = 1;
        /** 集采 */
        public static final Integer GROUP_PURCHASE = 2;
        /** 出库 */
        public static final Integer OUTBOUND = 3;
        /** 中央厨房 */
        public static final Integer CENTRAL_KITCHEN = 4;
        /** 配送 */
        public static final Integer DELIVERY_SUPPLIER = 5;
        /** 自动订货 */
        public static final Integer AUTO_SUPPLIER = 21;
        /** 窗口 */
        public static final Integer WINDOW = 23;
    }

    /** 称重汇总维度 */
    public static final class WeightTotalCategory {
        private WeightTotalCategory() {
        }

        /** 库存维度 */
        public static final Integer STOCK = 3;
        /** 窗口维度 */
        public static final Integer WINDOW = 23;
    }

    /** 盘点周期类型 */
    public static final class InventoryCycleType {
        private InventoryCycleType() {
        }

        /** 按日 */
        public static final Integer DAILY = 1;
        /** 按周 */
        public static final Integer WEEKLY = 2;
        /** 按月 */
        public static final Integer MONTHLY = 3;
    }

    // -------------------------------------------------------------------------
    // 采购订单类型
    // -------------------------------------------------------------------------
    /**
     * 采购商品行类型（库字段 {@code gb_DPG_purchase_type} 等），与
     * {@link PurchaseBatchOrderMode}（采购批次 {@code gb_dpb_purchase_type}：手动/自动订货）不是同一套枚举。
     */
    public static final class PurchaseOrderType {
        private PurchaseOrderType() {
        }

        /** 待定 */
        public static final Integer UN_DETERMINED = 0;
        /** 自采（门店自行采购），库值 1 */
        public static final Integer SELF_PURCHASE = 1;
        /** 集采 */
        public static final Integer GROUP_PURCHASE = 2;
        /** 出库 */
        public static final Integer OUTBOUND = 3;
        /** 库房采购 */
        public static final Integer WAREHOUSE_PURCHASE = 31;
        /** 中央厨房 */
        public static final Integer CENTRAL_KITCHEN = 4;
        /** 中央厨房采购 */
        public static final Integer CENTRAL_KITCHEN_PURCHASE = 41;
        /** 配送商 / **供货商订货**（业务：向供货商订货的采购行，库值 5；与 {@link #SELF_PURCHASE} 自采不同） */
        public static final Integer DELIVERY_SUPPLIER = 5;
        /** 退货 */
        public static final Integer RETURN = 9;
    }

    /**
     * 采购批次订货方式（库字段 {@code gb_dpb_purchase_type}，
     * {@link com.nongxinle.entity.GbDistributerPurchaseBatchEntity#getGbDpbPurchaseType()}）。
     */
    public static final class PurchaseBatchOrderMode {
        private PurchaseBatchOrderMode() {
        }

        /** 手动订货 */
        public static final Integer MANUAL = 0;
        /** 自动订货 */
        public static final Integer AUTO = 1;
    }

    /**
     * 部门商品库存扣减类型（与表 gb_department_goods_stock_reduce.gb_dgsr_type 一致）。
     * <p><b>菜品成本分析（按菜分摊、均价）</b>：仅汇总 {@link #PRODUCTION}（type=1），与 {@link com.nongxinle.service.GbDepartmentGoodsStockReduceService#queryProductionReduceAggByDisGoods(java.util.Map)} 一致。</p>
     * <p><b>区间损耗率</b>：分子为 {@link #WASTE}+{@link #LOSS}（2+3）出库金额，分母为 1+2+3 出库金额合计（不含 {@link #RETURN}）；全量 1+2+3 按商品汇总见 {@link com.nongxinle.service.GbDepartmentGoodsStockReduceService#queryProduceLossWasteReduceAggByDisGoods(java.util.Map)}。</p>
     */
    public static final class StockReduceType {
        private StockReduceType() {
        }

        /** 生产成本（菜品成本分析里按菜分摊的出库均价、W_g 仅汇总此类型） */
        public static final Integer PRODUCTION = 1;
        /** 损耗（废气等） */
        public static final Integer WASTE = 2;
        /** 损失成本 */
        public static final Integer LOSS = 3;
        /** 退货 */
        public static final Integer RETURN = 4;
        /** 其它类型 5（与库表 gb_dgsr_type 等约定一致） */
        public static final Integer STARS = 5;
    }

    /**
     * 配料/食材行「利用率」分档（与前端「食材利用率分布」环图一致）。
     * <p>接口字段 {@code utilizationRate} 为百分数，如 83.33 表示 83.33%（与 {@link #fromRatePercent(BigDecimal)} 入参单位一致）。</p>
     * <p>区间约定（与 UI）：{@code <90%} 偏低；{@code [90%, 110%]} 正常；{@code (110%, 120%]} 偏高；{@code >120%} 浪费严重
     * （110% 算正常、120% 算偏高；&gt;120% 为严重浪费）。</p>
     * <p>无利用率（理论用量为 0 等）不调用本类，或调用 {@code fromRatePercent} 时返回 null。</p>
     */
    public static final class IngredientUtilizationLevel {
        private IngredientUtilizationLevel() {
        }

        /** 偏低，&lt; 90% */
        public static final String CODE_LOW = "LOW";
        /** 正常，90%（含）～ 110%（含） */
        public static final String CODE_NORMAL = "NORMAL";
        /** 偏高，&gt; 110% 且 ≤ 120% */
        public static final String CODE_HIGH = "HIGH";
        /** 浪费严重，&gt; 120% */
        public static final String CODE_CRITICAL = "CRITICAL";

        public static final String LABEL_ZH_LOW = "偏低";
        public static final String LABEL_ZH_NORMAL = "正常";
        public static final String LABEL_ZH_HIGH = "偏高";
        public static final String LABEL_ZH_CRITICAL = "浪费严重";

        /** 分档下沿（%）：&lt; 为「偏低」。 */
        public static final BigDecimal PCT_LOW_EXCLUSIVE_MAX = new BigDecimal("90");
        /** 正常上沿（含）：≤ 为「正常」（与 PCT_LOW_EXCLUSIVE_MAX 闭区间 90%～110%）。 */
        public static final BigDecimal PCT_NORMAL_INCLUSIVE_MAX = new BigDecimal("110");
        /** 偏上沿（含）：≤ 为「偏高」；&gt; 为「浪费严重」 */
        public static final BigDecimal PCT_HIGH_INCLUSIVE_MAX = new BigDecimal("120");

        /**
         * 由利用率百分数解析分档，供 JSON 中返回 {@code level}、{@code labelZh}。
         *
         * @param ratePercent 百分数，如 100 表示 100%
         * @return 非空；入参为 null 或非有限数时返回 null
         */
        public static LevelAndLabel fromRatePercent(BigDecimal ratePercent) {
            if (ratePercent == null) {
                return null;
            }
            ratePercent = ratePercent.setScale(6, RoundingMode.HALF_UP);
            if (ratePercent.compareTo(PCT_LOW_EXCLUSIVE_MAX) < 0) {
                return new LevelAndLabel(CODE_LOW, LABEL_ZH_LOW);
            }
            if (ratePercent.compareTo(PCT_NORMAL_INCLUSIVE_MAX) <= 0) {
                return new LevelAndLabel(CODE_NORMAL, LABEL_ZH_NORMAL);
            }
            if (ratePercent.compareTo(PCT_HIGH_INCLUSIVE_MAX) <= 0) {
                return new LevelAndLabel(CODE_HIGH, LABEL_ZH_HIGH);
            }
            return new LevelAndLabel(CODE_CRITICAL, LABEL_ZH_CRITICAL);
        }

        /**
         * 接口级「level + 中文名」，仅两字段，无行数/占比等聚合。
         */
        public static final class LevelAndLabel {
            private final String level;
            private final String labelZh;

            public LevelAndLabel(String level, String labelZh) {
                this.level = level;
                this.labelZh = labelZh;
            }

            public String getLevel() {
                return level;
            }

            public String getLabelZh() {
                return labelZh;
            }
        }
    }

    // -------------------------------------------------------------------------
    // 订货 / 采购侧订单状态 gb_do_buy_status 等
    // -------------------------------------------------------------------------
    /** 订货 / 采购侧订单状态 */
    public static final class OrderBuyStatus {
        private OrderBuyStatus() {
        }

        /** 新建 */
        public static final Integer NEW = 0;
        /** 采购员已分享给供货商 */
        public static final Integer SHARED_TO_SUPPLIER = 1;
        /** 拣货单已打印 */
        public static final Integer PICK_LIST_PRINTED = 2;
        /** 已录入重量与单价 */
        public static final Integer HAS_WEIGHT_AND_PRICE = 3;
        /** 采购行已完成 */
        public static final Integer PURCHASE_LINE_FINISHED = 4;
        /** 未付款完成（历史字段名 UN_PAY_FINISH） */
        public static final Integer UNPAID_FINISHED = 5;
        /** 已付款完成（历史字段名 HAVE_PAY_FINISH） */
        public static final Integer PAID_FINISHED = 6;
    }

    // -------------------------------------------------------------------------
    // 采购商品状态
    // -------------------------------------------------------------------------
    /** 采购商品状态 */
    public static final class PurchaseGoodsStatus {
        private PurchaseGoodsStatus() {
        }

        /** 新建 */
        public static final Integer NEW = 0;
        /** 已分享给供货商 */
        public static final Integer SHARED_TO_SUPPLIER = 1;
        /** 称重完成 */
        public static final Integer WEIGHT_FINISHED = 2;
        /** 等待收货 */
        public static final Integer AWAITING_RECEIPT = 3;
        /** 入库完成 */
        public static final Integer STOCK_FINISHED = 4;
        /** 结账完成 */
        public static final Integer PAY_FINISHED = 5;
    }

    /** 采购商品记录子类型 */
    public static final class PurchaseGoodsLineType {
        private PurchaseGoodsLineType() {
        }

        /** 货架调出（历史字段名 SHELF_DIAOCHU） */
        public static final Integer SHELF_TRANSFER = -2;
        /** 自用（非订货行） */
        public static final Integer FOR_SELF = 0;
        /** 为订单订货 */
        public static final Integer FOR_ORDER = 1;
    }

    /** 称重商品行状态 */
    public static final class WeightGoodsStatus {
        private WeightGoodsStatus() {
        }

        /** 准备中 */
        public static final Integer PREPARING = -1;
        /** 已打印 */
        public static final Integer PRINTED = 0;
    }

    /** 称重单整体状态 */
    public static final class WeightSheetStatus {
        private WeightSheetStatus() {
        }

        /** 进行中 / 未闭单 */
        public static final Integer OPEN = 0;
        /** 已闭单 */
        public static final Integer CLOSED = 1;
    }
}
