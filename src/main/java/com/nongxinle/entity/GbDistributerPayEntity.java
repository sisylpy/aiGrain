package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 批发商支付实体
 */
@Data
@TableName("gb_distributer_pay")
@EqualsAndHashCode(callSuper = false)
public class GbDistributerPayEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer gbDistributerPayId;
    private Integer gbGdpGbDisId;
    private Integer gbGdpGbNewDisId;
    private String gbGdpBuyQuantity;
    private String gbGdpPaySubtotal;
    private Integer gbGdpStatus;
    private String gbGdpPayTime;
    private Date gbGdpFromTime;
    private Date gbGdpStopTime;
    private Integer gbGdpType;
}
