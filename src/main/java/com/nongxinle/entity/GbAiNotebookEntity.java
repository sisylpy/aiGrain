package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("gb_ai_notebook")
public class GbAiNotebookEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbAiNbId;

    private Long gbAiNbUserId;

    private Long gbAiNbAnchorDistributerId;

    private Long gbAiNbAnchorDepartmentId;

    private String gbAiNbName;

    private String gbAiNbDescription;

    private Date gbAiNbCreatedAt;

    private Date gbAiNbUpdatedAt;
}
