package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户看板表
 */
@Data
@TableName("gb_ai_dashboard")
public class GbAiDashboardEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long distributerId;
    private String dashboardCode;
    private String dashboardName;
    private Integer isDefault;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
