package com.nyzy.recycle;

import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.recycle.mapper.RecycleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RecycleServiceTest {

    private final RecycleMapper mapper = Mockito.mock(RecycleMapper.class);
    private final RecycleService service = new RecycleService(mapper);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void restore_invalidBizType_rejected() {
        ApiException ex = assertThrows(ApiException.class, () -> service.restore("hacker; DROP TABLE", 1L));
        assertTrue(ex.getMessage().contains("非法的业务类型"));
    }

    @Test
    void restore_validType_mapsToWhitelistedTable() {
        when(mapper.restore(eq("abandon_parcel"), eq(5L))).thenReturn(1);
        service.restore("abandon", 5L);
        verify(mapper).restore("abandon_parcel", 5L);
    }

    @Test
    void purge_nonAdmin_forbidden() {
        UserContext.set(new UserContext.CurrentUser(2L, "operator", "operator"));
        ApiException ex = assertThrows(ApiException.class, () -> service.purge("water", 1L));
        assertEquals(403, ex.getCode());
    }

    @Test
    void purge_admin_validType_ok() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
        when(mapper.purge(eq("water_facility"), eq(9L))).thenReturn(1);
        assertDoesNotThrow(() -> service.purge("water", 9L));
        verify(mapper).purge("water_facility", 9L);
    }

    @Test
    void autoPurgeExpired_runsAllTables() {
        when(mapper.purgeExpired(any(), any())).thenReturn(0);
        service.autoPurgeExpired();
        verify(mapper, times(6)).purgeExpired(any(), any());   // 6 张白名单表
    }
}
