package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;


@Data
@TableName("gb_dep_food")
@EqualsAndHashCode(callSuper = false)
public class GbDepFoodEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer gbDepFoodId;
    private Integer gbDfDepId;
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
    private Integer gbDfGbDistributerId;

    @TableField(exist = false)
    private GbDistributerFoodEntity gbDistributerFoodEntity;
    @TableField(exist = false)
    private GbDepartmentEntity gbDepartmentEntity;
}
