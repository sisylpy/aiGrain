package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;


@Setter@Getter@ToString

@TableName("gb_out_stock")
public class GbOutStockEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private String percent;
	private String showPercent;
	private String total;
	private List<GbDistributerFatherGoodsEntity> arr;




}
