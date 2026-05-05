package com.nongxinle.ai.metric;

import com.nongxinle.ai.scope.AiQueryScope;
import com.nongxinle.ai.scope.AiScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将指标 YAML 中的 {@link DepartmentScopeStrategy} 解析为「最终参与 SQL IN 的部门 id 列表」。
 * <p>始终在 {@link AiQueryScope#getResolvedDepartmentIds()}（已含会话 STORE/GROUP 与用户收窄）内求交，避免越权。</p>
 */
@Component
@RequiredArgsConstructor
public class MetricDepartmentScopeResolver {

    private final AiScopeResolver scopeResolver;

    /**
     * @param sessionScope       当前轮会话范围（已 narrow）
     * @param departmentScope    指标声明
     * @param anchorDepartmentId SELF / SELF_OR_CHILDREN 的锚点部门（如登录用户 gb_DU_department_id）；ALL/CUSTOM 可 null
     */
    public List<Integer> resolveDepartmentIdsForMetric(AiQueryScope sessionScope,
                                                        DepartmentScopeStrategy departmentScope,
                                                        Integer anchorDepartmentId) {
        return resolveDepartmentIdsForMetric(sessionScope, departmentScope, anchorDepartmentId, null);
    }

    public List<Integer> resolveDepartmentIdsForMetric(AiQueryScope sessionScope,
                                                        DepartmentScopeStrategy departmentScope,
                                                        Integer anchorDepartmentId,
                                                        Map<Integer, List<Integer>> subtreeCache) {
        List<Integer> allowed = sessionScope.getResolvedDepartmentIds();
        if (allowed == null || allowed.isEmpty()) {
            return List.of();
        }
        Set<Integer> allowedSet = new HashSet<>(allowed);

        switch (departmentScope) {
            case ALL:
            case CUSTOM:
                return allowed.stream().sorted().collect(Collectors.toList());
            case SELF: {
                int anchorSelf = effectiveAnchor(anchorDepartmentId, sessionScope);
                if (anchorSelf <= 0 || !allowedSet.contains(anchorSelf)) {
                    return List.of();
                }
                return List.of(anchorSelf);
            }
            case SELF_OR_CHILDREN:
            default: {
                int anchor = effectiveAnchor(anchorDepartmentId, sessionScope);
                if (anchor <= 0) {
                    return List.of();
                }
                Set<Integer> subtree = new HashSet<>(scopeResolver.collectSubtreeDepartmentIds(anchor, subtreeCache));
                return allowed.stream().filter(subtree::contains).sorted().collect(Collectors.toList());
            }
        }
    }

    /**
     * 未传锚点时：用会话单店父部门；否则用列表首元素（集团场景弱默认，建议调用方传用户部门）
     */
    private static int effectiveAnchor(Integer anchorDepartmentId, AiQueryScope sessionScope) {
        if (anchorDepartmentId != null) {
            return anchorDepartmentId;
        }
        if (sessionScope.getDepartmentFatherId() != null) {
            return sessionScope.getDepartmentFatherId().intValue();
        }
        List<Integer> ids = sessionScope.getResolvedDepartmentIds();
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return 0;
    }
}
