package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;


@Setter@Getter@ToString

@TableName("gb_department_goods_stock_reduce_attachment")
public class GbDepartmentGoodsStockReduceAttachmentEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  
	 */
	private Integer gbDepartmentGoodsStockReduceAttachId;
	/**
	 *  
	 */
	private Integer gbDgsraGbDgsrId;
	/**
	 *  
	 */
	private String gbDgsraContent;
	/**
	 *  
	 */
	private String gbDgsraFilePath;
	private String gbDgsraFileLargePath;
	private Integer gbDgsraType;
	private Integer gbDgsraStars;
	/**
	 *  
	 */
	private Integer gbDgsraStatus;
	private Integer gbDgsraNxSupplierId;
	private Integer gbDgsraNxDistributerId;

}
