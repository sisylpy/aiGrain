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
	private Integer gbDgsrGbDepartmentId;
	private Integer gbDgsrGbDepartmentFatherId;
	private Integer gbDgsrGbDistributerId;
	private Integer gbDgsrGbDisGoodsId;
	private Integer gbDgsrGbDepDisGoodsId;
	private Integer gbDgsrGbGoodsStockId;

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


	/**
	 *
	 */
	private Integer gbDgsrGbDisGoodsFatherId;
	private Integer gbDgsrGbDisGoodsGrandId;
	private Integer gbDgsrGbDisGoodsGreatId;
	/** 对应出库所扣库存批次的 {@code gb_department_goods_stock.gb_dgs_nx_supplier_id}：{@code -1}=自采；非空且不等于 {@code -1}（通常为正整数）= 供货商/配送商入库；{@code null}=未回填，勿默认自采或配送。 */
	@TableField("gb_dgsr_stock_nx_supplier_id")
	private Integer gbDgsrStockNxSupplierId;
	private Integer gbDgsrStatus;
	private Integer gbDgsrStockPurUserId;
	private Integer gbDgsrGbPurGoodsId;
	
	// 字段重构说明：
	// 原表结构设计有独立字段：cost_weight、cost_subtotal、waste_weight、waste_subtotal、
	// loss_weight、loss_subtotal、return_weight、return_subtotal、produce_weight、produce_subtotal
	// 
	// 现已统一使用 gb_dgsr_weight 和 gb_dgsr_subtotal 字段
	// 通过 gb_dgsr_type 区分：1=生产耗用；2=废弃；3=损耗（口语常称报损）；4=退货（与 {@link com.nongxinle.utils.GbConstants.StockReduceType} 一致）
	// 
	// 这一变化简化了表结构，提高代码可维护性，避免字段重复冗余
	// 在业务逻辑中通过type参数区分不同业务场景的重量和金额计算

	/** 小程序展示用：与 gbDgsrType 对应，仅一对有值（由 gbDgsrWeight/gbDgsrSubtotal 回填） */
	@TableField(exist = false)
	private String gbDgsrProduceWeight;
	@TableField(exist = false)
	private String gbDgsrProduceSubtotal;
	@TableField(exist = false)
	private String gbDgsrWasteWeight;
	@TableField(exist = false)
	private String gbDgsrWasteSubtotal;
	@TableField(exist = false)
	private String gbDgsrLossWeight;
	@TableField(exist = false)
	private String gbDgsrLossSubtotal;
	@TableField(exist = false)
	private String gbDgsrReturnWeight;
	@TableField(exist = false)
	private String gbDgsrReturnSubtotal;

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
