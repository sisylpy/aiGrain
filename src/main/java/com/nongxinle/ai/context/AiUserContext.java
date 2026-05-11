package com.nongxinle.ai.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Run 维度用户快照；第一版可由请求与会话占位字段装配，后续接员工/角色表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUserContext {

    /** 来源：{@code gb_department_user.gb_du_admin}；合成角色试验时为 null。 */
    private Integer sourceAdminRole;

    private Long userId;
    private String roleCode;
    private String roleName;

    /** 挂靠部门父级 ID（表中 {@code gb_du_department_father_id}）；供区域/收窄策略扩展。 */
    private Integer departmentFatherId;

    private Long groupId;
    private Long regionId;
    /** 用户挂靠部门 ID（可能为门店子部门，与门店根不同）。 */
    private Long departmentId;
    /**
     * 门店根部门 ID：由 {@code gb_department} 沿 {@code gb_department_father_id} 归一到 {@code father_id = 0} 的根，
     * 不得将挂靠子部门 ID 误作门店根。
     */
    private Long storeId;
    private Long distributerId;

    /**
     * 权限内的门店<b>根</b>部门 ID 列表（已从 {@link #departmentId} 归一化）；非 {@code gb_du_department_id} 原值。
     * 集团等宽角色可为空；单店场景通常为单元素列表。
     */
    @Builder.Default
    private List<Long> allowedStoreIds = new ArrayList<>();

    @Builder.Default
    private List<String> permissions = new ArrayList<>();
}
