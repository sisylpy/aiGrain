-- nx_goods：记录来源批发商、商品状态（如由批发商扩充目录写入农鑫时标记来源）。
-- 若存在库名前缀差异（如无 schema 前缀），请将 `nongxinle` 改为实际库名；或先：`USE nongxinle;` 后对 `nx_goods` 执行。

ALTER TABLE `nongxinle`.`nx_goods`
  ADD COLUMN `nx_from_gb_distributer_id` int DEFAULT NULL COMMENT '来源批发商ID（可由批发商扩充目录等非主数据链路写入时使用）',
  ADD COLUMN `nx_goods_status` int DEFAULT NULL COMMENT '农鑫商品业务状态（具体取值由产品/枚举约定）';
