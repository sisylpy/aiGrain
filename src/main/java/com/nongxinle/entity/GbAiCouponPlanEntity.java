package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;


@Data
@TableName("gb_ai_coupon_plan")
@EqualsAndHashCode(callSuper = false)
public class GbAiCouponPlanEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  方案ID
	 */
	@TableId
	private Long gbAiCouponPlanId;
	/**
	 *  部门ID
	 */
	private Long gbAiCouponPlanDepartmentId;
	/**
	 *  分配者ID
	 */
	private Long gbAiCouponPlanDistributerId;
	/**
	 *  优惠券名称
	 */
	private String gbAiCouponPlanName;
	/**
	 *  优惠券类型 (discount/cash/gift)
	 */
	private String gbAiCouponPlanType;
	/**
	 *  优惠金额/折扣
	 */
	private BigDecimal gbAiCouponPlanValue;
	/**
	 *  使用门槛 (满X元可用)
	 */
	private BigDecimal gbAiCouponPlanThreshold;
	/**
	 *  发行数量
	 */
	private Integer gbAiCouponPlanTotalCount;
	/**
	 *  已领取数量
	 */
	private Integer gbAiCouponPlanReceivedCount;
	/**
	 *  已使用数量
	 */
	private Integer gbAiCouponPlanUsedCount;
	/**
	 *  开始时间
	 */
	private Date gbAiCouponPlanStartTime;
	/**
	 *  结束时间
	 */
	private Date gbAiCouponPlanEndTime;
	/**
	 *  有效期天数
	 */
	private Integer gbAiCouponPlanValidDays;
	/**
	 *  状态 (0=待发布,1=进行中,2=已结束)
	 */
	private Integer gbAiCouponPlanStatus;
	/**
	 *  AI分析内容
	 */
	private String gbAiCouponPlanAnalysis;
	/**
	 *  创建时间
	 */
	private Date gbAiCouponPlanCreateTime;
}
