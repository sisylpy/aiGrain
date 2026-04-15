package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 批发商商品别名Entity
 */
@Data
@TableName("gb_distributer_alias")
@EqualsAndHashCode(callSuper = false)
public class GbDistributerAliasEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer gbDistributerAliasId;

    private Integer gbDaDisGoodsId;

    private Integer gbDaGbAliasId;

    private String gbDaAliasName;

    private String gbDaAliasPinyin;

    private String gbDaAliasPy;
}
