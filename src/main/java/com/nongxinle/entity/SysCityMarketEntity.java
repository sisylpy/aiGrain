package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 城市市场实体
 */
@Data
@TableName("sys_city_market")
@EqualsAndHashCode(callSuper = false)
public class SysCityMarketEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer sysCityMarketId;
    private String sysCityMarketName;
    private Integer sysCityMarketCityId;
}
