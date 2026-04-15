package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 系统用户实体
 */
@Data
@TableName("sys_user")
@EqualsAndHashCode(callSuper = false)
public class SysUserEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long userId;
    private String username;
    private String password;
    private String salt;
    private String email;
    private String mobile;
    private Integer status;
}
