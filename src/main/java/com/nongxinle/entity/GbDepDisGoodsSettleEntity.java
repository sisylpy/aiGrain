package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;


@Setter@Getter@ToString

@TableName("gb_dep_dis_goods_settle")
public class GbDepDisGoodsSettleEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  社区商品id
	 */
	private Integer gbDepDisGoodsSettleId;
	/**
	 *  批发商父类商品id
	 */
	private Integer gbDdgsDfgGoodsFatherId;
	/**
	 *  批发商id
	 */
	private Integer gbDdgsDistributerId;
	/**
	 *  商品名称
	 */
	private Integer gbDdgsDisGoodsId;
	/**
	 *  商品名称
	 */
	private String gbDdgsDisGoodsName;
	/**
	 *  商品规格
	 */
	private String gbDdgsDisGoodsStandardname;
	/**
	 *  
	 */
	private String gbDdgsDisGoodsStandardWeight;
	/**
	 *  
	 */
	private Integer gbDdgsDisGoodsType;
	/**
	 *  价格
	 */
	private String gbDdgsDisGoodsPrice;
	/**
	 *  
	 */
	private String gbDdgsDisGoodsLowestPrice;
	/**
	 *  
	 */
	private String gbDdgsDisGoodsHighestPrice;
	/**
	 *  是否控制价格
	 */
	private Integer gbDdgsDisControlPrice;
	/**
	 *  是否控制鲜度
	 */
	private Integer gbDdgsDisControlFresh;
	/**
	 *  鲜度报警小时
	 */
	private String gbDdgsDisFreshWarnHour;
	/**
	 *  废弃报警小时
	 */
	private String gbDdgsDisFreshWasteHour;
	/**
	 *  盘库方式1 月，2周，3日
	 */
	private Integer gbDdgsDisGoodsInventoryType;
	/**
	 *  盘库方式1 月，2周，3日
	 */
	private Integer gbDdgsDisGoodsInventoryDepId;
	/**
	 *  商品状态
	 */
	private Integer gbDdgsStatus;
	/**
	 *  库房或中央厨房id
	 */
	private Integer gbDdgsSettleDepartmentId;
	/**
	 *  库房或中央厨房id
	 */
	private Integer gbDdgsSettleDepartmentFatherId;
	/**
	 *  废弃报警小时
	 */
	private String gbDdgsSettleAmount;
	/**
	 *  废弃报警小时
	 */
	private String gbDdgsSettleSubtotal;

	private String gbDdgsSalesAmount;
	private String gbDdgsSalesSubtotal;
	/**
	 *  库房或中央厨房id
	 */
	private Integer gbDdgsSettleId;
	/**
	 *  废弃报警小时
	 */
	private String gbDdgsSettleMonth;
	/**
	 *  废弃报警小时
	 */
	private String gbDdgsSettleYear;

	private GbDistributerGoodsEntity gbDistributerGoodsEntity;

}
