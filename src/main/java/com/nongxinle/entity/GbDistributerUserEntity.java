package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 批发商用户实体
 */
@Data
@TableName("gb_distributer_user")
@EqualsAndHashCode(callSuper = false)
public class GbDistributerUserEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 批发商用户id
     */
    @TableId(type = IdType.AUTO)
    private Integer gbDistributerUserId;

    /**
     * 微信头像
     */
    private String gbDiuWxAvartraUrl;

    /**
     * 微信昵称
     */
    private String gbDiuWxNickName;

    /**
     * 微信openid
     */
    private String gbDiuWxOpenId;

    /**
     * 微信手机号码
     */
    private String gbDiuWxPhone;

    /**
     * 批发商id
     */
    private Integer gbDiuDistributerId;

    /**
     * 是否管理员
     */
    private Integer gbDiuAdmin;

    /**
     * 打印设备id
     */
    private String gbDiuPrintDeviceId;

    /**
     * 头像是否变更
     */
    private Integer gbDiuUrlChange;

    /**
     * 小票打印设备id
     */
    private String gbDiuPrintBillDeviceId;

    /**
     * 企业微信用户id
     */
    private Integer gbDiuQyCorpUserId;

    /**
     * 登录次数
     */
    private Integer gbDiuLoginTimes;
}
