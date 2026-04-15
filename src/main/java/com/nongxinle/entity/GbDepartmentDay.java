package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;


@Setter@Getter@ToString

@TableName("gb_department_day")
public class GbDepartmentDay implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  订货部门id
	 */
	private String  day;
	/**
	 *  订货部门名称
	 */
	private Double dayStockTotal;

}
