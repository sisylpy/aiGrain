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

    @TableField(exist = false)
    private GbDistributerGoodsEntity gbDistributerGoodsEntity;
}
