package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 商业类型实体
 */
@Data
@TableName("sys_business_type")
@EqualsAndHashCode(callSuper = false)
public class SysBusinessTypeEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer sysBusinessTypeId;
    private String sysBusinessTypeName;
    private String sysBusinessTypeDescribe;
}
