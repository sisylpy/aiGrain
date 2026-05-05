package com.nongxinle.ai.metric;

/**
 * 指标在部门维度上的展开策略（相对「会话已解析范围」与「锚点部门」）。
 * <p>与 YAML {@code scope_strategy.department_scope} 对齐。</p>
 */
public enum DepartmentScopeStrategy {

    /** 仅锚点部门一单节点（需落在会话允许集合内） */
    SELF,

    /** 锚点部门及其子部门（与 gb_department 父子树一致），再与会话范围求交 */
    SELF_OR_CHILDREN,

    /**
     * 整店 / 会话已展开的全部部门节点：直接使用 {@link com.nongxinle.ai.scope.AiQueryScope#getResolvedDepartmentIds()}
     * （在 STORE 下即门店包；GROUP 下即集团 dis 下全部门，且已做用户收窄）
     */
    ALL,

    /**
     * 自定义：一期与 ALL 等同（显式列表由会话解析层负责）；后续可由总部传参扩展
     */
    CUSTOM;

    public static DepartmentScopeStrategy fromYaml(Object raw) {
        if (raw == null) {
            return SELF_OR_CHILDREN;
        }
        String s = raw.toString().trim().toUpperCase().replace('-', '_');
        try {
            return DepartmentScopeStrategy.valueOf(s);
        } catch (IllegalArgumentException e) {
            return SELF_OR_CHILDREN;
        }
    }
}
