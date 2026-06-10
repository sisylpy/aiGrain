package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;


@Data
@TableName("gb_distributer_food_goods")
@EqualsAndHashCode(callSuper = false)
public class GbDistributerFoodGoodsEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer gbDistributerFoodGoodsId;
    private Integer gbDfgDisId;
    private Integer gbDfgFoodId;
    private Integer gbDfgDisGoodsId;
    /**
     * 每份菜品对该原料的用量（如「鲜活小龙虾」1.5 斤/份）；菜品成本分析「可支撑」共料分摊时，与同原料其它菜的该字段<strong>相加为分母 S</strong>（比例即各 u 之比）。
     */
    private String gbDfgGoodsAmount;
    private String gbDfgGoodsName;
    private String gbDfgGoodsStandardname;
    private Integer gbDfgStatus;
    /**
     * 每份用量（用户录入的数），如 50（表示 50g/份）
     */
    private java.math.BigDecimal gbDfgPortionAmount;
    /**
     * 每份用量单位（最小单位），如 g/ml/个/张/只/斤
     */
    private String gbDfgPortionUnit;
    /**
     * 1 个采购包装 = 多少最小单位，如 3000（1袋=3000g）
     */
    private java.math.BigDecimal gbDfgPackQtyInMin;

    /**
     * 近 30 日库存批次采购单价（gb_department_goods_stock.gb_dgs_price）均值；无有效记录为 "0"。
     */
    @TableField(exist = false)
    private String gbDfgGoodsAveragePrice;

    @TableField(exist = false)
    private GbDistributerGoodsEntity gbDistributerGoodsEntity;

    /**
     * 本区间、本作用域下该批发商商品 type=1 均价的「出库价」：制作金额÷制作数量（与菜品成本分析 reduce 口径一致；无生产出库时 "0"）。
     */
    @TableField(exist = false)
    private String gbDfgOutboundUnitPrice;
    /**
     * 生产（type=1）出库总数量（与库存 reduce 重量汇总统寸一致）。
     */
    @TableField(exist = false)
    private String gbDfgProduceReduceWeight;
    @TableField(exist = false)
    private String gbDfgProduceReduceCost;
    /**
     * 损耗+损失（type2+3）在「本料」上的数量/金额：1+2+3 按料汇总 减 type1 同料汇总，与区间无冲突时应非负。
     */
    @TableField(exist = false)
    private String gbDfgWasteLossReduceWeight;
    @TableField(exist = false)
    private String gbDfgWasteLossReduceCost;
    @TableField(exist = false)
    private String gbDfgOutbound123Weight;
    @TableField(exist = false)
    private String gbDfgOutbound123Cost;
    /**
     * 员工餐（type=6，原料型）出库总数量/金额；与 {@link com.nongxinle.utils.GbConstants.StockReduceType#EMPLOYEE_MEAL} 一致。
     */
    @TableField(exist = false)
    private String gbDfgEmployeeMealReduceWeight;
    @TableField(exist = false)
    private String gbDfgEmployeeMealReduceCost;
}
