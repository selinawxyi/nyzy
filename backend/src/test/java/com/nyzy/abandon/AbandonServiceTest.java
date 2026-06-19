package com.nyzy.abandon;

import com.nyzy.abandon.entity.AbandonParcel;
import com.nyzy.abandon.mapper.AbandonParcelMapper;
import com.nyzy.abandon.mapper.AbandonReasonMapper;
import com.nyzy.abandon.mapper.AbandonTaskMapper;
import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.cultivation.mapper.PlantingRecordMapper;
import com.nyzy.system.mapper.AuditLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Year;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** AbandonService 业务规则单元测试 (Mockito, 无需数据库) */
class AbandonServiceTest {

    private final AbandonParcelMapper parcelMapper = Mockito.mock(AbandonParcelMapper.class);
    private final AbandonReasonMapper reasonMapper = Mockito.mock(AbandonReasonMapper.class);
    private final AbandonTaskMapper taskMapper = Mockito.mock(AbandonTaskMapper.class);
    private final AuditLogMapper auditLogMapper = Mockito.mock(AuditLogMapper.class);
    private final PlantingRecordMapper plantingMapper = Mockito.mock(PlantingRecordMapper.class);
    private final com.nyzy.auth.DataScope dataScope = Mockito.mock(com.nyzy.auth.DataScope.class);
    private final com.nyzy.notification.NotificationService notificationService =
            Mockito.mock(com.nyzy.notification.NotificationService.class);

    private final AbandonService service =
            new AbandonService(parcelMapper, reasonMapper, taskMapper, auditLogMapper, plantingMapper, dataScope, notificationService);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private AbandonParcel parcel(Long id, String status) {
        AbandonParcel p = new AbandonParcel();
        p.setId(id);
        p.setParcelCode("JY-T001");
        p.setGovernStatus(status);
        return p;
    }

    @Test
    void changeStatus_invalidTransition_rejected() {
        when(parcelMapper.selectById(1L)).thenReturn(parcel(1L, "UNGOVERNED"));
        // 未治理 -> 已治理 不允许 (必须先经治理中)
        ApiException ex = assertThrows(ApiException.class,
                () -> service.changeStatus(1L, "GOVERNED", null));
        assertTrue(ex.getMessage().contains("不允许的状态流转"));
    }

    @Test
    void changeStatus_validTransition_ok() {
        when(parcelMapper.selectById(1L)).thenReturn(parcel(1L, "UNGOVERNED"));
        assertDoesNotThrow(() -> service.changeStatus(1L, "GOVERNING", "开始治理"));
    }

    @Test
    void delete_nonAdmin_forbidden() {
        UserContext.set(new UserContext.CurrentUser(2L, "operator", "operator"));
        ApiException ex = assertThrows(ApiException.class,
                () -> service.delete(1L, "重复录入"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void delete_withoutReason_rejected() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
        ApiException ex = assertThrows(ApiException.class, () -> service.delete(1L, " "));
        assertTrue(ex.getMessage().contains("删除原因"));
    }

    @Test
    void delete_withActiveTask_blocked() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
        when(parcelMapper.selectById(1L)).thenReturn(parcel(1L, "GOVERNING"));
        when(taskMapper.selectCount(any())).thenReturn(1L);
        ApiException ex = assertThrows(ApiException.class,
                () -> service.delete(1L, "误判"));
        assertTrue(ex.getMessage().contains("治理任务"));
    }

    private com.nyzy.abandon.entity.AbandonTask task(Long id, String status) {
        com.nyzy.abandon.entity.AbandonTask t = new com.nyzy.abandon.entity.AbandonTask();
        t.setId(id);
        t.setAbandonId(1L);
        t.setName("治理任务");
        t.setTaskStatus(status);
        return t;
    }

    @Test
    void taskProgress_outOfRange_rejected() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "HANDLING"));
        ApiException ex = assertThrows(ApiException.class, () -> service.updateTaskProgress(1L, 150));
        assertTrue(ex.getMessage().contains("0-100"));
    }

    @Test
    void taskProgress_onDoneTask_rejected() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "DONE"));
        ApiException ex = assertThrows(ApiException.class, () -> service.updateTaskProgress(1L, 50));
        assertTrue(ex.getMessage().contains("已验收"));
    }

    @Test
    void acceptTask_pass_movesParcelToGoverned_andWritesPlanting() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "ACCEPTING"));
        when(parcelMapper.selectById(1L)).thenReturn(parcel(1L, "GOVERNING"));
        service.acceptTask(1L, true, "玉米", new java.math.BigDecimal("10"), 2025);
        // 回写种植记录
        verify(plantingMapper).insert(any());
        // 任务与地块均被更新
        verify(taskMapper, atLeastOnce()).updateById(any());
        verify(parcelMapper, atLeastOnce()).updateById(any());
    }

    @Test
    void acceptTask_reject_setsReturned_noPlanting() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "ACCEPTING"));
        service.acceptTask(1L, false, null, null, null);
        verify(plantingMapper, never()).insert(any());
    }

    @Test
    void taskFeedback_blank_rejected() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "HANDLING"));
        ApiException ex = assertThrows(ApiException.class, () -> service.taskFeedback(1L, " "));
        assertTrue(ex.getMessage().contains("反馈"));
    }

    @Test
    void create_futureYear_rejected() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
        AbandonParcel p = parcel(null, "UNGOVERNED");
        p.setAbandonYear(Year.now().getValue() + 1);
        ApiException ex = assertThrows(ApiException.class, () -> service.create(p));
        assertTrue(ex.getMessage().contains("未来年份"));
    }
}
