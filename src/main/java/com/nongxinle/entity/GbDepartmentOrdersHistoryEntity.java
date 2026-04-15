package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;


@Setter@Getter@ToString

@TableName("gb_department_orders_history")
public class GbDepartmentOrdersHistoryEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  部门订单id
	 */
	private Integer gbDepartmentOrdersHistoryId;
	/**
	 *  部门id
	 */
	private Integer gbDohDepDisGoodsId;
	/**
	 *  部门订单申请数量
	 */
	private String gbDohQuantity;
	/**
	 *  部门订单申请规格
	 */
	private String gbDohStandard;
	/**
	 *  部门订单申请备注
	 */
	private String gbDohRemark;
	/**
	 *  部门订单部门id
	 */
	private Integer gbDohDepartmentId;
	/**
	 *  
	 */
	private Integer gbDohDepartmentFatherId;
	/**
	 *  部门订单订货用户id
	 */
	private Integer gbDohOrderUserId;
	/**
	 *  部门订单申请时间
	 */
	private String gbDohApplyDate;
	/**
	 *  出货方式0,日采;1,出库;2,供货商;3,加工
	 */
	private Integer gbDohSellType;

	private  Integer gbDohStandardId;
	private  Integer gbDohDisGoodsId;
	private  Integer gbDohDistributerId;
	private String gbDohStandardScale;

	private GbDistributerGoodsEntity gbDistributerGoodsEntity;

	private List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities;

}
