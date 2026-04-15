CREATE TABLE `gb_distributer_supplier_payment` (
  `gb_distributer_supplier_payment_id` int NOT NULL AUTO_INCREMENT COMMENT '支付id',
  `gb_dsp_date` varchar(50) DEFAULT NULL COMMENT '支付日期',
  `gb_dsp_supplier_id` int DEFAULT NULL COMMENT '供应商id',
  `gb_dsp_pay_user_id` int DEFAULT NULL COMMENT '支付用户id',
  `gb_dsp_nx_distributer_id` int DEFAULT NULL COMMENT '配送商id',
  `gb_dsp_wx_out_trade_no` varchar(100) DEFAULT NULL COMMENT '微信交易号',
  `gb_dsp_status` int DEFAULT NULL COMMENT '支付状态',
  `gb_dsp_pay_user_open_id` varchar(100) DEFAULT NULL COMMENT '支付用户openid',
  `gb_dsp_pay_total` varchar(50) DEFAULT NULL COMMENT '支付金额',
  `gb_dsp_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `gb_dsp_pay_full_time` varchar(50) DEFAULT NULL COMMENT '支付完整时间',
  PRIMARY KEY (`gb_distributer_supplier_payment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批发商供应商支付表';
