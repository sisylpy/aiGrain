package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;


@Setter@Getter@ToString

@TableName("gb_dep_father_goods_settle")
public class GbDepFatherGoodsSettleEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  
	 */
	private Integer gbDepFatherGoodsSettleStaticsId;
	/**
	 *  
	 */
	private String gbDfgssFatherGoodsName;
	private Integer gbDfgssFatherGoodsId;
	/**
	 *  
	 */
	private Integer gbDfgssFathersFatherId;
	/**
	 *  
	 */
	private Integer gbDfgssFatherGoodsLevel;
	/**
	 *  
	 */
	private Integer gbDfgssDepartmentFatherId;
	/**
	 *  
	 */
	private Integer gbDfgssDistributerId;
	/**
	 *  
	 */
	private String gbDfgssOutStockSubtotal;

	/**
	 *  
	 */
	private Integer gbDfgssSettleId;
	private String gbDfgssSettleMonth;
	private String gbDfgssSettleYear;

	private Integer gbDfgssOutStockType;

}
