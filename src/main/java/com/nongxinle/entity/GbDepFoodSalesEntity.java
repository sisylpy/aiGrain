package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;


@Setter@Getter@ToString

@TableName("gb_dep_food_sales")
public class GbDepFoodSalesEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private Integer gbDepFoodSalesId;
	private Integer gbDfsDepId;
	private Integer gbDfsFoodId;
	private Integer gbDfsDepFatherId;
	private String gbDfsAmount;
	private Integer gbDfsSettleId;
	private String gbDfsMonth;
	private String gbDfsFullDate;
	private Integer gbDfsUserId;
	private String gbDfsYear;
	private String gbDfsSubtotal;
	private Integer gbDfsDistributerId;
}
