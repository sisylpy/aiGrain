package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.dto.DepartmentTypeCountRow;
import com.nongxinle.entity.GbDepartmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 订货部门Mapper接口
 */
@Mapper
public interface GbDepartmentMapper extends BaseMapper<GbDepartmentEntity> {

    /**
     * 查询子部门
     * @param depFatherId 父部门ID
     * @return 子部门列表
     */
    List<GbDepartmentEntity> querySubDepartments(@Param("depFatherId") Integer depFatherId);

    /**
     * 根据分销商ID查询分组部门（门店）
     * @param map 包含disId和depType的Map
     * @return 部门列表
     */
    List<GbDepartmentEntity> queryGroupDepsByDisId(Map<String, Object> map);

    /**
     * 统计一批部门 id 的类型分布（用于 AI 范围事实抬头）。
     */
    List<DepartmentTypeCountRow> countDepartmentTypesByIds(@Param("ids") List<Integer> ids);

    /**
     * 在给定 id 集合内筛选父级直营/加盟门店（{@code gb_department_is_group_dep = 1}，与「每个店」粒度一致）。
     */
    List<Integer> selectRetailParentStoreDepartmentIdsInList(@Param("ids") List<Integer> ids);

    /**
     * 分销户下「门店」：{@code gb_department_father_id = 0}（领域口径 {@code docs/DOMAIN_ORG_MODEL.md}）。
     */
    List<Integer> selectStoreDepartmentIdsUnderDistributer(@Param("disId") int disId);

    /**
     * 在给定 id 集合内筛选 {@code gb_department_father_id = 0} 的部门（门店根）。
     */
    List<Integer> selectDepartmentIdsFatherIdZeroInList(@Param("ids") List<Integer> ids);

    GbDepartmentEntity queryDepInfo(Map<String, Object> mapDep);
}
