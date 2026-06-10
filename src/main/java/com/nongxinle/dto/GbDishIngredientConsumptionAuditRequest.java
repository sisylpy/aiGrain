package com.nongxinle.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;
import lombok.Setter;
import lombok.AccessLevel;

/**
 * 单菜配料消耗排查（{@code /gbDishCostAnalysis/dishIngredientConsumptionAudit}）JSON 请求体。
 */
@Data
public class GbDishIngredientConsumptionAuditRequest {

    private String startDate;

    /** 区间结束日；与 {@link #endDate} 二选一 */
    private String stopDate;

    private String endDate;

    /** 批发商 id */
    @Setter(AccessLevel.NONE)
    private Integer disId;

    /** 兼容字段名 */
    @Setter(AccessLevel.NONE)
    private Integer distributerId;

    @Setter(AccessLevel.NONE)
    private Integer depFatherId;

    @Setter(AccessLevel.NONE)
    @JsonAlias("subDepid")
    private Integer subDepId;

    /** 菜品 id（与 {@link #foodId} 二选一） */
    @Setter(AccessLevel.NONE)
    private Integer dishId;

    @Setter(AccessLevel.NONE)
    private Integer foodId;

    @JsonSetter("disId")
    public void setDisId(Object value) {
        this.disId = coerceNullableInteger(value);
    }

    @JsonSetter("distributerId")
    public void setDistributerId(Object value) {
        this.distributerId = coerceNullableInteger(value);
    }

    @JsonSetter("depFatherId")
    public void setDepFatherId(Object value) {
        this.depFatherId = coerceNullableInteger(value);
    }

    @JsonSetter("subDepId")
    public void setSubDepId(Object value) {
        this.subDepId = coerceNullableInteger(value);
    }

    @JsonSetter("dishId")
    public void setDishId(Object value) {
        this.dishId = coerceNullableInteger(value);
    }

    @JsonSetter("foodId")
    public void setFoodId(Object value) {
        this.foodId = coerceNullableInteger(value);
    }

    static Integer coerceNullableInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        return Integer.valueOf(s);
    }

    public Integer resolvedDisId() {
        return disId != null ? disId : distributerId;
    }

    public Integer resolvedFoodId() {
        return dishId != null ? dishId : foodId;
    }

    public String resolvedEndDate() {
        if (stopDate != null && !stopDate.trim().isEmpty()) {
            return stopDate.trim();
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            return endDate.trim();
        }
        return null;
    }
}
