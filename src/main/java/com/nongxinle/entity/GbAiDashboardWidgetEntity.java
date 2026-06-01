package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户关注卡片表
 */
@Data
@TableName("gb_ai_dashboard_widget")
public class GbAiDashboardWidgetEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dashboardId;
    private Long moduleId;
    private String widgetType;
    private String title;
    private Integer position;
    private Integer enabled;
    private String configJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
