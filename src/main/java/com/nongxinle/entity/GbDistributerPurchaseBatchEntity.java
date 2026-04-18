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
 * 批发商采购批次实体
 */
@Data
@TableName("gb_distributer_purchase_batch")
@EqualsAndHashCode(callSuper = false)
public class GbDistributerPurchaseBatchEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    // ========== 数据库表字段 ==========
    
    @TableId(type = IdType.AUTO)
    private Integer gbDistributerPurchaseBatchId;
    
    @TableField
    private Integer gbDpbStatus;
    
    @TableField
    private Integer gbDpbUserAdminType;
    
    @TableField
    private String gbDpbTime;
    
    @TableField
    private Integer gbDpbPurUserId;
    
    @TableField
    private Integer gbDpbDepBillId;
    
    @TableField
    private Integer gbDpbDistributerId;
    
    @TableField
    private String gbDpbDate;
    
    @TableField
    private String gbDpbHour;
    
    @TableField
    private String gbDpbMinute;
    
    @TableField
    private String gbDpbSubtotal;
    
    @TableField(exist = false)
    private String groupDate;
    
    @TableField
    private Integer gbDpbPurDepartmentId;
    
    @TableField
    private Integer gbDpbPayType;
    
    @TableField
    private String gbDpbPaySubtotal;
    
    @TableField
    private Integer gbDpbSupplierId;
    
    @TableField
    private String gbDpbPurchaseMonth;
    
    @TableField
    private String gbDpbPurchaseWeek;
    
    @TableField
    private String gbDpbPurchaseYear;
    
    @TableField
    private String gbDpbPurchaseFullTime;
    
    @TableField
    private String gbDpbSellerReplyFullTime;
    
    @TableField
    private String gbDpbFinishFullTime;
    
    @TableField
    private String gbDpbBuyUserOpenId;
    
    @TableField
    private String gbDpbSellUserOpenId;
    
    @TableField
    private Integer gbDpbGbSupplierPaymentId;
    
    @TableField
    private Integer gbDpbBuyUserId;
    
    @TableField
    private Integer gbDpbSellUserId;

    /**
     * 批次订货方式，库字段 {@code gb_dpb_purchase_type}：0 手动订货，1 自动订货（常量见
     * {@link com.nongxinle.utils.GbConstants.PurchaseBatchOrderMode}）。
     * 与采购商品行 {@code gb_DPG_purchase_type} 含义不同，参见 {@link com.nongxinle.utils.GbConstants.PurchaseOrderType}。
     */
    @TableField
    private Integer gbDpbPurchaseType;

    @TableField(exist = false)
    private String gbDpbBatchNo;

    // ========== 非数据库字段（关联实体） ==========
    
    @TableField(exist = false)
    private List<GbDistributerPurchaseGoodsEntity> gbDPGEntities;
    
    @TableField(exist = false)
    private GbDistributerEntity gbDistributerEntity;
    
    @TableField(exist = false)
    private GbDepartmentUserEntity nxJrdhPurEntity;
    
    @TableField(exist = false)
    private NxJrdhUserEntity nxJrdhSellerEntity;
    
    @TableField(exist = false)
    private NxJrdhSupplierEntity nxJrdhSupplierEntity;
    
    @TableField(exist = false)
    private GbDistributerPurchaseGoodsEntity purchaseGoodsEntity;
    
    @TableField(exist = false)
    private GbDepartmentEntity purDeparment;
}
