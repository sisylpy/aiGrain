package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 今日达供应商实体
 */
@Data
@TableName("nx_jrdh_supplier")
@EqualsAndHashCode(callSuper = false)
public class NxJrdhSupplierEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 供货商id
     */
    @TableId(type = IdType.AUTO)
    private Integer nxJrdhSupplierId;

    /**
     * 供货商名称
     */
    private String nxJrdhsSupplierName;

    /**
     * GB批发商id
     */
    private Integer nxJrdhsGbDistributerId;

    /**
     * GB部门id
     */
    private Integer nxJrdhsGbDepartmentId;

    /**
     * 接单元id
     */
    private Integer nxJrdhsUserId;

    /**
     * 配送商id
     */
    private Integer nxJrdhsNxDistributerId;

    /**
     * 社区id
     */
    private Integer nxJrdhsNxCommunityId;

    /**
     * 配送商采购用户id
     */
    private Integer nxJrdhsNxPurUserId;

    /**
     * 扩展数据（用于存储统计信息，非数据库字段）
     */
    @TableField(exist = false)
    private Object itemData;

    /**
     * GB批发商对象（非数据库字段）
     */
    @TableField(exist = false)
    private GbDistributerEntity gbDistributerEntity;

    /**
     * 供货商用户（非数据库字段）
     */
    @TableField(exist = false)
    private NxJrdhUserEntity jrdhUserEntity;
}
