package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * 农鑫商品实体
 */
@TableName("nongxinle.nx_goods")
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class NxGoodsEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer nxGoodsId;
    private String nxGoodsName;
    private String nxGoodsStandardname;
    private String nxGoodsBrand;
    private String nxGoodsPlace;
    private String nxGoodsPinyin;
    private String nxGoodsPy;
    private Integer nxGoodsFatherId;
    private Integer nxGoodsSort;
    /**
     * 三级品名父下四级 SKU（level=3）中的「根」规格标记：同级仅允许一条为 1（与三级连带的主规格）。
     * 仅在「同时新建三级+首条四级」等唯一根场景设为 1；仅追加另一条四级 SKU 时应为 0。
     */
    private Integer nxGoodsIsOldestSon;
    private Integer nxGoodsGrandId;
    private Integer nxGoodsGreatGrandId;
    private String nxGoodsFile;
    @TableField(exist = false)
    private String color;
    private Integer nxGoodsApplyNxDistributerId;
    /** 扩充目录 SKU 来源批发商 ID（库字段 nx_from_gb_distributer_id） */
    private Integer nxFromGbDistributerId;
    /** 农鑫商品业务状态（库字段 nx_goods_status；-1 表示批发商/AI 扩充待定） */
    private Integer nxGoodsStatus;
    private Integer nxGoodsLevel;
    private Integer nxGoodsIsHidden;
    @TableField(exist = false)
    private String nxGoodsSubNames;
    @TableField(exist = false)
    private Integer isDownload;
    private Integer nxGoodsSonsSort;
    @TableField(exist = false)
    private Integer nxDepartmentGoodsId;
    @TableField(exist = false)
    private String nxDepartmentGoodsPrice;
    private String nxGoodsDetail;
    private String nxGoodsFileBig;
    private Integer nxGoodsStandardAmount;
    private String nxGoodsStandardWeight;
    @TableField(exist = false)
    private Integer subAmount;
    private Integer nxGoodsQuantityDays;
    private String nxGoodsCartonUnit;
    private String nxGoodsItemsPerCarton;

    // 非数据库字段
    @TableField(exist = false)
    private List<NxGoodsEntity> nxGoodsEntityList;
    @TableField(exist = false)
    private List<NxGoodsEntity> nxGoodsFatherEntityList;
    @TableField(exist = false)
    private List<NxGoodsEntity> nxGoodsGrandEntityList;
    @TableField(exist = false)
    private NxGoodsEntity fatherGoods;
    @TableField(exist = false)
    private NxGoodsEntity grandGoods;
    @TableField(exist = false)
    private NxGoodsEntity greatGrandGoods;
    @TableField(exist = false)
    private Boolean isShow = false;
    @TableField(exist = false)
    private int gbDepOrderCount = 0;

    // ========== 老项目迁移: 关联对象 ==========
    /** 批发商商品实体 */
    @TableField(exist = false)
    private GbDistributerGoodsEntity gbDistributerGoodsEntity;

    /** 部门批发商商品实体 */
    @TableField(exist = false)
    private GbDepartmentDisGoodsEntity gbDepartmentDisGoodsEntity;

    /** 部门订单实体 */
    @TableField(exist = false)
    private GbDepartmentOrdersEntity gbDepartmentOrdersEntity;

    /** 商品规格列表 */
    @TableField(exist = false)
    private List<NxStandardEntity> nxGoodsStandardEntities;

    /** 商品别名列表 */
    @TableField(exist = false)
    private List<NxAliasEntity> nxAliasEntities;

    // 别名查询字段
    @TableField(exist = false)
    private Integer subNxGoodsId;
    @TableField(exist = false)
    private String subNxGoodsName;
    @TableField(exist = false)
    private String subNxGoodsDetail;
    @TableField(exist = false)
    private String subNxGoodsFile;
    @TableField(exist = false)
    private Integer subNxGoodsFatherId;
    @TableField(exist = false)
    private Integer subNxGoodsSort;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NxGoodsEntity that = (NxGoodsEntity) o;
        return Objects.equals(nxGoodsId, that.nxGoodsId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nxGoodsId);
    }
}
