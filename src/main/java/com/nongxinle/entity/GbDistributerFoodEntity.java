package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;


@Data
@TableName("gb_distributer_food")
@EqualsAndHashCode(callSuper = false)
public class GbDistributerFoodEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer gbDistributerFoodId;
    private Integer gbDfDistributerId;
    private Integer gbDfNxFoodId;
    private String gbDfFoodName;
    private String gbDfFoodPrice;
    private Integer gbDfStatus;
    private String gbDfFoodPinyin;
    private String gbDfFoodPy;
    /**
     * 菜品父级：指向同表 {@code gb_distributer_food} 中「分类/父菜」行的 id；叶子菜品挂在父节点下时由 {@link #foodEntityList} 组装树。
     */
    private Integer gbDfFoodFatherId;
    private String gbDfFoodImg;
    private String gbDfFoodImgLarge;
    private String gbDfFoodMethod;
    private String gbDfFoodDetail;
    private Integer gbDfGoodsSort;

    @TableField(exist = false)
    private List<GbDistributerFoodGoodsEntity> gbdisFoodGoodsEntities;
    /**
     * 非表字段：当前父级/分类节点下的子菜品列表（接口拼装树时用；与部门菜列表无必然同步）。
     */
    @TableField(exist = false)
    private List<GbDistributerFoodEntity> foodEntityList;
    /**
     * 反向挂部门菜；JSON 不输出，避免与 {@link GbDepFoodEntity#getGbDistributerFoodEntity()} 形成 Fastjson 循环引用导致整段批发商菜品被省略。
     */
    @TableField(exist = false)
    @JSONField(serialize = false)
    private GbDepFoodEntity gbDepFoodEntity;
    @TableField(exist = false)
    private GbDistributerFoodGoodsEntity rawFoodGoods;
}
