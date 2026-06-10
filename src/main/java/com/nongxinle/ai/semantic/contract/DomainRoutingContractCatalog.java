package com.nongxinle.ai.semantic.contract;

import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * 八域路由简表只读 Catalog（P1 Step 1 skeleton）。
 * <p>不含 wire / planType / Tool；不改变运行时语义链路。
 */
@UtilityClass
public final class DomainRoutingContractCatalog {

    public static final DomainRoutingContract PURCHASE =
            DomainRoutingContract.builder()
                    .domainCode("PURCHASE")
                    .domainName("采购")
                    .businessObject("商品")
                    .businessObject("供货商")
                    .businessObject("采购")
                    .businessObject("订货")
                    .businessObject("自采")
                    .businessObject("采购金额")
                    .businessObject("采购次数")
                    .businessObject("单价")
                    .supportedTaskType("OVERVIEW")
                    .supportedTaskType("RANKING")
                    .supportedTaskType("DETAIL")
                    .supportedTaskType("COMPARE")
                    .supportedTaskType("ANOMALY")
                    .anchorType("GOODS")
                    .anchorType("SUPPLIER")
                    .crossDomainHint("purchase_stock_reduce_mismatch")
                    .crossDomainHint("freshness_risk")
                    .crossDomainHint("overstock_risk")
                    .routeExample("这个月采购最多的商品是什么")
                    .routeExample("这个月采购情况怎么样")
                    .routeExample("供货商采购金额排行")
                    .routeExample("第一名是谁供的")
                    .status(DomainRoutingContractStatus.ACTIVE)
                    .build();

    public static final DomainRoutingContract REVENUE =
            DomainRoutingContract.builder()
                    .domainCode("REVENUE")
                    .domainName("营收")
                    .businessObject("门店")
                    .businessObject("营业额")
                    .businessObject("收入")
                    .businessObject("销售额")
                    .businessObject("经营收入")
                    .supportedTaskType("OVERVIEW")
                    .supportedTaskType("RANKING")
                    .supportedTaskType("DETAIL")
                    .supportedTaskType("COMPARE")
                    .supportedTaskType("ANOMALY")
                    .supportedTaskType("TREND")
                    .anchorType("STORE")
                    .crossDomainHint("business_diagnosis")
                    .crossDomainHint("dish_sales")
                    .routeExample("各门店营业额对比")
                    .routeExample("本月销售额是多少")
                    .routeExample("这个月营业额怎么样")
                    .status(DomainRoutingContractStatus.ACTIVE)
                    .build();

    public static final DomainRoutingContract STOCK_REDUCE =
            DomainRoutingContract.builder()
                    .domainCode("STOCK_REDUCE")
                    .domainName("出库核销")
                    .businessObject("出库")
                    .businessObject("出库情况")
                    .businessObject("出库金额")
                    .businessObject("核销")
                    .businessObject("耗用")
                    .businessObject("生产耗用")
                    .businessObject("报损")
                    .businessObject("废弃")
                    .businessObject("退货")
                    .supportedTaskType("OVERVIEW")
                    .supportedTaskType("RANKING")
                    .supportedTaskType("DETAIL")
                    .supportedTaskType("COMPARE")
                    .supportedTaskType("ANOMALY")
                    .anchorType("GOODS")
                    .anchorType("STORE")
                    .anchorType("WAREHOUSE")
                    .crossDomainHint("purchase_stock_reduce_mismatch")
                    .crossDomainHint("freshness_risk")
                    .routeExample("本月出库金额排行")
                    .routeExample("这个月出库情况怎么样")
                    .routeExample("这个月退货金额是多少")
                    .routeExample("哪些商品报损最多")
                    .status(DomainRoutingContractStatus.ACTIVE)
                    .build();

    public static final DomainRoutingContract WAREHOUSE =
            DomainRoutingContract.builder()
                    .domainCode("WAREHOUSE")
                    .domainName("库房库存")
                    .businessObject("库房")
                    .businessObject("库存")
                    .businessObject("商品库存")
                    .businessObject("库存比较多")
                    .businessObject("库存情况")
                    .businessObject("入库")
                    .businessObject("库房商品")
                    .businessObject("库存预警")
                    .supportedTaskType("OVERVIEW")
                    .supportedTaskType("RANKING")
                    .supportedTaskType("DETAIL")
                    .supportedTaskType("COMPARE")
                    .supportedTaskType("ANOMALY")
                    .anchorType("WAREHOUSE")
                    .anchorType("GOODS")
                    .anchorType("STORE")
                    .crossDomainHint("stock_reduce")
                    .crossDomainHint("purchase")
                    .routeExample("库存现在怎么样")
                    .routeExample("帮我看看库存有没有问题")
                    .routeExample("库存金额一共多少")
                    .routeExample("哪些商品库存偏低")
                    .status(DomainRoutingContractStatus.ACTIVE)
                    .build();

    public static final DomainRoutingContract DISH_SALES =
            DomainRoutingContract.builder()
                    .domainCode("DISH_SALES")
                    .domainName("菜品销售")
                    .businessObject("菜品销售")
                    .businessObject("菜品销量")
                    .businessObject("销售")
                    .businessObject("销售情况")
                    .businessObject("销量")
                    .businessObject("销售额")
                    .businessObject("售卖")
                    .businessObject("点菜")
                    .businessObject("菜品收入")
                    .supportedTaskType("OVERVIEW")
                    .supportedTaskType("RANKING")
                    .supportedTaskType("DETAIL")
                    .supportedTaskType("COMPARE")
                    .supportedTaskType("ANOMALY")
                    .supportedTaskType("TREND")
                    .anchorType("DISH")
                    .anchorType("STORE")
                    .crossDomainHint("dish_profit")
                    .crossDomainHint("business_diagnosis")
                    .crossDomainHint("menu_operation")
                    .routeExample("销量最高的菜品")
                    .routeExample("这个月菜品销量怎么样")
                    .routeExample("哪个菜卖得最好")
                    .routeExample("哪个菜销售额最高")
                    .status(DomainRoutingContractStatus.ACTIVE)
                    .build();

    public static final DomainRoutingContract DISH_PROFIT =
            DomainRoutingContract.builder()
                    .domainCode("DISH_PROFIT")
                    .domainName("菜品毛利")
                    .businessObject("毛利")
                    .businessObject("毛利率")
                    .businessObject("利润")
                    .businessObject("成本")
                    .businessObject("理论成本")
                    .businessObject("实际成本")
                    .businessObject("差异")
                    .supportedTaskType("OVERVIEW")
                    .supportedTaskType("RANKING")
                    .supportedTaskType("DETAIL")
                    .supportedTaskType("COMPARE")
                    .anchorType("DISH")
                    .anchorType("STORE")
                    .crossDomainHint("dish_sales")
                    .crossDomainHint("business_diagnosis")
                    .crossDomainHint("menu_operation")
                    .routeExample("毛利最高的菜品")
                    .routeExample("这个月菜品毛利怎么样")
                    .routeExample("哪个菜毛利率最低")
                    .routeExample("哪个菜毛利最高")
                    .routeExample("这道菜毛利率多少")
                    .routeExample("香煎青鱼毛利率是多少")
                    .status(DomainRoutingContractStatus.ACTIVE)
                    .build();

    public static final DomainRoutingContract DISH_COST =
            DomainRoutingContract.builder()
                    .domainCode("DISH_COST")
                    .domainName("菜品成本分析")
                    .businessObject("菜品成本")
                    .businessObject("成本分析")
                    .businessObject("配料")
                    .businessObject("用料")
                    .businessObject("实际成本")
                    .businessObject("理论成本")
                    .businessObject("成本偏差")
                    .supportedTaskType("DETAIL")
                    .anchorType("DISH")
                    .anchorType("STORE")
                    .crossDomainHint("dish_profit")
                    .crossDomainHint("dish_sales")
                    .routeExample("这道菜成本怎么样")
                    .routeExample("某菜配料分析")
                    .routeExample("某菜用料情况")
                    .routeExample("香煎青鱼价格和配方怎么优化")
                    .routeExample("香煎青鱼按55%目标毛利率应该卖多少钱")
                    .routeExample("某菜价格合适吗")
                    .routeExample("某菜配料够用几天")
                    .routeExample("某菜还能卖几天")
                    .status(DomainRoutingContractStatus.ACTIVE)
                    .build();

    public static final DomainRoutingContract MENU_OPERATION =
            DomainRoutingContract.builder()
                    .domainCode("MENU_OPERATION")
                    .domainName("菜单经营顾问")
                    .businessObject("菜单")
                    .businessObject("菜单经营")
                    .businessObject("菜单优化")
                    .businessObject("菜单结构")
                    .businessObject("经营建议")
                    .businessObject("拖后腿")
                    .businessObject("需要调整")
                    .businessObject("拖累菜单")
                    .businessObject("爆品")
                    .businessObject("高销量低利润")
                    .businessObject("卖得多但不赚钱")
                    .supportedTaskType("OVERVIEW")
                    .supportedTaskType("ANALYSIS")
                    .anchorType("STORE")
                    .crossDomainHint("dish_profit")
                    .crossDomainHint("dish_sales")
                    .crossDomainHint("business_overview")
                    .routeExample("这个月菜单经营怎么样")
                    .routeExample("菜单怎么优化")
                    .routeExample("哪些菜在拖后腿")
                    .routeExample("哪些菜拖累菜单利润")
                    .routeExample("有哪些菜需要调整")
                    .routeExample("卖得火但不赚钱的菜有哪些")
                    .routeExample("卖得多但不赚钱的菜")
                    .status(DomainRoutingContractStatus.ACTIVE)
                    .build();

    public static final DomainRoutingContract BUSINESS_OVERVIEW =
            DomainRoutingContract.builder()
                    .domainCode("BUSINESS_OVERVIEW")
                    .domainName("经营概览")
                    .businessObject("经营情况")
                    .businessObject("经营概况")
                    .businessObject("整体经营")
                    .businessObject("综合表现")
                    .supportedTaskType("OVERVIEW")
                    .supportedTaskType("SUMMARY")
                    .anchorType("STORE")
                    .crossDomainHint("business_diagnosis")
                    .crossDomainHint("revenue")
                    .crossDomainHint("purchase")
                    .crossDomainHint("menu_operation")
                    .routeExample("这个月经营情况怎么样")
                    .routeExample("整体经营概况")
                    .status(DomainRoutingContractStatus.ACTIVE)
                    .build();

    public static final DomainRoutingContract BUSINESS_DIAGNOSIS =
            DomainRoutingContract.builder()
                    .domainCode("BUSINESS_DIAGNOSIS")
                    .domainName("经营诊断")
                    .businessObject("经营情况")
                    .businessObject("异常")
                    .businessObject("综合诊断")
                    .businessObject("门店对比")
                    .businessObject("问题原因")
                    .supportedTaskType("DIAGNOSIS")
                    .supportedTaskType("OVERVIEW")
                    .supportedTaskType("COMPARE")
                    .supportedTaskType("ANOMALY")
                    .anchorType("STORE")
                    .crossDomainHint("revenue")
                    .crossDomainHint("purchase")
                    .crossDomainHint("stock_reduce")
                    .crossDomainHint("dish_profit")
                    .crossDomainHint("dish_sales")
                    .routeExample("门店经营有什么问题")
                    .routeExample("综合诊断一下本月经营")
                    .routeExample("这个月经营有什么异常")
                    .routeExample("这个月经营哪里有问题")
                    .routeExample("这个月为什么经营不好")
                    .routeExample("这个月有哪些风险")
                    .routeExample("这个月给我一些经营建议")
                    .status(DomainRoutingContractStatus.ACTIVE)
                    .build();

    private static final List<DomainRoutingContract> ALL =
            List.of(
                    REVENUE,
                    PURCHASE,
                    STOCK_REDUCE,
                    WAREHOUSE,
                    DISH_SALES,
                    DISH_PROFIT,
                    DISH_COST,
                    MENU_OPERATION,
                    BUSINESS_OVERVIEW,
                    BUSINESS_DIAGNOSIS);

    public static List<DomainRoutingContract> listDomainRoutingContracts() {
        return ALL;
    }

    public static DomainRoutingContract findByDomainCode(String domainCode) {
        if (domainCode == null) {
            return null;
        }
        String code = domainCode.trim().toUpperCase();
        for (DomainRoutingContract c : ALL) {
            if (code.equals(c.getDomainCode())) {
                return c;
            }
        }
        return null;
    }
}
