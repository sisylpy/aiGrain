package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 批发商实体
 */
@Data
@TableName("gb_distributer")
@EqualsAndHashCode(callSuper = false)
public class GbDistributerEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 批发商id
     */
    @TableId(type = IdType.AUTO)
    private Integer gbDistributerId;

    /**
     * 批发商名称
     */
    private String gbDistributerName;

    /**
     * 批发商位置经度
     */
    private String gbDistributerLan;

    /**
     * 批发商位置纬度
     */
    private String gbDistributerLun;

    /**
     * 批发商商业类型
     */
    private Integer gbDistributerBusinessType;

    /**
     * 批发商负责人
     */
    private String gbDistributerManager;

    /**
     * 批发商电话
     */
    private String gbDistributerPhone;

    /**
     * 批发商地址
     */
    private String gbDistributerAddress;

    /**
     * 批发商图片
     */
    private String gbDistributerImg;

    /**
     * 结算日期
     */
    private String gbDistributerSettleDate;

    /**
     * 结算周
     */
    private String gbDistributerSettleWeek;

    /**
     * 结算月
     */
    private String gbDistributerSettleMonth;

    /**
     * 结算年
     */
    private String gbDistributerSettleYear;

    /**
     * 结算完整时间
     */
    private String gbDistributerSettleFullTime;

    /**
     * 结算次数
     */
    private String gbDistributerSettleTimes;

    /**
     * 时段
     */
    private Integer gbDistributerTimeQuantum;

    /**
     * 购买数量
     */
    private String gbDistributerBuyQuantity;

    /**
     * 城市ID
     */
    private Integer gbDistributerSysCityId;

    /**
     * 记录秒数
     */
    private String gbDistributerRecordSeconds;

    /**
     * 简称
     */
    private String gbDistributerPickName;

    /**
     * 打印名称
     */
    private String gbDistributerPrintName;

    /**
     * 配送商ID
     */
    private Integer gbDistributerNxDisId;

    /**
     * 库存周期
     */
    private Integer gbDistributerStockCycle;

    // 关联对象 - 非数据库列
    @TableField(exist = false)
    private GbDistributerUserEntity gbDistributerUserEntity;

    @TableField(exist = false)
    private SysUserEntity sysUserEntity;

    @TableField(exist = false)
    private GbDistributerModuleEntity gbDistributerModuleEntity;

    @TableField(exist = false)
    private SysBusinessTypeEntity sysBusinessTypeEntity;

    @TableField(exist = false)
    private GbDepartmentUserEntity singleDepartmentUser;

    @TableField(exist = false)
    private GbDistributerFatherGoodsEntity linshiFather;

    @TableField(exist = false)
    private NxJrdhUserEntity nxDisBuyerUser;

    @TableField(exist = false)
    private NxJrdhUserEntity sellerUser;


    // 部门列表
    @TableField(exist = false)
    private List<GbDepartmentEntity> mendianDepartmentList;

    @TableField(exist = false)
    private List<GbDepartmentEntity> purDepartmentList;

    @TableField(exist = false)
    private List<GbDepartmentEntity> stockDepartmentList;

    @TableField(exist = false)
    private List<GbDepartmentEntity> kitchenDepartmentList;

    @TableField(exist = false)
    private List<GbDepartmentEntity> franchiseeDepartmentList;

    @TableField(exist = false)
    private GbDepartmentEntity appSupplierDepartment;
}
