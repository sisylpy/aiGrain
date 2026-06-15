package com.nongxinle.ai.workrecord.dto;

import lombok.Data;

@Data
public class WorkRecordCreateRequest {

    private Long userId;
    private Long distributerId;
    private Long departmentId;
    /** TEXT / VOICE_TRANSCRIPT */
    private String inputType;
    private String content;
    /** 可选；缺省为提交时刻 */
    private String recordedAt;
}
