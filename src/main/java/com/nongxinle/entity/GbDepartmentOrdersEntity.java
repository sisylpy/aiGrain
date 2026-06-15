package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 部门订单实体
 */
@Setter@Getter@ToString
@TableName("gb_department_orders")
public class GbDepartmentOrdersEntity implements Serializable, Comparable {
    
    /**
     * 部门订单ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Integer gbDepartmentOrdersId;
    private static final long serialVersionUID = 1L;

    /**
     *  部门订单gb商品id
     */
    private Integer gbDoNxGoodsId;
    /**
     *  部门订单商品父id
     */
    private Integer gbDoNxGoodsFatherId;
    /**
     *  部门订单社区商品id
     */
    private Integer gbDoDisGoodsId;
    private Integer gbDoDisGoodsFatherId;
    private Integer gbDoDisGoodsGrandId;
    private Integer gbDoDisGoodsGreatId;

    private Integer gbDoDepDisGoodsId;

    /**
     *  部门订单申请数量
     */
    private String gbDoQuantity;
    /**
     *  部门订单申请规格
     */
    private String gbDoStandard;
    /**
     *  部门订单申请备注
     */
    private String gbDoRemark;
    /**
     *  部门订单重量
     */
    private String gbDoWeight;
    /**
     *  部门订单商品单价
     */
    private String gbDoPrice;
    /**
     *  部门订单申请商品小计
     */
    private String gbDoSubtotal;
    /**
     *  部门订单部门id
     */
    private Integer gbDoDepartmentId;

    private Integer gbDoDepartmentFatherId;
    /**
     *  部门订单批发商id
     */
    private Integer gbDoDistributerId;
    /**
     *  部门订单账单id
     */
    private Integer gbDoBillId;
    
    /**
     *  部门订单申请商品状态
     */
    private Integer gbDoStatus;
    /**
     *  部门订单订货用户id
     */
    private Integer gbDoOrderUserId;
    /**
     *  部门订单商品称重用户id
     */
    private Integer gbDoPickUserId;
    /**
     *  部门订单商品输入单价用户id
     */
    private Integer gbDoReceiveUserId;
    /**
     *  部门商品采购员id
     */
    private Integer gbDoPurchaseUserId;
    /**
     *  部门订单商品进货状态
     */
    private Integer gbDoBuyStatus;
    private Integer gbDoCostPriceLevel;
    /**
     *  部门订单申请时间
     */
    private String gbDoApplyDate;
    private String gbDoApplyWhatDay;
    private String gbDoApplyArriveDate;
    private String gbDoApplyFullTime;
    private String gbDoSellingPrice;
    private String gbDoSellingSubtotal;

    /**
     *  部门订单送达时间
     */
    private String gbDoArriveOnlyDate;
    private String gbDoArriveDate;
    private Integer gbDoArriveWeeksYear;

    /**
     * 采购商品id
     */
    private Integer gbDoPurchaseGoodsId;

    private Integer gbDoGoodsType;

    private String gbDoOperationTime;
    private String gbDoPrintStandard;
    private String gbDoArriveWhatDay;
    private Integer gbDoIsAgent;
    private Integer gbDoNxGoodsGrandId;
    private Integer gbDoNxGoodsGreatId;
    private String gbDoApplyOnlyTime;
    private String gbDoCostPrice;
    private String gbDoCostWeight;
    private String gbDoCostSubtotal;
    private String gbDoPriceDifferent;
    private Integer gbDoNxDistributerId;
    private Integer gbDoNxDistributerGoodsId;
    private Integer gbDoNxDepartmentOrderId;
    private Integer gbDoToDepartmentId;
    private Integer gbDoOrderType;
    private Integer gbDoReturnUserId;
    private Integer gbDoDgsrReturnId;
    private Integer gbDoDsStandardId;
    private Integer gbDoWeightTotalId;
    private Integer gbDoWeightGoodsId;
    private String gbDoDsStandardScale;
    private String gbDoScaleWeight;
    private String gbDoScalePrice;
    private String gbDoGoodsName;

    /** 订货部门名称（关联查询填充，非表字段） */
    @TableField(exist = false)
    private String gbDoOrderDepartmentName;
    /** 父级部门名称（关联查询填充，非表字段） */
    @TableField(exist = false)
    private String gbDoParentDepartmentName;

    // ============ 非数据库字段（关联对象） ============
    
    @TableField(exist = false)
    private NxGoodsEntity nxGoodsEntity;

    @TableField(exist = false)
    private GbDistributerGoodsEntity gbDistributerGoodsEntity;

    @TableField(exist = false)
    private GbDepartmentUserEntity gbDepartmentUserEntity;

    @TableField(exist = false)
    private GbDepartmentEntity gbDepartmentEntity;

    @TableField(exist = false)
    private GbDepartmentEntity orderDepartment;

    @TableField(exist = false)
    private GbDepartmentUserEntity receiveUserEntity;

    @TableField(exist = false)
    private GbDepartmentUserEntity pickerUserEntity;


    @TableField(exist = false)
    private List<GbDistributerGoodsEntity> gbDistributerGoodsEntityList;

    @TableField(exist = false)
    private TreeSet<NxGoodsEntity> nxGoodsEntities;

    @TableField(exist = false)
    private List<GbDepartmentOrdersEntity> hasOrderList;

    @TableField(exist = false)
    private Boolean onFocus;

    @TableField(exist = false)
    private Boolean hasChoice = true;

    @TableField(exist = false)
    private Boolean isNotice = false;

    @TableField(exist = false)
    private Boolean stockIsZero = false;

    @TableField(exist = false)
    private Boolean showDate = true;

    @TableField(exist = false)
    private Boolean isWeeks = true;

    @TableField(exist = false)
    private GbDepartmentEntity stockDepartment;

    @TableField(exist = false)
    private List<GbDepartmentGoodsStockEntity> goodsStockEntityList;

    @TableField(exist = false)
    private List<GbDepartmentGoodsStockEntity> outGoodsStockEntityList;

    @TableField(exist = false)
    private GbDepartmentGoodsStockEntity selfControlStockEntity;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GbDepartmentOrdersEntity that = (GbDepartmentOrdersEntity) o;
        return gbDoArriveDate.equals(that.gbDoArriveDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gbDoArriveDate);
    }

    @Override
    public int compareTo(Object o) {
        if (o instanceof GbDepartmentOrdersEntity) {
            GbDepartmentOrdersEntity e = (GbDepartmentOrdersEntity) o;
            return e.gbDoArriveDate.compareTo(this.gbDoArriveDate);
        }
        return 0;
    }
}
