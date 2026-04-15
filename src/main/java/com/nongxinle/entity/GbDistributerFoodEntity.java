package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;


@Data
@TableName("gb_distributer_food")
@EqualsAndHashCode(callSuper = false)
public class GbDistributerFoodEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer gbDistributerFoodId;
    private Integer gbDfDistributerId;
    private Integer gbDfNxFoodId;
    private String gbDfFoodName;
    private String gbDfFoodPrice;
    private Integer gbDfStatus;
    private String gbDfFoodPinyin;
    private String gbDfFoodPy;
    private Integer gbDfFoodFatherId;
    private String gbDfFoodImg;
    private String gbDfFoodImgLarge;
    private String gbDfFoodMethod;
    private String gbDfFoodDetail;
    private Integer gbDfGoodsSort;

    @TableField(exist = false)
    private List<GbDistributerFoodGoodsEntity> gbdisFoodGoodsEntities;
    @TableField(exist = false)
    private List<GbDistributerFoodEntity> foodEntityList;
    @TableField(exist = false)
    private GbDepFoodEntity gbDepFoodEntity;
    @TableField(exist = false)
    private GbDistributerFoodGoodsEntity rawFoodGoods;
}
