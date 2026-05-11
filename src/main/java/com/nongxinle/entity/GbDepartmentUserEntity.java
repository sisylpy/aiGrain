package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 订货部门用户实体
 */
@Data
@TableName("gb_department_user")
@EqualsAndHashCode(callSuper = false)
public class GbDepartmentUserEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 订货部门用户id
     */
    @TableId(type = IdType.AUTO)
    private Integer gbDepartmentUserId;

    /**
     * 订货部门id
     */
    private Integer gbDuDepartmentId;

    /**
     * 微信头像
     */
    private String gbDuWxAvartraUrl;

    /**
     * 微信昵称
     */
    private String gbDuWxNickName;

    /**
     * 微信openid
     */
    private String gbDuWxOpenId;

    /**
     * 微信手机号码
     */
    private String gbDuWxPhone;

    /**
     * 用户脚色
     */
    private Integer gbDuAdmin;

    /**
     * 批发商id
     */
    private Integer gbDuDistributerId;

    /**
     * 头像是否变更
     */
    private Integer gbDuUrlChange;

    /**
     * 部门父级id
     */
    private Integer gbDuDepartmentFatherId;

    /**
     * 加入日期
     */
    private String gbDuJoinDate;

    /**
     * 打印设备id
     */
    private String gbDuPrintDeviceId;

    /**
     * 小票打印设备id
     */
    private String gbDuPrintBillDeviceId;

    /**
     * 客服id
     */
    private Integer gbDuCustomerService;

    /**
     * 登录次数
     */
    private Integer gbDuLoginTimes;

    /** 扩展统计（如采购总额 billTotal、退货总额 returnPayTotal），非表字段 */
    @TableField(exist = false)
    private Map<String, Object> itemData;

    /**
     * 按天的采购明细（小程序 zicaiArr）：每项含 day、zicai（自采额）、dinghuo（订货额）
     */
    @TableField(exist = false)
    private List<Map<String, Object>> zicaiArr;

}
