package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;


@Setter@Getter@ToString

@TableName("gb_department_bill")
public class GbDepartmentBillEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  
	 */
	private Integer gbDepartmentBillId;
	/**
	 *  
	 */
	private Integer gbDbDisId;
	/**
	 *  
	 */
	private Integer gbDbDepId;
	private Integer gbDbDepFatherId;
	/**
	 *  
	 */
	private String gbDbTotal;
	/**
	 *  
	 */
	private Integer gbDbStatus;
	/**
	 *  
	 */
	private String gbDbTime;
	/**
	 *  
	 */
	private Integer gbDbIssueUserId;
	/**
	 *  
	 */
	private String gbDbDate;

	private String gbDbWillPayDate;
	/**
	 *  
	 */
	private String gbDbMonth;
	private String gbDbYear;
	/**
	 *  
	 */
	private String gbDbWeek;
	/**
	 *  
	 */
	private String gbDbTradeNo;
	/**
	 *  
	 */
	private Integer gbDbPrintTimes;
	/**
	 *  星期
	 */
	private String gbDbDay;
	private Integer gbDbIssueOrderType;
	private Integer gbDbIssueDepId;
	private Integer gbDbOrderAmount;
	private Integer gbDbGbSupplierPaymentId;
//
	private Integer	gbDbConfirmGoodsUserId;
	private Integer	gbDbConfirmPriceUserId;
	private Integer	gbDbConfirmSettleUserId;
	private String	gbDbConfirmGoodsTime;
	private String	gbDbConfirmPriceTime;
	private String	gbDbConfirmSettleTime;
	private String	gbDbSellingTotal;
	private Integer gbDbDepSettleId;
	private Integer gbDbIssueNxDisId;
	private Integer gbDbSetAutoGoods;

	private Integer gbDbUserCouponId;
	private Integer gbDbReturnOrderId;
	private  String gbUserOpenId;
	private  String gbDbWxOutTradeNo;
	private  String gbDbPayTotal;
	private  String gbDbUserCouponTotal;
	private  String gbDbReturnTotal;
	private  String gbDbGreatCouponTotal;
	private  String gbDbChaTotal;


	private List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities;
	private List<GbDepartmentEntity> orderDepartments;
	private GbDepartmentEntity gbDepartmentEntity;
	private GbDepartmentEntity issueDepartmentEntity;
	private GbDepartmentUserEntity issueUserEntity;




}
