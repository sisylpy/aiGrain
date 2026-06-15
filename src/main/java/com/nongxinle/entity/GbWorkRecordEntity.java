package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("gb_work_record")
public class GbWorkRecordEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbWrId;

    private Long gbWrConversationId;

    private Long gbWrSourceMessageId;

    /** 最近一次 AI 处理对应的 Run ID（retry 后会更新为最新 Run，历史 Run 仍在 gb_ai_agent_run）。 */
    private Long gbWrSourceRunId;

    private Long gbWrDistributerId;

    private Long gbWrDepartmentId;

    private Long gbWrRecorderUserId;

    /** TEXT / VOICE_TRANSCRIPT / BUSINESS_CARD */
    private String gbWrInputType;

    /** MANUAL / BUSINESS_CARD */
    private String gbWrOriginType;

    private Long gbWrBizConversationId;
    private Long gbWrBizMessageId;
    private Long gbWrBizRunId;
    private String gbWrBizAnswerPlanType;
    private String gbWrBizCardType;
    private String gbWrBizItemKey;
    private String gbWrBizFactSnapshot;

    private String gbWrRawContent;

    private String gbWrPolishedContent;

    private Long gbWrCategoryId;

    private String gbWrCategoryCode;

    private String gbWrCategoryNameSnapshot;

    /** EXISTING / SUGGEST_NEW / OTHER */
    private String gbWrCategoryDecision;

    private String gbWrSuggestedCategoryName;

    /** PENDING / PROCESSING / SUCCESS / FAILED */
    private String gbWrAiStatus;

    private BigDecimal gbWrAiConfidence;

    private String gbWrAiReason;

    private String gbWrAiErrorCode;

    private Date gbWrRecordedAt;

    private Date gbWrCreatedAt;

    private Date gbWrUpdatedAt;

    /** 0 正常 1 软删 */
    private Integer gbWrStatus;
}
