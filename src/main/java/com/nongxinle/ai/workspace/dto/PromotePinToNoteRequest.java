package com.nongxinle.ai.workspace.dto;

import lombok.Data;

@Data
public class PromotePinToNoteRequest {

    private Long userId;

    /** 覆盖默认（Pin.title）；可空 */
    private String title;

    /**
     * 覆盖默认正文；可空表示使用 Pin 的 {@code sourceTextSnapshot} 作为初始 Markdown，
     * 便于用户直接在笔记里编辑。
     */
    private String content;
}
