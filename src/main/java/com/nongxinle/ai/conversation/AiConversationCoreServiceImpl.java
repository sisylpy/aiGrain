package com.nongxinle.ai.conversation;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.ai.advisor.AiAdvisorConversationConstants;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbAiMessageEntity;
import com.nongxinle.mapper.GbAiConversationMapper;
import com.nongxinle.mapper.GbAiMessageMapper;
import com.nongxinle.mapper.GbDepartmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiConversationCoreServiceImpl implements AiConversationCoreService {

    private final GbAiConversationMapper conversationMapper;
    private final GbAiMessageMapper messageMapper;
    private final GbDepartmentMapper departmentMapper;

    @Override
    public GbAiConversationEntity createNewConversationForAgentRun(
            Long departmentId,
            Long distributerId,
            AiConversationScopeMode scopeMode,
            Long userId) {
        AiConversationScopeMode mode = scopeMode != null ? scopeMode : AiConversationScopeMode.STORE;
        log.info("[AgentRun] createNewConversation mode={} departmentId={} distributerId={} userId={}",
                mode, departmentId, distributerId, userId);

        Long effDistributerId = distributerId;
        if (mode == AiConversationScopeMode.GROUP) {
            if (effDistributerId == null) {
                throw new IllegalArgumentException("集团模式必须提供 distributerId(disId)");
            }
        } else {
            if (departmentId == null) {
                throw new IllegalArgumentException("单店模式必须提供 departmentId(门店父部门)");
            }
            if (effDistributerId == null) {
                GbDepartmentEntity d = departmentMapper.selectById(departmentId.intValue());
                if (d != null && d.getGbDepartmentDisId() != null) {
                    effDistributerId = d.getGbDepartmentDisId().longValue();
                }
            }
        }

        GbAiConversationEntity conv = new GbAiConversationEntity();
        conv.setGbAiConversationScopeMode(mode.getCode());
        conv.setGbAiConversationDepartmentId(mode == AiConversationScopeMode.STORE ? departmentId : null);
        conv.setGbAiConversationDistributerId(effDistributerId);
        conv.setGbAiConversationUserId(userId);
        conv.setGbAiConversationStatus(0);
        conv.setGbAiConversationTitle("新对话");
        conv.setGbAiConversationCreateTime(new Date());
        conv.setGbAiConversationUpdateTime(new Date());

        conversationMapper.insert(conv);
        log.info("[AgentRun] 创建新对话成功 - conversationId={} mode={}", conv.getGbAiConversationId(), mode);
        return conv;
    }

    @Override
    public GbAiConversationEntity requireConversationOwnedByUser(Long conversationId, Long userId) {
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        GbAiConversationEntity conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new IllegalArgumentException("conversation not found: " + conversationId);
        }
        if (!Objects.equals(conv.getGbAiConversationUserId(), userId)) {
            throw new IllegalArgumentException("conversation does not belong to current user");
        }
        return conv;
    }

    @Override
    public List<GbAiMessageEntity> getConversationMessages(Long conversationId) {
        log.debug("获取对话消息 - conversationId={}", conversationId);
        return messageMapper.selectList(
                new LambdaQueryWrapper<GbAiMessageEntity>()
                        .eq(GbAiMessageEntity::getGbAiMessageConversationId, conversationId)
                        .orderByAsc(GbAiMessageEntity::getGbAiMessageCreateTime));
    }

    @Override
    public GbAiConversationEntity getOrCreateAdvisorConversation(
            Long advisorId,
            String conversationTitle,
            Long departmentId,
            Long distributerId,
            AiConversationScopeMode scopeMode,
            Long userId) {

        if (advisorId == null || userId == null) {
            throw new IllegalArgumentException("advisorId and userId required");
        }
        AiConversationScopeMode mode = scopeMode != null ? scopeMode : AiConversationScopeMode.STORE;

        Long effDistributerId = distributerId;
        if (mode == AiConversationScopeMode.GROUP) {
            if (effDistributerId == null) {
                throw new IllegalArgumentException("集团模式必须提供 distributerId(disId)");
            }
        } else {
            if (departmentId == null) {
                throw new IllegalArgumentException("单店模式必须提供 departmentId(门店父部门)");
            }
            if (effDistributerId == null) {
                GbDepartmentEntity d = departmentMapper.selectById(departmentId.intValue());
                if (d != null && d.getGbDepartmentDisId() != null) {
                    effDistributerId = d.getGbDepartmentDisId().longValue();
                }
            }
        }

        LambdaQueryWrapper<GbAiConversationEntity> w = new LambdaQueryWrapper<>();
        w.eq(GbAiConversationEntity::getGbAiConversationUserId, userId)
                .eq(GbAiConversationEntity::getGbAiConversationAdvisorId, advisorId)
                .eq(GbAiConversationEntity::getGbAiConversationThreadKind, AiAdvisorConversationConstants.THREAD_KIND_ADVISOR)
                .eq(GbAiConversationEntity::getGbAiConversationScopeMode, mode.getCode());
        if (mode == AiConversationScopeMode.STORE) {
            w.eq(GbAiConversationEntity::getGbAiConversationDepartmentId, departmentId)
                    .eq(GbAiConversationEntity::getGbAiConversationDistributerId, effDistributerId);
        } else {
            w.isNull(GbAiConversationEntity::getGbAiConversationDepartmentId)
                    .eq(GbAiConversationEntity::getGbAiConversationDistributerId, effDistributerId);
        }
        w.orderByDesc(GbAiConversationEntity::getGbAiConversationUpdateTime).last("LIMIT 1");

        List<GbAiConversationEntity> found = conversationMapper.selectList(w);
        if (!found.isEmpty()) {
            return found.get(0);
        }

        GbAiConversationEntity conv = new GbAiConversationEntity();
        conv.setGbAiConversationScopeMode(mode.getCode());
        conv.setGbAiConversationDepartmentId(mode == AiConversationScopeMode.STORE ? departmentId : null);
        conv.setGbAiConversationDistributerId(effDistributerId);
        conv.setGbAiConversationUserId(userId);
        conv.setGbAiConversationStatus(0);
        conv.setGbAiConversationTitle(StrUtil.blankToDefault(conversationTitle, "顾问对话"));
        conv.setGbAiConversationCreateTime(new Date());
        conv.setGbAiConversationUpdateTime(new Date());
        conv.setGbAiConversationAdvisorId(advisorId);
        conv.setGbAiConversationThreadKind(AiAdvisorConversationConstants.THREAD_KIND_ADVISOR);

        conversationMapper.insert(conv);
        log.info(
                "[AdvisorConv] inserted conversationId={} advisorId={} userId={} mode={}",
                conv.getGbAiConversationId(),
                advisorId,
                userId,
                mode);
        return conv;
    }
}
