package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 今日达用户实体
 */
@Data
@TableName("nx_jrdh_user")
@EqualsAndHashCode(callSuper = false)
public class NxJrdhUserEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 订货用户id
     */
    @TableId(type = IdType.AUTO)
    private Integer nxJrdhUserId;

    /**
     * 微信头像
     */
    private String nxJrdhWxAvartraUrl;

    /**
     * 微信昵称
     */
    private String nxJrdhWxNickName;

    /**
     * 微信openid
     */
    private String nxJrdhWxOpenId;

    /**
     * 微信手机号码
     */
    private String nxJrdhWxPhone;

    /**
     * 加入日期
     */
    private String nxJrdhJoinDate;

    /**
     * 批发商id
     */
    private Integer nxJrdhNxDistributerId;

    /**
     * 批发商用户id
     */
    private Integer nxJrdhNxPurchaserUserId;

    /**
     * 社区id
     */
    private Integer nxJrdhNxCommunityId;

    /**
     * 社区采购用户id
     */
    private Integer nxJrdhNxCommPurchaserUserId;

    /**
     * 头像是否变更
     */
    private Integer nxJrdhUrlChange;

    /**
     * 0 seller, 1 nxpurchaser, 2 gbpurchaser
     */
    private Integer nxJrdhAdmin;

    /**
     * GB批发商id
     */
    private Integer nxJrdhGbDistributerId;

    /**
     * GB部门id
     */
    private Integer nxJrdhGbDepartmentId;

    /**
     * GB部门用户id
     */
    private Integer nxJrdhGbDepartmentUserId;

    /**
     * 设备id
     */
    private String nxJrdhDeviceId;

    /**
     * 打印设备id
     */
    private String nxJrdhDevicePrintId;

    /**
     * 授权供应商id
     */
    private Integer nxJrdhAuthSupplierId;
}
