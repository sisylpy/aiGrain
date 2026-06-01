package com.nongxinle.ai.composer.menu;

/** menuFactPack.dishRows 中 Guard 校验用的菜品指标快照。 */
public record MenuExpertFactDishRow(
        String dishName, String blendedGrossMarginRateOnListPrice, String actualProfitAmount) {}
