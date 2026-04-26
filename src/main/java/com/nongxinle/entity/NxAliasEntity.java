package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 商品别名Entity
 */
@Data
@TableName("nongxinle.nx_alias")
@EqualsAndHashCode(callSuper = false)
public class NxAliasEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 别名id
     */
    @TableId(type = IdType.AUTO)
    private Integer nxAliasId;

    /**
     * 别名名称
     */
    private String nxAliasName;

    private String nxAliasPy;
    private String nxAliasPinyin;

    /**
     * 别名商品id
     */
    private Integer nxAlsGoodsId;

    /**
     * 别名排序
     */
    private Integer nxAlsSort;
}
