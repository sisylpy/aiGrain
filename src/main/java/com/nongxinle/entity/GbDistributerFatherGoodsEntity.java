package com.nongxinle.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 批发商<strong>商品</strong>父类实体（{@code gb_distributer_father_goods}，配料/主档商品类目树）。
 * <p><strong>与「菜品父类」区分</strong>：菜品分类、父菜下挂子菜请用 {@link GbDistributerFoodEntity#getGbDfFoodFatherId()}
 * 与同表上的 {@link GbDistributerFoodEntity#getFoodEntityList()}，勿与本实体下的 {@link #getGbDistributerGoodsEntities()} 混淆。</p>
 */
@Data
@TableName("gb_distributer_father_goods")
@EqualsAndHashCode(callSuper = false)
public class GbDistributerFatherGoodsEntity implements Serializable, Comparable<GbDistributerFatherGoodsEntity> {
    private static final long serialVersionUID = 1L;

    @Override
    public int compareTo(GbDistributerFatherGoodsEntity o) {
        // 按 gbDfgFatherGoodsSort 排序
        if (this.gbDfgFatherGoodsSort == null && o.gbDfgFatherGoodsSort == null) {
            return 0;
        }
        if (this.gbDfgFatherGoodsSort == null) {
            return 1;
        }
        if (o.gbDfgFatherGoodsSort == null) {
            return -1;
        }
        return this.gbDfgFatherGoodsSort.compareTo(o.gbDfgFatherGoodsSort);
    }

    @TableId(type = IdType.AUTO)
    private Integer gbDistributerFatherGoodsId;
    private Integer gbDfgDistributerId;
    private String gbDfgFatherGoodsName;
    private Integer gbDfgFatherGoodsLevel;
    private Integer gbDfgGoodsAmount;
    private Integer gbDfgPriceAmount;
    private Integer gbDfgPriceTwoAmount;
    private Integer gbDfgPriceThreeAmount;
    private String gbDfgFatherGoodsColor;
    private Integer gbDfgNxGoodsId;
    private String gbDfgFatherGoodsImg;
    private Integer gbDfgFatherGoodsSort;
    private Integer gbDfgFathersFatherId;
    private String gbDfgFatherGoodsImgLarge;

    // 非数据库字段
    @TableField(exist = false)
    private List<GbDistributerGoodsEntity> gbDistributerGoodsEntities;

    @TableField(exist = false)
    private NxGoodsEntity nxGoodsEntity;

    @TableField(exist = false)
    private List<GbDistributerFatherGoodsEntity> fatherGoodsEntities;

    @TableField(exist = false)
    private TreeSet<GbDistributerFatherGoodsEntity> treeFatherGoodsEntities;

    @TableField(exist = false)
    private List<GbDistributerPurchaseGoodsEntity> gbDistributerPurchaseGoodsEntities;

    @TableField(exist = false)
    private List<GbDepartmentDisGoodsEntity> gbDepartmentDisGoodsEntities;

    @TableField(exist = false)
    private List<GbDepartmentGoodsStockEntity> gbDepartmentGoodsStockEntities;

    @TableField(exist = false)
    private String GbDgGoodsSubNames;

    @TableField(exist = false)
    private Map<String, Object> dailyData;

    @TableField(exist = false)
    private Boolean isSelected = false;

    @TableField(exist = false)
    private BigDecimal purchaseSubTotal;

    @TableField(exist = false)
    private double purchaseSubTotalDouble;

    // 用于 disGetDepDisGoodsCataGb 查询的别名字段
    @TableField(exist = false)
    private Integer ggGbDistributerFatherGoodsId;
    @TableField(exist = false)
    private Integer ggGbDfgFathersFatherId;
    @TableField(exist = false)
    private String ggGbDfgFatherGoodsName;
    @TableField(exist = false)
    private String ggGbDfgFatherGoodsImg;
    @TableField(exist = false)
    private String ggGbDfgFatherGoodsImgLarge;
    @TableField(exist = false)
    private Integer ggGbDfgFatherGoodsSort;

    @TableField(exist = false)
    private Integer gGbDistributerFatherGoodsId;
    @TableField(exist = false)
    private Integer gGbDfgFathersFatherId;
    @TableField(exist = false)
    private String gGbDfgFatherGoodsName;
    @TableField(exist = false)
    private Integer gGbDfgFatherGoodsSort;

    @TableField(exist = false)
    private String fatherGoodsTotal;

    @TableField(exist = false)
    private String fatherGoodsPercent;

    // 页面需要的库存相关字段
    @TableField(exist = false)
    @JSONField(name = "fatherStockTotalString")
    private String fatherStockTotalString; // 库存金额字符串

    @TableField(exist = false)
    @JSONField(name = "fatherStockTotalPercent")
    private String fatherStockTotalPercent; // 库存占比百分比

    @TableField(exist = false)
    @JSONField(name = "fatherStockManyString")
    private String fatherStockManyString; // 库存商品数量字符串

    @TableField(exist = false)
    @JSONField(name = "fatherWasteTotalString")
    private String fatherWasteTotalString; // 过期金额字符串

    @TableField(exist = false)
    @JSONField(name = "fatherWasteRateString")
    private String fatherWasteRateString; // 过期商品数量字符串
}
