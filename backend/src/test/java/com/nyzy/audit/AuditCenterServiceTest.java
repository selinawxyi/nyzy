package com.nyzy.audit;

import com.nyzy.abandon.AbandonService;
import com.nyzy.abandon.mapper.AbandonParcelMapper;
import com.nyzy.common.ApiException;
import com.nyzy.resource.SupportFacilityService;
import com.nyzy.resource.WaterFacilityService;
import com.nyzy.resource.mapper.SupportFacilityMapper;
import com.nyzy.resource.mapper.WaterFacilityMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditCenterServiceTest {

    private final WaterFacilityMapper waterMapper = Mockito.mock(WaterFacilityMapper.class);
    private final SupportFacilityMapper supportMapper = Mockito.mock(SupportFacilityMapper.class);
    private final AbandonParcelMapper abandonMapper = Mockito.mock(AbandonParcelMapper.class);
    private final WaterFacilityService waterService = Mockito.mock(WaterFacilityService.class);
    private final SupportFacilityService supportService = Mockito.mock(SupportFacilityService.class);
    private final AbandonService abandonService = Mockito.mock(AbandonService.class);

    private final com.nyzy.notification.NotificationService notificationService =
            Mockito.mock(com.nyzy.notification.NotificationService.class);
    private final AuditCenterService service = new AuditCenterService(
            waterMapper, supportMapper, abandonMapper, waterService, supportService, abandonService, notificationService);

    @Test
    void audit_water_delegatesToWaterService() {
        service.audit("water", 5L, true);
        verify(waterService).audit(5L, true);
        verifyNoInteractions(supportService, abandonService);
    }

    @Test
    void audit_abandon_approve_movesToUngoverned() {
        service.audit("abandon", 7L, true);
        verify(abandonService).changeStatus(7L, "UNGOVERNED", null);
    }

    @Test
    void audit_abandon_reject_movesToRejected() {
        service.audit("abandon", 7L, false);
        verify(abandonService).changeStatus(7L, "REJECTED", null);
    }

    @Test
    void audit_invalidType_rejected() {
        ApiException ex = assertThrows(ApiException.class, () -> service.audit("evil", 1L, true));
        assertTrue(ex.getMessage().contains("非法的业务类型"));
    }
}
