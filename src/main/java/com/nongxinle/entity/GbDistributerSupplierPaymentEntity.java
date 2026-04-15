package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 批发商供应商支付实体
 */
@Data
@TableName("gb_distributer_supplier_payment")
@EqualsAndHashCode(callSuper = false)
public class GbDistributerSupplierPaymentEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 支付id
     */
    @TableId(type = IdType.AUTO)
    private Integer gbDistributerSupplierPaymentId;

    /**
     * 支付日期
     */
    private String gbDspDate;

    /**
     * 供应商id
     */
    private Integer gbDspSupplierId;

    /**
     * 支付用户id
     */
    private Integer gbDspPayUserId;

    /**
     * 配送商id
     */
    private Integer gbDspNxDistributerId;

    /**
     * 微信交易号
     */
    private String gbDspWxOutTradeNo;

    /**
     * 支付状态
     */
    private Integer gbDspStatus;

    /**
     * 支付用户openid
     */
    private String gbDspPayUserOpenId;

    /**
     * 支付金额
     */
    private String gbDspPayTotal;

    /**
     * 批发商id
     */
    private Integer gbDspDistributerId;

    /**
     * 支付完整时间
     */
    private String gbDspPayFullTime;

    /**
     * 供应商对象（非数据库字段）
     */
    @TableField(exist = false)
    private NxJrdhSupplierEntity jrdhSupplierEntity;

    /**
     * 支付用户对象（非数据库字段）
     */
    @TableField(exist = false)
    private GbDistributerUserEntity payUserEntity;

    /**
     * 采购批次列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<GbDistributerPurchaseBatchEntity> gbDisPurchaseBatchEntities;

}
