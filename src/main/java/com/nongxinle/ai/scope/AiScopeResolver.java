package com.nongxinle.ai.scope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将 {@link GbAiConversationEntity} 解析为 {@link AiQueryScope}（部门子树或集团全部门）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiScopeResolver {

    private final GbDepartmentMapper departmentMapper;

    public AiQueryScope resolve(GbAiConversationEntity conv) {
        return resolve(conv, null);
    }

    /**
     * @param subtreeCache 可选；同一次请求内传入同一 Map 可复用 {@link #collectSubtreeDepartmentIds(int, Map)} 结果，减少 gb_department 重复查询。
     */
    public AiQueryScope resolve(GbAiConversationEntity conv, Map<Integer, List<Integer>> subtreeCache) {
        if (conv == null) {
            return AiQueryScope.builder()
                    .mode(AiConversationScopeMode.STORE)
                    .departmentFatherId(null)
                    .distributerId(null)
                    .disIdForPurchaseQueries(0)
                    .resolvedDepartmentIds(List.of())
                    .build();
        }
        AiConversationScopeMode mode = AiConversationScopeMode.fromCode(conv.getGbAiConversationScopeMode());
        if (mode == AiConversationScopeMode.GROUP) {
            return resolveGroup(conv);
        }
        return resolveStore(conv, subtreeCache);
    }

    private AiQueryScope resolveStore(GbAiConversationEntity conv, Map<Integer, List<Integer>> subtreeCache) {
        Long father = conv.getGbAiConversationDepartmentId();
        if (father == null) {
            log.warn("[AI-SCOPE] STORE conversation missing departmentId conversationId={}", conv.getGbAiConversationId());
            int dis = conv.getGbAiConversationDistributerId() != null ? conv.getGbAiConversationDistributerId().intValue() : 0;
            return AiQueryScope.builder()
                    .mode(AiConversationScopeMode.STORE)
                    .departmentFatherId(null)
                    .distributerId(conv.getGbAiConversationDistributerId())
                    .disIdForPurchaseQueries(dis)
                    .resolvedDepartmentIds(List.of())
                    .build();
        }
        List<Integer> ids = collectSubtreeDepartmentIds(father.intValue(), subtreeCache);
        int dis = resolveDisIdForStore(conv, father);
        return AiQueryScope.builder()
                .mode(AiConversationScopeMode.STORE)
                .departmentFatherId(father)
                .distributerId(conv.getGbAiConversationDistributerId() != null
                        ? conv.getGbAiConversationDistributerId()
                        : (dis > 0 ? (long) dis : null))
                .disIdForPurchaseQueries(dis)
                .resolvedDepartmentIds(ids)
                .build();
    }

    private int resolveDisIdForStore(GbAiConversationEntity conv, Long father) {
        if (conv.getGbAiConversationDistributerId() != null) {
            return conv.getGbAiConversationDistributerId().intValue();
        }
        GbDepartmentEntity row = departmentMapper.selectById(father.intValue());
        return row != null && row.getGbDepartmentDisId() != null ? row.getGbDepartmentDisId() : 0;
    }

    private AiQueryScope resolveGroup(GbAiConversationEntity conv) {
        Long disL = conv.getGbAiConversationDistributerId();
        if (disL == null) {
            log.warn("[AI-SCOPE] GROUP conversation missing distributerId conversationId={}", conv.getGbAiConversationId());
            return AiQueryScope.builder()
                    .mode(AiConversationScopeMode.GROUP)
                    .departmentFatherId(null)
                    .distributerId(null)
                    .disIdForPurchaseQueries(0)
                    .resolvedDepartmentIds(List.of())
                    .build();
        }
        int dis = disL.intValue();
        List<GbDepartmentEntity> rows = departmentMapper.selectList(
                new LambdaQueryWrapper<GbDepartmentEntity>()
                        .eq(GbDepartmentEntity::getGbDepartmentDisId, dis));
        List<Integer> ids = new ArrayList<>();
        for (GbDepartmentEntity r : rows) {
            if (r.getGbDepartmentId() != null) {
                ids.add(r.getGbDepartmentId());
            }
        }
        log.info("[AI-SCOPE] GROUP disId={} departmentsResolved={} conversationId={}",
                dis, ids.size(), conv.getGbAiConversationId());
        return AiQueryScope.builder()
                .mode(AiConversationScopeMode.GROUP)
                .departmentFatherId(null)
                .distributerId(disL)
                .disIdForPurchaseQueries(dis)
                .resolvedDepartmentIds(ids)
                .build();
    }

    /**
     * BFS 收集子树内全部部门 ID（含根）。
     */
    public List<Integer> collectSubtreeDepartmentIds(int rootDepartmentId) {
        return collectSubtreeDepartmentIdsUncached(rootDepartmentId);
    }

    /**
     * 带请求级缓存的子树收集；{@code cache} 为 null 时与 {@link #collectSubtreeDepartmentIds(int)} 等价。
     */
    public List<Integer> collectSubtreeDepartmentIds(int rootDepartmentId, Map<Integer, List<Integer>> cache) {
        if (cache == null) {
            return collectSubtreeDepartmentIdsUncached(rootDepartmentId);
        }
        return cache.computeIfAbsent(rootDepartmentId, this::collectSubtreeDepartmentIdsUncached);
    }

    private List<Integer> collectSubtreeDepartmentIdsUncached(int rootDepartmentId) {
        List<Integer> out = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        Deque<Integer> q = new ArrayDeque<>();
        q.add(rootDepartmentId);
        visited.add(rootDepartmentId);
        while (!q.isEmpty()) {
            int id = q.poll();
            out.add(id);
            List<GbDepartmentEntity> children = departmentMapper.selectList(
                    new LambdaQueryWrapper<GbDepartmentEntity>()
                            .eq(GbDepartmentEntity::getGbDepartmentFatherId, id));
            for (GbDepartmentEntity c : children) {
                if (c.getGbDepartmentId() == null) {
                    continue;
                }
                int cid = c.getGbDepartmentId();
                if (visited.add(cid)) {
                    q.add(cid);
                }
            }
        }
        return out;
    }
}
