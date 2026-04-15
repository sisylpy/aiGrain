package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;


@Setter@Getter@ToString

@TableName("gb_report")
public class GbReportEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  
	 */
	@TableId(type = IdType.AUTO)
	private Integer gbReportId;
	/**
	 *  
	 */
	private String gbRepIds;
	/**
	 *  
	 */
	private String gbRepType;
	private String gbRepStartDate;
	private String gbRepStopDate;
	/**
	 *  
	 */
	private Integer gbRepDisUserId;

	private GbDepartmentEntity gbDepartmentEntity;

}
