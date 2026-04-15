package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;


@Setter@Getter@ToString

@TableName("nx_trace_report")
public class NxTraceReportEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 * 溯源报告ID
	 */
	private Integer nxTraceReportId;
	/**
	 * 采购批次ID（关联nx_distributer_purchase_batch）
	 */
	private Integer nxTrBatchId;
	/**
	 * 供应商ID（关联供应商表）
	 */
	private Integer nxTrSupplierId;
	/**
	 * 供应商名称（冗余字段，便于查询显示）
	 */
	private String nxTrSupplierName;
	/**
	 * 供应商联系方式
	 */
	private String nxTrSupplierContact;
	/**
	 * 采购日期
	 */
	private String nxTrPurchaseDate;
	/**
	 * 入库日期
	 */
	private String nxTrStockInDate;

}
