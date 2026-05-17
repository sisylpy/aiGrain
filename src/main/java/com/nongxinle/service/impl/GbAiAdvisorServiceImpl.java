package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nongxinle.ai.advisor.workflow.dto.AiAdvisorDetailDTO;
import com.nongxinle.ai.advisor.workflow.dto.AiAdvisorListItemDTO;
import com.nongxinle.ai.advisor.workflow.dto.AiAdvisorWorkflowItemDTO;
import com.nongxinle.ai.advisor.workflow.dto.AiAdvisorWorkflowRunListItemDTO;
import com.nongxinle.entity.GbAiAdvisorEntity;
import com.nongxinle.mapper.GbAiAdvisorMapper;
import com.nongxinle.mapper.GbAiWorkflowMapper;
import com.nongxinle.mapper.GbAiWorkflowRunMapper;
import com.nongxinle.service.GbAiAdvisorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GbAiAdvisorServiceImpl implements GbAiAdvisorService {

    private final GbAiAdvisorMapper advisorMapper;
    private final GbAiWorkflowMapper workflowMapper;
    private final GbAiWorkflowRunMapper workflowRunMapper;

    @Override
    public List<AiAdvisorListItemDTO> listAdvisors() {
        LambdaQueryWrapper<GbAiAdvisorEntity> q = Wrappers.lambdaQuery();
        q.eq(GbAiAdvisorEntity::getGbAiAdvisorEnabled, 1)
                .orderByAsc(GbAiAdvisorEntity::getGbAiAdvisorSortOrder)
                .orderByAsc(GbAiAdvisorEntity::getGbAiAdvisorId);
        return advisorMapper.selectList(q).stream().map(this::toListItem).collect(Collectors.toList());
    }

    @Override
    public AiAdvisorDetailDTO getAdvisor(Long advisorId) {
        GbAiAdvisorEntity row = requireEnabledAdvisor(advisorId);
        return toDetail(row);
    }

    @Override
    public List<AiAdvisorWorkflowItemDTO> listAdvisorWorkflows(Long advisorId) {
        requireEnabledAdvisor(advisorId);
        return workflowMapper.selectAdvisorWorkflowItems(advisorId);
    }

    @Override
    public List<AiAdvisorWorkflowRunListItemDTO> listRecentWorkflowRuns(Long advisorId, Long userId, int limit) {
        requireEnabledAdvisor(advisorId);
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        int lim = limit <= 0 ? 10 : Math.min(limit, 50);
        return workflowRunMapper.selectRecentByAdvisorAndUser(advisorId, userId, lim);
    }

    private GbAiAdvisorEntity requireEnabledAdvisor(Long advisorId) {
        if (advisorId == null) {
            throw new IllegalArgumentException("advisorId required");
        }
        GbAiAdvisorEntity row = advisorMapper.selectById(advisorId);
        if (row == null || row.getGbAiAdvisorEnabled() == null || row.getGbAiAdvisorEnabled() != 1) {
            throw new IllegalArgumentException("advisor not found or disabled: " + advisorId);
        }
        return row;
    }

    private AiAdvisorListItemDTO toListItem(GbAiAdvisorEntity e) {
        AiAdvisorListItemDTO d = new AiAdvisorListItemDTO();
        d.setAdvisorId(e.getGbAiAdvisorId());
        d.setCode(e.getGbAiAdvisorCode());
        d.setName(e.getGbAiAdvisorName());
        d.setSubtitle(e.getGbAiAdvisorSubtitle());
        d.setSortOrder(e.getGbAiAdvisorSortOrder());
        return d;
    }

    private AiAdvisorDetailDTO toDetail(GbAiAdvisorEntity e) {
        AiAdvisorDetailDTO d = new AiAdvisorDetailDTO();
        d.setAdvisorId(e.getGbAiAdvisorId());
        d.setCode(e.getGbAiAdvisorCode());
        d.setName(e.getGbAiAdvisorName());
        d.setSubtitle(e.getGbAiAdvisorSubtitle());
        d.setDescription(e.getGbAiAdvisorDescription());
        d.setAvatarUrl(e.getGbAiAdvisorAvatarUrl());
        d.setSortOrder(e.getGbAiAdvisorSortOrder());
        d.setDistributerId(e.getGbAiAdvisorDistributerId());
        d.setDepartmentId(e.getGbAiAdvisorDepartmentId());
        return d;
    }
}
