package com.nongxinle.ai.workrecord;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.ai.workrecord.dto.WorkRecordCategoryDTO;
import com.nongxinle.entity.GbWorkRecordCategoryEntity;
import com.nongxinle.mapper.GbWorkRecordCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkRecordCategoryService {

    private final GbWorkRecordCategoryMapper categoryMapper;

    public List<WorkRecordCategoryDTO> listActiveCategories(Long distributerId) {
        Long dis = distributerId != null ? distributerId : 0L;
        List<GbWorkRecordCategoryEntity> rows =
                categoryMapper.selectList(
                        new LambdaQueryWrapper<GbWorkRecordCategoryEntity>()
                                .eq(GbWorkRecordCategoryEntity::getGbWrcStatus, WorkRecordConstants.CATEGORY_ACTIVE)
                                .and(
                                        w ->
                                                w.eq(GbWorkRecordCategoryEntity::getGbWrcDistributerId, 0L)
                                                        .or()
                                                        .eq(GbWorkRecordCategoryEntity::getGbWrcDistributerId, dis))
                                .orderByAsc(GbWorkRecordCategoryEntity::getGbWrcSortOrder));
        return rows.stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<GbWorkRecordCategoryEntity> listActiveCategoryEntities(Long distributerId) {
        Long dis = distributerId != null ? distributerId : 0L;
        return categoryMapper.selectList(
                new LambdaQueryWrapper<GbWorkRecordCategoryEntity>()
                        .eq(GbWorkRecordCategoryEntity::getGbWrcStatus, WorkRecordConstants.CATEGORY_ACTIVE)
                        .and(
                                w ->
                                        w.eq(GbWorkRecordCategoryEntity::getGbWrcDistributerId, 0L)
                                                .or()
                                                .eq(GbWorkRecordCategoryEntity::getGbWrcDistributerId, dis))
                        .orderByAsc(GbWorkRecordCategoryEntity::getGbWrcSortOrder));
    }

    public Map<Long, GbWorkRecordCategoryEntity> activeCategoryMap(Long distributerId) {
        Map<Long, GbWorkRecordCategoryEntity> map = new LinkedHashMap<>();
        for (GbWorkRecordCategoryEntity c : listActiveCategoryEntities(distributerId)) {
            map.put(c.getGbWrcId(), c);
        }
        return map;
    }

    public GbWorkRecordCategoryEntity requireOtherCategory(Long distributerId) {
        Long dis = distributerId != null ? distributerId : 0L;
        GbWorkRecordCategoryEntity row =
                categoryMapper.selectOne(
                        new LambdaQueryWrapper<GbWorkRecordCategoryEntity>()
                                .eq(GbWorkRecordCategoryEntity::getGbWrcCode, WorkRecordConstants.CATEGORY_CODE_OTHER)
                                .eq(GbWorkRecordCategoryEntity::getGbWrcStatus, WorkRecordConstants.CATEGORY_ACTIVE)
                                .and(
                                        w ->
                                                w.eq(GbWorkRecordCategoryEntity::getGbWrcDistributerId, 0L)
                                                        .or()
                                                        .eq(GbWorkRecordCategoryEntity::getGbWrcDistributerId, dis))
                                .last("LIMIT 1"));
        if (row == null) {
            throw new IllegalStateException("OTHER category not seeded; run sql/gb_work_record_mvp.sql");
        }
        return row;
    }

    private WorkRecordCategoryDTO toDto(GbWorkRecordCategoryEntity e) {
        return WorkRecordCategoryDTO.builder()
                .categoryId(e.getGbWrcId())
                .categoryCode(e.getGbWrcCode())
                .categoryName(e.getGbWrcName())
                .description(e.getGbWrcDescription())
                .sortOrder(e.getGbWrcSortOrder())
                .build();
    }
}
