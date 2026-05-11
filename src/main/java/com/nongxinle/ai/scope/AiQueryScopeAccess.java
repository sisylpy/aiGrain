package com.nongxinle.ai.scope;

import com.nongxinle.mapper.GbDepartmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将解析后的 {@link AiQueryScope} 与登录用户可见部门求交，避免跨批发商/跨无权限节点取数。
 * <p>
 * 用户可视范围在每次请求内根据 {@code gb_department_user} 与部门树计算，不写入会话表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiQueryScopeAccess {

    private final AiUserOrgContextLoader userOrgContextLoader;
    private final AiScopeResolver scopeResolver;
    private final GbDepartmentMapper departmentMapper;
    private final AiDepartmentUserExpansionResolver departmentUserExpansionResolver;

    /**
     * @param departmentUserId {@code gb_department_user_id}，与对话接口 userId 一致；null 表示不做收窄（兼容旧调用）
     */
    public AiQueryScope narrowForDepartmentUser(AiQueryScope scope, Long departmentUserId) {
        return narrowForDepartmentUser(scope, departmentUserId, null);
    }

    /**
     * @param subtreeCache 与 {@link AiScopeResolver#resolve(com.nongxinle.entity.GbAiConversationEntity, Map)} 共用时可减少重复子树查询
     */
    public AiQueryScope narrowForDepartmentUser(AiQueryScope scope, Long departmentUserId,
                                                Map<Integer, List<Integer>> subtreeCache) {
        if (departmentUserId == null) {
            return scope;
        }
        Optional<AiUserOrgContext> ctx = userOrgContextLoader.load(departmentUserId);
        if (ctx.isEmpty()) {
            log.warn("[AI-SCOPE-ACCESS] department user not found, emptying resolved departments userPk={}", departmentUserId);
            return copyWithResolved(scope, List.of(), null);
        }
        return narrow(scope, ctx.get(), subtreeCache);
    }

    private AiQueryScope narrow(AiQueryScope scope, AiUserOrgContext user, Map<Integer, List<Integer>> subtreeCache) {
        int userDis = user.getDistributerId() != null ? user.getDistributerId() : 0;
        int scopeDis = scope.getDisIdForPurchaseQueries();
        if (userDis > 0 && scopeDis > 0 && userDis != scopeDis) {
            log.warn("[AI-SCOPE-ACCESS] distributer mismatch userDis={} scopeDis={} depUserId={}",
                    userDis, scopeDis, user.getDepartmentUserId());
            return copyWithResolved(scope, List.of(), null);
        }
        // 集团模式（GROUP）：不做部门收窄，集团管理员应能看到该 dis 下全部门店
        if (scope.getMode() == AiConversationScopeMode.GROUP) {
            log.info("[AI-SCOPE-ACCESS] GROUP mode skip narrowing depUserId={} userDis={} scopeDis={}",
                    user.getDepartmentUserId(), userDis, scopeDis);
            return copyWithResolved(scope, scope.getResolvedDepartmentIds(), user.getDepartmentId().longValue());
        }
        Integer anchorDep = user.getDepartmentId();
        if (anchorDep == null) {
            log.warn("[AI-SCOPE-ACCESS] user has no department id depUserId={}", user.getDepartmentUserId());
            return copyWithResolved(scope, List.of(), null);
        }
        AiDepartmentUserExpansionResolver.ResolvedExpansion exp = departmentUserExpansionResolver.resolve(user);
        Integer expansionRoot = exp.expansionRootDepartmentId();
        if (expansionRoot == null) {
            log.warn("[AI-SCOPE-ACCESS] expansion root null depUserId={} anchorDep={}", user.getDepartmentUserId(), anchorDep);
            return copyWithResolved(scope, List.of(), null);
        }
        Set<Integer> allowed = new HashSet<>(scopeResolver.collectSubtreeDepartmentIds(expansionRoot, subtreeCache));
        List<Integer> before = scope.getResolvedDepartmentIds();
        List<Integer> narrowed = before.stream().filter(allowed::contains).sorted().collect(Collectors.toList());
        if (narrowed.size() != before.size()) {
            log.info("[AI-SCOPE-ACCESS] narrowed departments {} -> {} (depUserId={} anchorDep={} expansionKind={} expansionRoot={} allowedIds={})",
                    before.size(), narrowed.size(), user.getDepartmentUserId(), anchorDep, exp.kind(), expansionRoot, allowed);
        } else {
            log.info("[AI-SCOPE-ACCESS] user visible subtree depUserId={} anchorDep={} expansionKind={} expansionRoot={} allowedCount={} allowedIds={}",
                    user.getDepartmentUserId(), anchorDep, exp.kind(), expansionRoot, allowed.size(), allowed);
        }
        return copyWithResolved(scope, List.copyOf(narrowed), anchorDep.longValue());
    }

    private AiQueryScope copyWithResolved(AiQueryScope scope, List<Integer> resolved, Long userMemoryAnchor) {
        Map<Integer, Integer> counts = resolved.isEmpty()
                ? Map.of()
                : AiQueryScope.toTypeCountMap(departmentMapper.countDepartmentTypesByIds(resolved));
        return AiQueryScope.builder()
                .mode(scope.getMode())
                .departmentFatherId(scope.getDepartmentFatherId())
                .distributerId(scope.getDistributerId())
                .disIdForPurchaseQueries(scope.getDisIdForPurchaseQueries())
                .resolvedDepartmentIds(resolved)
                .departmentTypeCounts(counts)
                .parentStoreCount(scope.getParentStoreCount())
                .userMemoryAnchorDepartmentId(userMemoryAnchor)
                .groupRevenueUseDistributerWideQuery(scope.isGroupRevenueUseDistributerWideQuery())
                .build();
    }
}
