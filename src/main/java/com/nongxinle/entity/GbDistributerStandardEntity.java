package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 批发商商品规格实体
 */
@Data
@TableName("gb_distributer_standard")
@EqualsAndHashCode(callSuper = false)
public class GbDistributerStandardEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer gbDistributerStandardId;
    private Integer gbDsDisGoodsId;
    private String gbDsStandardName;
    private String gbDsStandardWeight;
    private String gbDsStandardScale;

}
