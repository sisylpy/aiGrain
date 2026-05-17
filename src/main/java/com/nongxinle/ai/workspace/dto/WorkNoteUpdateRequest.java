package com.nongxinle.ai.workspace.dto;

import lombok.Data;

@Data
public class WorkNoteUpdateRequest {

    private Long userId;

    private String title;

    /** 允许 ""；omit null 表示不改 */
    private String content;
}
