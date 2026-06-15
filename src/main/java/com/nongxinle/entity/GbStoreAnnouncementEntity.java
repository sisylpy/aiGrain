package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("gb_store_announcement")
public class GbStoreAnnouncementEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbSaId;

    private Long gbSaDistributerId;

    private Long gbSaDepartmentId;

    private Long gbSaPublisherUserId;

    /** TEXT / BUSINESS_CARD / AI_MESSAGE */
    private String gbSaAnnouncementType;

    /** WORK_RECORD / WORK_PIN / DIRECT */
    private String gbSaSourceType;

    private Long gbSaSourceId;

    private String gbSaTitle;

    private String gbSaTextContent;

    private String gbSaCardType;

    private String gbSaCardSnapshotJson;

    private String gbSaCardsSnapshotJson;

    private Long gbSaSourceConversationId;

    private Long gbSaSourceMessageId;

    private Long gbSaSourceRunId;

    /** BUSINESS_CARD 行级来源 itemKey，如 goodsId:101 或 __CARD__ */
    private String gbSaSourceItemKey;

    /** PUBLISHED / DELETED */
    private String gbSaPublishStatus;

    private Date gbSaPublishedAt;

    private Date gbSaCreatedAt;

    private Date gbSaUpdatedAt;
}
