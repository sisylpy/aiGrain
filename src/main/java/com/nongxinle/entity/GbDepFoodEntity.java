package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;
import java.util.Map;


@Data
@TableName("gb_dep_food")
@EqualsAndHashCode(callSuper = false)
public class GbDepFoodEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer gbDepFoodId;
    private Integer gbDfDepId;
    /**
     * 批发商侧菜品 id（gb_distributer_food.gb_distributer_food_id），与配方 gb_distributer_food_goods.gb_dfg_food_id 对应
     */
    private Integer gbDfFoodId;
    private Integer gbDfNxFoodId;
    private String gbDfFoodName;
    private String gbDfFoodPrice;
    private Integer gbDfStatus;
    private String gbDfFoodPinyin;
    private String gbDfFoodPy;
    private String gbDfDepFatherId;
    private Integer gbDfFoodFatherId;
    private String gbDfFoodImg;
    private String gbDfFoodImgLarge;
    private String gbDfFoodMethod;
    private String gbDfFoodDetail;
    private Integer gbDfGoodsSort;
    private Integer gbDfDistributerId;

    @TableField(exist = false)
    private GbDistributerFoodEntity gbDistributerFoodEntity;
    @TableField(exist = false)
    private GbDepartmentEntity gbDepartmentEntity;
    @TableField(exist = false)
    private String gbDfSalesAmount;

    /**
     * 经营分析（与 {@code /gbdepfood/depGetAllFood} 在传 startDate/stopDate 且 disId、depFatherId 齐全时填充）：
     * 周销量、标价收入、成本、毛利率、区间损耗说明等，见 {@link com.nongxinle.service.GbDepFoodBusinessInsightService#buildInsight} 菜品行字段。
     */
    @TableField(exist = false)
    private Map<String, Object> gbDfBusinessInsight;

    /**
     * 与 {@code /gbDishCostAnalysis/ingredientAnalysis} 中 {@code salesDishRows[].ingredientRows} 单条结构一致（仅四参齐全走经营分析时填充）。
     */
    @TableField(exist = false)
    private List<Map<String, Object>> ingredientAnalysisRows;

    public String getGbDfSalesAmount() {
        return gbDfSalesAmount;
    }

    public void setGbDfSalesAmount(String gbDfSalesAmount) {
        this.gbDfSalesAmount = gbDfSalesAmount;
    }
}
