package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;


@Data
@TableName("gb_ai_restaurant_profile")
@EqualsAndHashCode(callSuper = false)
public class GbAiRestaurantProfileEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 *  餐厅画像ID
	 */
	@TableId(type = IdType.AUTO)
	private Long gbAiRestaurantProfileId;
	/**
	 *  部门ID
	 */
	private Long gbAiRestaurantProfileDepartmentId;
	/**
	 *  分配者ID
	 */
	private Long gbAiRestaurantProfileDistributerId;
	/**
	 *  餐厅名称
	 */
	private String gbAiRestaurantProfileRestaurantName;
	/**
	 *  地址
	 */
	private String gbAiRestaurantProfileAddress;
	/**
	 *  经度
	 */
	private BigDecimal gbAiRestaurantProfileLongitude;
	/**
	 *  纬度
	 */
	private BigDecimal gbAiRestaurantProfileLatitude;
	/**
	 *  商圈
	 */
	private String gbAiRestaurantProfileBusinessDistrict;
	/**
	 *  营业时间
	 */
	private String gbAiRestaurantProfileBusinessHours;
	/**
	 *  菜系类型
	 */
	private String gbAiRestaurantProfileCuisineType;
	/**
	 *  人均价格
	 */
	private BigDecimal gbAiRestaurantProfileAvgPrice;
	/**
	 *  座位数
	 */
	private Integer gbAiRestaurantProfileSeatCount;
	/**
	 *  经营阶段
	 */
	private String gbAiRestaurantProfileBusinessStage;
	/**
	 *  粉丝数量
	 */
	private Integer gbAiRestaurantProfileFollowerCount;
	/**
	 *  日均客流
	 */
	private Integer gbAiRestaurantProfileDailyCustomers;
	/**
	 *  日均营收
	 */
	private BigDecimal gbAiRestaurantProfileDailyRevenue;
	/**
	 *  目标客群年龄
	 */
	private String gbAiRestaurantProfileTargetAgeRange;
	/**
	 *  目标消费者
	 */
	private String gbAiRestaurantProfileTargetConsumer;
	/**
	 *  附近竞争对手数量
	 */
	private Integer gbAiRestaurantProfileNearbyCompetitorCount;
	/**
	 *  市场饱和度
	 */
	private String gbAiRestaurantProfileMarketSaturation;
	/**
	 *  竞争优势
	 */
	private String gbAiRestaurantProfileCompetitiveAdvantage;
	/**
	 *  竞品分析
	 */
	private String gbAiRestaurantProfileCompetitorAnalysis;
	/**
	 *  竞品分析时间
	 */
	private Date gbAiRestaurantProfileCompetitorAnalyzedTime;
	/**
	 *  老板姓名
	 */
	private String gbAiRestaurantProfileBossName;
	/**
	 *  老板风格
	 */
	private String gbAiRestaurantProfileBossStyle;
	/**
	 *  风险偏好
	 */
	private String gbAiRestaurantProfileRiskPreference;
	/**
	 *  决策速度
	 */
	private String gbAiRestaurantProfileDecisionSpeed;
	/**
	 *  价格敏感度
	 */
	private Integer gbAiRestaurantProfileCostSensitive;
	/**
	 *  后厨容量
	 */
	private Integer gbAiRestaurantProfileKitchenCapacity;
	/**
	 *  员工数量
	 */
	private Integer gbAiRestaurantProfileStaffCount;
	/**
	 *  月租金
	 */
	private BigDecimal gbAiRestaurantProfileRentMonthly;
	/**
	 *  最后聊天时间
	 */
	private Date gbAiRestaurantProfileLastChatTime;
	/**
	 *  总聊天次数
	 */
	private Integer gbAiRestaurantProfileTotalChatCount;
	/**
	 *  摘要
	 */
	private String gbAiRestaurantProfileSummary;
	/**
	 *  创建时间
	 */
	private Date gbAiRestaurantProfileCreateTime;
	/**
	 *  更新时间
	 */
	private Date gbAiRestaurantProfileUpdateTime;
	/**
	 *  月工资
	 */
	private BigDecimal gbAiRestaurantProfileMonthlyWage;
	/**
	 *  月固定成本
	 */
	private BigDecimal gbAiRestaurantProfileMonthlyFixedCost;
}
