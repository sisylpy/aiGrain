package com.nongxinle.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nongxinle.ai.advisor.workflow.AiWorkflowRunHarnessBridge;
import com.nongxinle.ai.advisor.workflow.WorkflowRunStatus;
import com.nongxinle.ai.advisor.workflow.dto.AiWorkflowListItemDTO;
import com.nongxinle.ai.advisor.workflow.dto.WorkflowHarnessDispatchResult;
import com.nongxinle.ai.advisor.workflow.dto.WorkflowRunCreateRequest;
import com.nongxinle.ai.advisor.workflow.dto.WorkflowRunCreateResponseDTO;
import com.nongxinle.entity.GbAiAdvisorEntity;
import com.nongxinle.entity.GbAiWorkflowEntity;
import com.nongxinle.entity.GbAiWorkflowRunEntity;
import com.nongxinle.mapper.GbAiAdvisorMapper;
import com.nongxinle.mapper.GbAiWorkflowMapper;
import com.nongxinle.mapper.GbAiWorkflowRunMapper;
import com.nongxinle.service.GbAiWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GbAiWorkflowServiceImpl implements GbAiWorkflowService {

    private static final int WORKFLOW_RUN_ERROR_MESSAGE_MAX = 2000;

    private final GbAiWorkflowMapper workflowMapper;
    private final GbAiWorkflowRunMapper workflowRunMapper;
    private final GbAiAdvisorMapper advisorMapper;
    private final AiWorkflowRunHarnessBridge workflowRunHarnessBridge;

    @Override
    public List<AiWorkflowListItemDTO> listWorkflows() {
        LambdaQueryWrapper<GbAiWorkflowEntity> q = Wrappers.lambdaQuery();
        q.eq(GbAiWorkflowEntity::getGbAiWorkflowEnabled, 1)
                .orderByAsc(GbAiWorkflowEntity::getGbAiWorkflowSortOrder)
                .orderByAsc(GbAiWorkflowEntity::getGbAiWorkflowId);
        return workflowMapper.selectList(q).stream().map(this::toListItem).collect(Collectors.toList());
    }

    @Override
    public WorkflowRunCreateResponseDTO startWorkflowRun(Long workflowId, WorkflowRunCreateRequest body) {
        if (workflowId == null) {
            throw new IllegalArgumentException("workflowId required");
        }
        if (body == null || body.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        GbAiWorkflowEntity wf = workflowMapper.selectById(workflowId);
        if (wf == null || wf.getGbAiWorkflowEnabled() == null || wf.getGbAiWorkflowEnabled() != 1) {
            throw new IllegalArgumentException("workflow not found or disabled: " + workflowId);
        }
        if (body.getAdvisorId() != null) {
            GbAiAdvisorEntity advisor = advisorMapper.selectById(body.getAdvisorId());
            if (advisor == null || advisor.getGbAiAdvisorEnabled() == null || advisor.getGbAiAdvisorEnabled() != 1) {
                throw new IllegalArgumentException("advisor not found or disabled: " + body.getAdvisorId());
            }
        }

        String inputJson = null;
        if (body.getInputParams() != null && !body.getInputParams().isEmpty()) {
            inputJson = JSON.toJSONString(body.getInputParams());
        }

        Date now = new Date();
        GbAiWorkflowRunEntity run = new GbAiWorkflowRunEntity();
        run.setGbAiWrWorkflowId(workflowId);
        run.setGbAiWrAdvisorId(body.getAdvisorId());
        run.setGbAiWrUserId(body.getUserId());
        run.setGbAiWrConversationId(body.getConversationId());
        run.setGbAiWrMessageId(body.getMessageId());
        run.setGbAiWrStatus(WorkflowRunStatus.PENDING.code());
        run.setGbAiWrInputParamsJson(inputJson);
        run.setGbAiWrDistributerId(body.getDistributerId());
        run.setGbAiWrDepartmentId(body.getDepartmentId());
        run.setGbAiWrCreateTime(now);
        run.setGbAiWrUpdateTime(now);

        workflowRunMapper.insert(run);

        WorkflowRunCreateResponseDTO out = new WorkflowRunCreateResponseDTO();
        out.setWorkflowRunId(run.getGbAiWrId());
        out.setStatus(run.getGbAiWrStatus());

        if (workflowRunHarnessBridge.supportsHarnessDispatch(wf.getGbAiWorkflowCode())) {
            Date dispatchNow = new Date();
            try {
                WorkflowHarnessDispatchResult dispatched = workflowRunHarnessBridge.dispatch(body, wf.getGbAiWorkflowCode());
                applyHarnessDispatchSuccess(run.getGbAiWrId(), dispatched, dispatchNow);
                out.setStatus(WorkflowRunStatus.RUNNING.code());
                out.setRunId(dispatched.runId());
                out.setConversationId(dispatched.conversationId());
            } catch (RuntimeException ex) {
                applyHarnessDispatchFailure(run.getGbAiWrId(), ex, dispatchNow);
                out.setStatus(WorkflowRunStatus.FAILED.code());
            }
        }

        return out;
    }

    private void applyHarnessDispatchSuccess(long workflowRunId, WorkflowHarnessDispatchResult dispatched, Date startedAt) {
        GbAiWorkflowRunEntity patch = new GbAiWorkflowRunEntity();
        patch.setGbAiWrId(workflowRunId);
        patch.setGbAiWrRunId(dispatched.runId());
        patch.setGbAiWrConversationId(dispatched.conversationId());
        patch.setGbAiWrStatus(WorkflowRunStatus.RUNNING.code());
        patch.setGbAiWrStartedAt(startedAt);
        patch.setGbAiWrUpdateTime(new Date());
        workflowRunMapper.updateById(patch);
    }

    private void applyHarnessDispatchFailure(long workflowRunId, RuntimeException ex, Date finishedAt) {
        String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage().trim();
        if (msg.length() > WORKFLOW_RUN_ERROR_MESSAGE_MAX) {
            msg = msg.substring(0, WORKFLOW_RUN_ERROR_MESSAGE_MAX);
        }
        GbAiWorkflowRunEntity patch = new GbAiWorkflowRunEntity();
        patch.setGbAiWrId(workflowRunId);
        patch.setGbAiWrStatus(WorkflowRunStatus.FAILED.code());
        patch.setGbAiWrErrorMessage(msg);
        patch.setGbAiWrFinishedAt(finishedAt);
        patch.setGbAiWrUpdateTime(new Date());
        workflowRunMapper.updateById(patch);
    }

    private AiWorkflowListItemDTO toListItem(GbAiWorkflowEntity e) {
        AiWorkflowListItemDTO d = new AiWorkflowListItemDTO();
        d.setWorkflowId(e.getGbAiWorkflowId());
        d.setCode(e.getGbAiWorkflowCode());
        d.setName(e.getGbAiWorkflowName());
        d.setDescription(e.getGbAiWorkflowDescription());
        d.setCategory(e.getGbAiWorkflowCategory());
        d.setSortOrder(e.getGbAiWorkflowSortOrder());
        d.setIntentCode(e.getGbAiWorkflowIntentCode());
        d.setPathCode(e.getGbAiWorkflowPathCode());
        d.setHarnessEntryType(e.getGbAiWorkflowHarnessEntryType());
        d.setHarnessPathKey(e.getGbAiWorkflowHarnessPathKey());
        return d;
    }
}
