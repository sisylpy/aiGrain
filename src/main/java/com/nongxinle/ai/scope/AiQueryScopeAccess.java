package com.nongxinle.ai.scope;

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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiQueryScopeAccess {

    private final AiUserOrgContextLoader userOrgContextLoader;
    private final AiScopeResolver scopeResolver;

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
            return copyWithResolved(scope, List.of());
        }
        return narrow(scope, ctx.get(), subtreeCache);
    }

    private AiQueryScope narrow(AiQueryScope scope, AiUserOrgContext user, Map<Integer, List<Integer>> subtreeCache) {
        int userDis = user.getDistributerId() != null ? user.getDistributerId() : 0;
        int scopeDis = scope.getDisIdForPurchaseQueries();
        if (userDis > 0 && scopeDis > 0 && userDis != scopeDis) {
            log.warn("[AI-SCOPE-ACCESS] distributer mismatch userDis={} scopeDis={} depUserId={}",
                    userDis, scopeDis, user.getDepartmentUserId());
            return copyWithResolved(scope, List.of());
        }
        Integer anchorDep = user.getDepartmentId();
        if (anchorDep == null) {
            log.warn("[AI-SCOPE-ACCESS] user has no department id depUserId={}", user.getDepartmentUserId());
            return copyWithResolved(scope, List.of());
        }
        Set<Integer> allowed = new HashSet<>(scopeResolver.collectSubtreeDepartmentIds(anchorDep, subtreeCache));
        List<Integer> before = scope.getResolvedDepartmentIds();
        List<Integer> narrowed = before.stream().filter(allowed::contains).sorted().collect(Collectors.toList());
        if (narrowed.size() != before.size()) {
            log.info("[AI-SCOPE-ACCESS] narrowed departments {} -> {} (depUserId={} anchorDep={})",
                    before.size(), narrowed.size(), user.getDepartmentUserId(), anchorDep);
        }
        return copyWithResolved(scope, List.copyOf(narrowed));
    }

    private static AiQueryScope copyWithResolved(AiQueryScope scope, List<Integer> resolved) {
        return AiQueryScope.builder()
                .mode(scope.getMode())
                .departmentFatherId(scope.getDepartmentFatherId())
                .distributerId(scope.getDistributerId())
                .disIdForPurchaseQueries(scope.getDisIdForPurchaseQueries())
                .resolvedDepartmentIds(resolved)
                .build();
    }
}
