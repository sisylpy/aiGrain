package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("gb_ai_tag")
public class GbAiTagEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbAiTagId;

    private Long gbAiTagUserId;

    /** 与会话 gb_ai_conversation_distributer_id 对齐；空视为 0 */
    private Long gbAiTagAnchorDistributerId;

    /** 与会话 gb_ai_conversation_department_id 对齐；空视为 0 */
    private Long gbAiTagAnchorDepartmentId;

    private String gbAiTagName;

    private String gbAiTagColor;

    private Date gbAiTagCreatedAt;
}
