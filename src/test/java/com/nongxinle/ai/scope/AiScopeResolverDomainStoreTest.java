package com.nongxinle.ai.scope;

import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiScopeResolverDomainStoreTest {

    @Mock
    private GbDepartmentMapper departmentMapper;

    @Test
    void resolvesChildDepartmentToStoreRoot() {
        GbDepartmentEntity child = new GbDepartmentEntity();
        child.setGbDepartmentId(5);
        child.setGbDepartmentFatherId(10);

        GbDepartmentEntity store = new GbDepartmentEntity();
        store.setGbDepartmentId(10);
        store.setGbDepartmentFatherId(0);

        when(departmentMapper.selectById(5)).thenReturn(child);
        when(departmentMapper.selectById(10)).thenReturn(store);

        AiScopeResolver resolver = new AiScopeResolver(departmentMapper);
        assertThat(resolver.resolveDomainStoreDepartmentId(5)).isEqualTo(10);
    }

    @Test
    void missingRow_returnsOriginalId() {
        when(departmentMapper.selectById(99)).thenReturn(null);
        AiScopeResolver resolver = new AiScopeResolver(departmentMapper);
        assertThat(resolver.resolveDomainStoreDepartmentId(99)).isEqualTo(99);
    }
}
