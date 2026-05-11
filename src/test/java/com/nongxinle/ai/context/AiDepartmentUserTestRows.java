package com.nongxinle.ai.context;

import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbDepartmentMapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 单测：拼装 {@link GbDepartmentUserEntity} stub 与带 mock 的 {@link AiUserContextResolver}。 */
public final class AiDepartmentUserTestRows {

    private AiDepartmentUserTestRows() {
    }

    public static GbDepartmentUserEntity groupManager(int userPk, int deptId, int distributerId) {
        return baseRow(userPk, GbConstants.DepartmentUserRole.GROUP_MANAGER_APP, deptId, distributerId);
    }

    public static GbDepartmentUserEntity storeManager(int userPk, int deptId, int distributerId) {
        return baseRow(userPk, GbConstants.DepartmentUserRole.STORE_MANAGER_APP, deptId, distributerId);
    }

    public static GbDepartmentUserEntity storePurchaser(int userPk, int deptId, int distributerId) {
        return baseRow(userPk, GbConstants.DepartmentUserRole.STORE_PURCHASER_APP, deptId, distributerId);
    }

    public static GbDepartmentUserEntity warehouseManager(int userPk, int deptId, int distributerId) {
        return baseRow(userPk, GbConstants.DepartmentUserRole.WAREHOUSE_APP, deptId, distributerId);
    }

    private static GbDepartmentUserEntity baseRow(int userPk, int admin, int deptId, int distributerId) {
        GbDepartmentUserEntity e = new GbDepartmentUserEntity();
        e.setGbDepartmentUserId(userPk);
        e.setGbDuAdmin(admin);
        e.setGbDuDepartmentId(deptId);
        e.setGbDuDistributerId(distributerId);
        e.setGbDuDepartmentFatherId(0);
        return e;
    }

    public static AiUserContextResolver resolverReturning(GbDepartmentUserEntity row) {
        GbDepartmentUserService svc = mock(GbDepartmentUserService.class);
        GbDepartmentMapper mapper = mock(GbDepartmentMapper.class);
        Integer deptId = row.getGbDuDepartmentId();
        if (deptId != null) {
            GbDepartmentEntity chain = departmentChainForUserDept(row, mapper);
            when(mapper.selectById(deptId)).thenReturn(chain);
        }
        when(svc.getById(row.getGbDepartmentUserId())).thenReturn(row);
        return new AiUserContextResolver(svc, mapper);
    }

    /**
     * 为挂靠部门构造一条可 walk 到门店根的链：若 {@code gb_du_department_father_id == 0}，则视为已在根上；
     * 否则补一行父部门（father=0），满足单测归一路径。
     */
    private static GbDepartmentEntity departmentChainForUserDept(GbDepartmentUserEntity row, GbDepartmentMapper mapper) {
        int selfId = row.getGbDuDepartmentId();
        Integer father = row.getGbDuDepartmentFatherId();
        GbDepartmentEntity self = new GbDepartmentEntity();
        self.setGbDepartmentId(selfId);
        self.setGbDepartmentFatherId(father);
        if (father != null && father != 0) {
            GbDepartmentEntity parent = new GbDepartmentEntity();
            parent.setGbDepartmentId(father);
            parent.setGbDepartmentFatherId(0);
            when(mapper.selectById(father)).thenReturn(parent);
        }
        return self;
    }
}
