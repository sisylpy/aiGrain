package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;


@Setter@Getter@ToString

@TableName("gb_distributer_supplier")
public class GbDistributerSupplierEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  供货商id
	 */
	private Integer gbDistributerSupplierId;
	/**
	 *  供货商名称
	 */
	private String gbDistributerSupplierName;
	/**
	 *  gbDisid
	 */
	private Integer gbDsGbDistributerId;
	private Integer gbDsGbDepartmentId;
	private Integer gbDistributerSupplierFatherId;
	private Integer gbDsSupplierIsGroup;
	private Integer gbDsOrderType;
	private Integer gbDsSupplierUserId;
	private Integer gbDsPurUserId;

	private GbDistributerSupplierUserEntity gbDisApplintSupplierUserEntity;
    private GbDepartmentUserEntity purUserEntity;
    private List<GbDistributerGoodsEntity> gbDistributerGoodsEntities;



}
