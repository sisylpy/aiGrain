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
    private String gbDfgGoodsAmount;
    private String gbDfgGoodsName;
    private String gbDfgGoodsStandardname;
    private Integer gbDfgStatus;

    @TableField(exist = false)
    private GbDistributerGoodsEntity gbDistributerGoodsEntity;
}
