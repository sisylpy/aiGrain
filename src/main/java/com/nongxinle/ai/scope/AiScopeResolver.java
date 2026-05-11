package com.nongxinle.ai.scope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.utils.GbConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 {@link GbAiConversationEntity} 解析为 {@link AiQueryScope}（单店为子树；集团为父级门店锚点及其子树合并，见 {@link #resolveGroup}）。
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
                    .departmentTypeCounts(Map.of())
                    .userMemoryAnchorDepartmentId(null)
                    .build();
        }
        AiConversationScopeMode mode = AiConversationScopeMode.fromCode(conv.getGbAiConversationScopeMode());
        if (mode == AiConversationScopeMode.GROUP) {
            return resolveGroup(conv, subtreeCache);
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
                    .departmentTypeCounts(Map.of())
                    .userMemoryAnchorDepartmentId(null)
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
                .departmentTypeCounts(departmentTypeCountsForIds(ids))
                .parentStoreCount(1)
                .userMemoryAnchorDepartmentId(null)
                .build();
    }

    private int resolveDisIdForStore(GbAiConversationEntity conv, Long father) {
        if (conv.getGbAiConversationDistributerId() != null) {
            return conv.getGbAiConversationDistributerId().intValue();
        }
        GbDepartmentEntity row = departmentMapper.selectById(father.intValue());
        return row != null && row.getGbDepartmentDisId() != null ? row.getGbDepartmentDisId() : 0;
    }

    /**
     * 集团默认统计范围：**直营/加盟父级门店**（{@code gb_department_father_id = 0} 且 {@code gb_department_is_group_dep = 1}）。
     * 门店无需展开子树，直接取门店本身即可。
     * 若无符合条件的父级门店，回退为该 dis 下全量部门列表。
     */
    private AiQueryScope resolveGroup(GbAiConversationEntity conv, Map<Integer, List<Integer>> subtreeCache) {
        Long disL = conv.getGbAiConversationDistributerId();
        if (disL == null) {
            log.warn("[AI-SCOPE] GROUP conversation missing distributerId conversationId={}", conv.getGbAiConversationId());
            return AiQueryScope.builder()
                    .mode(AiConversationScopeMode.GROUP)
                    .departmentFatherId(null)
                    .distributerId(null)
                    .disIdForPurchaseQueries(0)
                    .resolvedDepartmentIds(List.of())
                    .departmentTypeCounts(Map.of())
                    .userMemoryAnchorDepartmentId(null)
                    .build();
        }
        int dis = disL.intValue();
        // 门店识别条件：father_id = 0 且 is_group_dep = 1
        List<GbDepartmentEntity> parentStores = departmentMapper.selectList(
                new LambdaQueryWrapper<GbDepartmentEntity>()
                        .eq(GbDepartmentEntity::getGbDepartmentDisId, dis)
                        .eq(GbDepartmentEntity::getGbDepartmentFatherId, 0)
                        .eq(GbDepartmentEntity::getGbDepartmentIsGroupDep, 1));

        List<Integer> ids;
        if (parentStores == null || parentStores.isEmpty()) {
            log.warn("[AI-SCOPE] GROUP disId={} no parent store anchors (father_id=0 & is_group_dep=1); fallback ALL dis departments",
                    dis);
            List<GbDepartmentEntity> rows = departmentMapper.selectList(
                    new LambdaQueryWrapper<GbDepartmentEntity>()
                            .eq(GbDepartmentEntity::getGbDepartmentDisId, dis));
            ids = new ArrayList<>();
            for (GbDepartmentEntity r : rows) {
                if (r.getGbDepartmentId() != null) {
                    ids.add(r.getGbDepartmentId());
                }
            }
        } else {
            // 门店本身即为自己，无需展开子树
            ids = parentStores.stream()
                    .map(GbDepartmentEntity::getGbDepartmentId)
                    .filter(id -> id != null)
                    .collect(java.util.stream.Collectors.toList());
        }

        log.info("[AI-SCOPE] GROUP disId={} departmentsResolved={} parentStoreAnchors={} parentStoreNames={} conversationId={}",
                dis, ids.size(),
                parentStores == null ? 0 : parentStores.size(),
                parentStores == null ? "[]" : parentStores.stream()
                        .map(GbDepartmentEntity::getGbDepartmentName)
                        .collect(Collectors.joining(",", "[", "]")),
                conv.getGbAiConversationId());
        int pCount = (parentStores != null) ? parentStores.size() : 0;
        return AiQueryScope.builder()
                .mode(AiConversationScopeMode.GROUP)
                .departmentFatherId(null)
                .distributerId(disL)
                .disIdForPurchaseQueries(dis)
                .resolvedDepartmentIds(ids)
                .departmentTypeCounts(departmentTypeCountsForIds(ids))
                .parentStoreCount(pCount)
                .userMemoryAnchorDepartmentId(null)
                .build();
    }

    /**
     * 解析范围内各部门类型计数，供 {@link AiQueryScope#toMarkdownFactHeader()} 使用。
     */
    public Map<Integer, Integer> departmentTypeCountsForIds(List<Integer> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return Map.of();
        }
        return AiQueryScope.toTypeCountMap(departmentMapper.countDepartmentTypesByIds(departmentIds));
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

    /**
     * 给定部门 id 集合内计数「父级门店」：{@code father_id = 0} 且 {@code is_group_dep = 1}
     * （与后台「一家店」口径一致；子树若不含顶层父节点则可能为 0）。
     */
    public int countRetailParentStoresAmongIds(List<Integer> departmentIds) {
        return listRetailStoreAnchorDepartmentIds(departmentIds).size();
    }

    /**
     * 在 {@code resolvedDepartmentIds} 可见集合内，枚举「父级门店锚点」：<br>
     * 仅 {@code gb_department_father_id = 0} 且 {@code gb_department_is_group_dep = 1}（与后台「一家店」粒度一致）。<br>
     * 不做 {@code type ∈ (1,11)} 推测回退；范围内若无此类节点则返回空列表，由调用方提示修正锚点。<br>
     * 去掉「同为候选时的祖先节点」（避免集团根吞掉旗下真实门店）。
     */
    public List<Integer> listRetailStoreAnchorDepartmentIds(List<Integer> resolvedDepartmentIds) {
        List<Integer> resolved = sanitizePositiveIds(resolvedDepartmentIds);
        if (resolved.isEmpty()) {
            log.debug("[AI-SCOPE-RETAIL-ANCHORS] skip: empty resolvedDepartmentIds input");
            return List.of();
        }
        LinkedHashSet<Integer> candidates = new LinkedHashSet<>();
        List<Integer> precise = departmentMapper.selectRetailParentStoreDepartmentIdsInList(resolved);
        if (precise != null) {
            for (Integer p : precise) {
                if (p != null && p > 0) {
                    candidates.add(p);
                }
            }
        }
        if (candidates.isEmpty()) {
            log.info(
                    "[AI-SCOPE-RETAIL-ANCHORS] hitCount=0 (no father_id=0 & is_group_dep=1 in expanded list) "
                            + "inputDeptCount={} inputDeptIdsPreview={}",
                    resolved.size(), idListPreview(resolved, 40));
            return List.of();
        }
        List<Integer> list = new ArrayList<>(candidates);
        Set<Integer> dropAncestors = new HashSet<>();
        for (Integer x : list) {
            for (Integer y : list) {
                if (Objects.equals(x, y)) {
                    continue;
                }
                if (isStrictAncestorDepartment(x, y)) {
                    dropAncestors.add(x);
                    break;
                }
            }
        }
        list.removeIf(dropAncestors::contains);
        list.sort(Integer::compareTo);
        log.info(
                "[AI-SCOPE-RETAIL-ANCHORS] hitCount={} anchorIds={} anchorsDetail={}",
                list.size(), list, formatDepartmentAnchorsForLog(list));
        return list;
    }

    /**
     * 分销户下门店根部门 id（{@code gb_department_dis_id = disId} 且 {@code gb_department_father_id = 0}）。
     *
     * @see docs/DOMAIN_ORG_MODEL.md
     */
    public List<Integer> listStoreDepartmentIdsUnderDistributer(int disId) {
        if (disId <= 0) {
            return List.of();
        }
        List<Integer> ids = departmentMapper.selectStoreDepartmentIdsUnderDistributer(disId);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>(ids.size());
        for (Integer i : ids) {
            if (i != null && i > 0) {
                out.add(i);
            }
        }
        out.sort(Integer::compareTo);
        return out;
    }

    /**
     * 在已展开部门列表内筛选门店根（{@code gb_department_father_id = 0}），用于集团汇总锚点 / 经营看板门店维度。
     */
    public List<Integer> listDomainStoreAnchorsInResolved(List<Integer> resolvedDepartmentIds) {
        List<Integer> resolved = sanitizePositiveIds(resolvedDepartmentIds);
        if (resolved.isEmpty()) {
            return List.of();
        }
        List<Integer> hit = departmentMapper.selectDepartmentIdsFatherIdZeroInList(resolved);
        if (hit == null || hit.isEmpty()) {
            log.info(
                    "[AI-SCOPE-DOMAIN-STORES] resolvedHitCount=0 inputDeptCount={} inputPreview={}",
                    resolved.size(), idListPreview(resolved, 40));
            return List.of();
        }
        List<Integer> list = new ArrayList<>();
        for (Integer i : hit) {
            if (i != null && i > 0) {
                list.add(i);
            }
        }
        list.sort(Integer::compareTo);
        log.info(
                "[AI-SCOPE-DOMAIN-STORES] resolvedHitCount={} domainStoreIds={} detail={}",
                list.size(), list, formatDepartmentAnchorsForLog(list));
        return list;
    }

    /** 日志用：批量拉名称，避免按 id 循环查询。 */
    private String formatDepartmentAnchorsForLog(List<Integer> anchorIds) {
        if (anchorIds == null || anchorIds.isEmpty()) {
            return "";
        }
        List<GbDepartmentEntity> rows = departmentMapper.selectBatchIds(anchorIds);
        Map<Integer, GbDepartmentEntity> byId = rows.stream()
                .filter(r -> r.getGbDepartmentId() != null)
                .collect(Collectors.toMap(GbDepartmentEntity::getGbDepartmentId, r -> r, (a, b) -> a));
        StringBuilder sb = new StringBuilder();
        for (Integer id : anchorIds) {
            GbDepartmentEntity e = byId.get(id);
            if (e != null) {
                sb.append(String.format(
                        "[id=%d name=%s fatherId=%s type=%s isGroupDep=%s disId=%s] ",
                        id,
                        e.getGbDepartmentName(),
                        e.getGbDepartmentFatherId(),
                        e.getGbDepartmentType(),
                        e.getGbDepartmentIsGroupDep(),
                        e.getGbDepartmentDisId()));
            } else {
                sb.append(String.format("[id=%d name=?] ", id));
            }
        }
        return sb.toString().trim();
    }

    private static String idListPreview(List<Integer> ids, int max) {
        if (ids == null || ids.isEmpty()) {
            return "[]";
        }
        if (ids.size() <= max) {
            return ids.toString();
        }
        return ids.subList(0, max) + "...(+" + (ids.size() - max) + " more)";
    }

    private static List<Integer> sanitizePositiveIds(List<Integer> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<Integer, Boolean> uniq = new LinkedHashMap<>();
        for (Integer i : raw) {
            if (i != null && i > 0) {
                uniq.put(i, Boolean.TRUE);
            }
        }
        return new ArrayList<>(uniq.keySet());
    }

    /**
     * {@code ancestorId} 是否在部门父链上严格高于 {@code descendantId}。
     */
    private boolean isStrictAncestorDepartment(int ancestorId, int descendantId) {
        if (ancestorId == descendantId) {
            return false;
        }
        Integer cur = descendantId;
        int guard = 0;
        while (cur != null && cur > 0 && guard++ < 128) {
            if (cur == ancestorId) {
                return true;
            }
            GbDepartmentEntity row = departmentMapper.selectById(cur);
            if (row == null || row.getGbDepartmentFatherId() == null || row.getGbDepartmentFatherId() <= 0) {
                break;
            }
            cur = row.getGbDepartmentFatherId();
        }
        return false;
    }

    /**
     * 将任意部门 id 归一到所属门店根（{@code gb_department_father_id = 0}）。
     * 无父链或查不到行时返回原始 {@code departmentId}。
     */
    public int resolveDomainStoreDepartmentId(int departmentId) {
        if (departmentId <= 0) {
            return departmentId;
        }
        Integer cur = departmentId;
        Set<Integer> guard = new HashSet<>();
        while (cur != null && cur > 0 && guard.add(cur)) {
            GbDepartmentEntity row = departmentMapper.selectById(cur);
            if (row == null) {
                return departmentId;
            }
            Integer father = row.getGbDepartmentFatherId();
            if (father == null || father == 0) {
                return cur;
            }
            cur = father;
        }
        return departmentId;
    }

}
