package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 企业微信GB批发商用户实体
 */
@Data
@TableName("qy_gb_dis_corp_user")
@EqualsAndHashCode(callSuper = false)
public class QyGbDisCorpUserEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer qyGbDisCorpUserId;
    private Integer qyGbDcuUserId;
    private Integer qyGbDcuDisId;
    private Integer qyGbDcuDepartmentId;
    private String qyGbDcuWxUserId;
    private Integer qyGbDcuCorpId;
}
