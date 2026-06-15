package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("gb_work_record_category")
public class GbWorkRecordCategoryEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbWrcId;

    private String gbWrcCode;

    private String gbWrcName;

    private String gbWrcDescription;

    private Integer gbWrcSortOrder;

    /** ACTIVE / DISABLED */
    private String gbWrcStatus;

    /** SYSTEM / MANUAL / AI_SUGGESTED */
    private String gbWrcSource;

    /** 0 = 全集团共享系统默认 */
    private Long gbWrcDistributerId;

    private Date gbWrcCreatedAt;

    private Date gbWrcUpdatedAt;
}
