package com.nongxinle.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 区间内按「记账部门」汇总的毛利额侧指标（与集团 {@code GROUP_SQL_ROLLUP} 行级语义一致）。 */
@Data
public class AiRecordingDeptRevenueAggRow {
    private Integer departmentId;
    private BigDecimal grossRevenue;
    private BigDecimal totalOrders;
    /** 该记账部门在区间内的去重营业日天数 */
    private Integer distinctRecordDates;
    /** 外卖营业额合计（区间内） */
    private BigDecimal takeoutRevenue;
    /** 平台抽成等费用合计（区间内） */
    private BigDecimal platformFee;
}
