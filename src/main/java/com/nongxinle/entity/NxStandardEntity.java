package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 农鑫商品规格实体
 */
@Data
@TableName("nx_standard")
@EqualsAndHashCode(callSuper = false)
public class NxStandardEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer nxStandardId;
    /**
     * 规格名称
     */
    private String nxStandardName;
    /**
     * 商品ID
     */
    private Integer nxSGoodsId;
    /**
     * 图片路径
     */
    private String nxStandardFilePath;
    /**
     * 规格比例
     */
    private String nxStandardScale;
    /**
     * 误差
     */
    private String nxStandardError;
    /**
     * 排序
     */
    private Integer nxStandardSort;
    /**
     * 重量
     */
    private Integer nxStandardWeight;
}
