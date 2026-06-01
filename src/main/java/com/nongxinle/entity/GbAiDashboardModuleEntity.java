package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户看板模块表
 */
@Data
@TableName("gb_ai_dashboard_module")
public class GbAiDashboardModuleEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dashboardId;
    private String moduleKey;
    private String moduleType;
    private String moduleTitle;
    private Integer position;
    private Integer enabled;
    private Integer removable;
    private Integer configurable;
    private String configJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
