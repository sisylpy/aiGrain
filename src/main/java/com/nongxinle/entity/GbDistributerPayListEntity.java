package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;


@Setter@Getter@ToString

@TableName("gb_distributer_pay_list")
public class GbDistributerPayListEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  
	 */
	private Integer gbDistributerPayListId;
	/**
	 *  
	 */
	private Integer gbNdplGbDisId;
	/**
	 *  
	 */
	private String gbNdplPaySubtotal;
	/**
	 *  
	 */
	private String gbNdplPayTime;
	/**
	 *  
	 */
	private Integer gbNdplType;
	/**
	 *  
	 */
	private Integer gbNdplStatus;
	private Integer gbNdplGbDepartmentFatherId;
	private Integer gbNdplGbDepartmentId;
	/**
	 *  
	 */
	private String gbNdplPayDate;
	/**
	 *  
	 */
	private Integer gbNdplGbPbId;
	/**
	 *  
	 */
	private String gbNdplPayMonth;
	/**
	 *  
	 */
	private String gbNdplPayYear;
	/**
	 *  
	 */
	private String gbNdplRestPoints;
	private Integer gbNdplGbDisGoodsId;
	/**
	 *  
	 */
	private Integer gbNdplNxSupplierId;

	private NxJrdhSupplierEntity nxJrdhSupplierEntity;
	private GbDepartmentEntity gbDepartmentEntity;
	private GbDistributerGoodsEntity gbDistributerGoodsEntity;

}
