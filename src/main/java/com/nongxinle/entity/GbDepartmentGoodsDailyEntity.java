package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 部门商品日报实体
 */
@Data
@TableName("gb_department_goods_daily")
@EqualsAndHashCode(callSuper = false)
public class GbDepartmentGoodsDailyEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer gbDepartmentGoodsDailyId;
    private Integer gbDgdGbDepartmentId;
    private Integer gbDgdGbDisGoodsId;
    private Integer gbDgdDepDisGoodsId;
    private String gbDgdDate;
    private String gbDgdFullTime;

    // 损耗相关
    private String gbDgdLossWeight;
    private String gbDgdLossSubtotal;

    // 制作相关
    private String gbDgdProduceWeight;
    private String gbDgdProduceSubtotal;

    // 退货相关
    private String gbDgdReturnWeight;
    private String gbDgdReturnSubtotal;

    // 废弃相关
    private String gbDgdWasteWeight;
    private String gbDgdWasteSubtotal;

    // 剩余相关
    private String gbDgdRestWeight;
    private String gbDgdRestSubtotal;

    // 利润相关
    private String gbDgdAfterProfitSubtotal;
    private String gbDgdProfitSubtotal;

    // 上日报废率相关
    private String gbDgdLastWeight;
    private String gbDgdLastProduceWeight;
    private String gbDgdFreshRate;

    // 清空时间
    private String gbDgdSellClearHour;
    private String gbDgdSellClearMinute;
}
