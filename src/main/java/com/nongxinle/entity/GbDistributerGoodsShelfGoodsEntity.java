package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;


@Setter@Getter@ToString

@TableName("gb_distributer_goods_shelf_goods")
public class GbDistributerGoodsShelfGoodsEntity implements Serializable, Comparable {
	private static final long serialVersionUID = 1L;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		GbDistributerGoodsShelfGoodsEntity that = (GbDistributerGoodsShelfGoodsEntity) o;
		return Objects.equals(gbDistributerGoodsShelfGoodsId, that.gbDistributerGoodsShelfGoodsId) &&
				Objects.equals(gbDgsgDisGoodsId, that.gbDgsgDisGoodsId) &&
				Objects.equals(gbDgsgShelfId, that.gbDgsgShelfId
				);
	}

	@Override
	public int hashCode() {
		return Objects.hash(gbDistributerGoodsShelfGoodsId, gbDgsgDisGoodsId, gbDgsgShelfId);
	}

	/**
	 *  货架商品id
	 *
	 */
	private Integer gbDistributerGoodsShelfGoodsId;
	/**
	 *  批发商商品id
	 */
	private Integer gbDgsgDisGoodsId;
	/**
	 *  货架id
	 */
	private Integer gbDgsgShelfId;
	/**
	 *  货架商品排序
	 */
	private Integer gbDgsgSort;
	private Integer gbDgsgDepFatherId;
	private Integer gbDgsgDepId;
	private Integer gbDgsgDepDisGoodsId;
	private Boolean isSelected = false;

	private GbDistributerGoodsEntity gbDistributerGoodsEntity;
	private GbDepartmentDisGoodsEntity gbDepartmentDisGoodsEntity;

	private List<GbDepartmentGoodsStockEntity> gbDepartmentGoodsStockEntities;


	@Override
	public int compareTo(Object o) {
		if (o instanceof GbDistributerGoodsShelfGoodsEntity) {
			GbDistributerGoodsShelfGoodsEntity e = (GbDistributerGoodsShelfGoodsEntity) o;
			return this.getGbDistributerGoodsShelfGoodsId().compareTo(e.getGbDistributerGoodsShelfGoodsId());
		}
		return 0;
	}
}
