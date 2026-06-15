package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 批发商模块实体
 */
@Data
@TableName("gb_distributer_module")
@EqualsAndHashCode(callSuper = false)
public class GbDistributerModuleEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer gbDistributerModuleId;

    private Integer gbDmStockNumber;

    private Integer gbDmPurchaseNumber;

    private Integer gbDmAppSupplierNumber;

    private Integer gbDmCentralKitchenNumber;

    private Integer gbDmDirectSalesNumber;

    private Integer gbDmFranchiseeNumber;

    private Integer gbDmDistributerId;

    private Integer gbDmFixedSupplierNumber;
}
