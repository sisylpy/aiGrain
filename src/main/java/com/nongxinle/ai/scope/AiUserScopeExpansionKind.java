package com.nongxinle.ai.scope;

/**
 * 订货用户可视部门范围的扩展方式（仅在单次请求内计算，不落库）。
 */
public enum AiUserScopeExpansionKind {

    /** 以用户挂靠部门为根做子树（默认；子部门订货/库房/采购锚点等多属此类）。 */
    ANCHOR_SUBTREE,

    /** 先上溯到父级门店节点（{@code gb_department_is_group_dep = 1}），再取其整棵子树。 */
    PARENT_STORE_SUBTREE,

    /** 先上溯到片区/区域根节点（{@code gb_department_type = REGION}），再取其整棵子树。 */
    REGION_SUBTREE
}
