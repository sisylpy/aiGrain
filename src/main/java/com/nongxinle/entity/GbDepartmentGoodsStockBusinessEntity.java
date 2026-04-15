package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;


@Setter@Getter@ToString

@TableName("gb_department_goods_stock_business")
public class GbDepartmentGoodsStockBusinessEntity implements Serializable,Comparable{
	private static final long serialVersionUID = 1L;

	private String gbBusinessFullTime;
	private String gbBusinessType;
	private String gbBusinessWeight;
	private BigDecimal gbBusinessResultWeight;
	private String gbApplyDepartment ;


	@Override
	public int compareTo(Object o) {
		if (o instanceof GbDepartmentGoodsStockBusinessEntity) {
			GbDepartmentGoodsStockBusinessEntity e = (GbDepartmentGoodsStockBusinessEntity) o;
			return e.getGbBusinessFullTime().compareTo(this.getGbBusinessFullTime());

		}
		return 0;
	}
}
