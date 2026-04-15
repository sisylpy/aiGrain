package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;


@Setter@Getter@ToString

@TableName("gb_out_stock_arr")
public class GbOutStockArrEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private String lastDouble;
	private String outDouble;
	private String restDouble;
	private String stockDouble;
	private List<GbOutStockEntity> cost;
	private List<GbOutStockEntity> loss;
	private List<GbOutStockEntity> waste;
	private List<GbOutStockEntity> returnS;




}
