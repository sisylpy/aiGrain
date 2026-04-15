package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;


@Setter@Getter@ToString

@TableName("gb_distributer_goods_shelf")
public class GbDistributerGoodsShelfEntity implements Serializable ,Comparable{
	private static final long serialVersionUID = 1L;
	
	/**
	 *  货架id
	 */
	private Integer gbDistributerGoodsShelfId;
	/**
	 *  货架名称
	 */
	private String gbDistributerGoodsShelfName;
	/**
	 *  货架排序
	 */
	private Integer gbDistributerGoodsShelfSort;
	/**
	 *  批发商id
	 */
	private Integer gbDistributerGoodsShelfDisId;
	private Integer gbDistributerGoodsShelfDepId;

	private List<GbDistributerGoodsShelfGoodsEntity> gbDisGoodsShelfGoodsEntities;
	private TreeSet<GbDistributerGoodsShelfGoodsEntity> treeSet;
	private Boolean isSelected = false;


	@Override
	public int compareTo(Object o) {

		if (o instanceof GbDistributerGoodsShelfEntity) {
			GbDistributerGoodsShelfEntity e = (GbDistributerGoodsShelfEntity) o;
			return this.getGbDistributerGoodsShelfId().compareTo(e.getGbDistributerGoodsShelfId());
		}
		return 0;
	}
}
