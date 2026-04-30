-- 父级/分类 毛利率标准（与 blendedGrossMarginRateOnListPrice 同口径 0~100%）
-- 带内：[gb_df_target_gross_margin_rate - gb_df_gross_margin_float_abs, T+F]
-- 在应用库执行前请将库名改为实际库名，或去掉下一行中的库名限定。

ALTER TABLE `gb_distributer_food`
  ADD COLUMN `gb_df_target_gross_margin_rate` decimal(5,2) NULL DEFAULT NULL COMMENT '目标综合毛利率(%)' AFTER `gb_df_goods_sort`,
  ADD COLUMN `gb_df_gross_margin_float_abs`   decimal(5,2) NULL DEFAULT NULL COMMENT '目标毛利率上下浮动绝对百分点' AFTER `gb_df_target_gross_margin_rate`;
