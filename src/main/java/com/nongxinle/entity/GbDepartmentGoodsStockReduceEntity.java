package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;


@Setter@Getter@ToString

@TableName("gb_department_goods_stock_reduce")
public class GbDepartmentGoodsStockReduceEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	@TableId(type = IdType.AUTO)
	private Integer gbDepartmentGoodsStockReduceId;
	private Integer gbDgsrDepartmentId;
	private Integer gbDgsrDepartmentFatherId;
	private Integer gbDgsrDistributerId;
	private Integer gbDgsrDisGoodsId;
	private Integer gbDgsrDepDisGoodsId;
	private Integer gbDgsrGoodsStockId;
	private Integer gbDgsrType;
	private String gbDgsrWeight;
	private String gbDgsrSubtotal;
	private String gbDgsrDate;
	private String gbDgsrFullTime;
	private Integer gbDgsrUserId;
	private Integer gbDgsrDepSettleId;
	private String gbDgsrWeek;
	private String gbDgsrMonth;
	private String gbDgsrYear;
	
	// 字段重构说明：
	// 原表结构设计有独立字段：cost_weight、cost_subtotal、waste_weight、waste_subtotal、
	// loss_weight、loss_subtotal、return_weight、return_subtotal、produce_weight、produce_subtotal
	// 
	// 现已统一使用 gb_dgsr_weight 和 gb_dgsr_subtotal 字段
	// 通过 gb_dgsr_type 字段区分类型：1=生产，2=损耗，3=损失，4=退货
	// 
	// 这一变化简化了表结构，提高代码可维护性，避免字段重复冗余
	// 在业务逻辑中通过type参数区分不同业务场景的重量和金额计算

    @TableField(exist = false)
    private GbDepartmentEntity gbDepartmentEntity;
    @TableField(exist = false)
    private GbDistributerGoodsEntity gbDistributerGoodsEntity;
    @TableField(exist = false)
    private GbDepartmentUserEntity gbDepartmentUserEntity;
    @TableField(exist = false)
    private GbDistributerPurchaseGoodsEntity gbDisPurchaseGoodsEntity;
    @TableField(exist = false)
    private GbDepartmentGoodsStockReduceAttachmentEntity gbDeGoodsStockReduceAttachmentEntity;
    @TableField(exist = false)
	private GbDepartmentGoodsStockEntity gbDepartmentGoodsStockEntity;
    @TableField(exist = false)
	private GbDepartmentGoodsStockRecordEntity gbDepartmentGoodsStockRecordEntity;

}
