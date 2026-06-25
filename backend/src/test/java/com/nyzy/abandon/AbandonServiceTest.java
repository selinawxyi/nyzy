package com.nyzy.abandon;

import com.nyzy.abandon.entity.AbandonParcel;
import com.nyzy.abandon.mapper.AbandonParcelMapper;
import com.nyzy.abandon.mapper.AbandonReasonMapper;
import com.nyzy.abandon.mapper.AbandonTaskMapper;
import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.cultivation.mapper.PlantingRecordMapper;
import com.nyzy.system.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Year;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** AbandonService 业务规则单元测试 (Mockito, 无需数据库) */
class AbandonServiceTest {

    private final AbandonParcelMapper parcelMapper = Mockito.mock(AbandonParcelMapper.class);
    private final AbandonReasonMapper reasonMapper = Mockito.mock(AbandonReasonMapper.class);
    private final AbandonTaskMapper taskMapper = Mockito.mock(AbandonTaskMapper.class);
    private final AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
    private final PlantingRecordMapper plantingMapper = Mockito.mock(PlantingRecordMapper.class);
    private final com.nyzy.auth.DataScope dataScope = Mockito.mock(com.nyzy.auth.DataScope.class);
    private final com.nyzy.notification.NotificationService notificationService =
            Mockito.mock(com.nyzy.notification.NotificationService.class);

    private final AbandonService service =
            new AbandonService(parcelMapper, reasonMapper, taskMapper, auditLogService, plantingMapper, dataScope, notificationService);

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

    @Test
    void create_duplicateActiveParcelCode_rejected() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
        when(parcelMapper.selectCount(any())).thenReturn(1L); // 该地块已有一条进行中的撂荒记录
        AbandonParcel p = parcel(null, "UNGOVERNED");
        p.setAbandonYear(Year.now().getValue());
        ApiException ex = assertThrows(ApiException.class, () -> service.create(p));
        assertTrue(ex.getMessage().contains("重复上报"));
    }

    @Test
    void create_noActiveDuplicate_allowed() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
        when(parcelMapper.selectCount(any())).thenReturn(0L); // 无进行中记录(或仅有GOVERNED/REJECTED)
        AbandonParcel p = parcel(null, "UNGOVERNED");
        p.setAbandonYear(Year.now().getValue());
        assertDoesNotThrow(() -> service.create(p));
        verify(parcelMapper).insert(p);
    }

    private AbandonParcel abandon(String code, String region, String reasonType, String status, int year, String area) {
        AbandonParcel p = new AbandonParcel();
        p.setParcelCode(code);
        p.setRegionPath(region);
        p.setReasonType(reasonType);
        p.setGovernStatus(status);
        p.setAbandonYear(year);
        p.setArea(new java.math.BigDecimal(area));
        return p;
    }

    @Test
    void stats_groupsByStatusReasonRegionAndYear() {
        when(parcelMapper.selectList(any())).thenReturn(java.util.Arrays.asList(
                abandon("A", "省/州/市/镇/太平村/一组", "LABOR", "UNGOVERNED", 2023, "5"),
                abandon("B", "省/州/市/镇/太平村/二组", "LABOR", "UNGOVERNED", 2024, "3"),
                abandon("C", "省/州/市/镇/红旗村/一组", "ECON", "GOVERNING", 2024, "2")));

        Map<String, Object> r = service.stats(null);

        assertEquals(3, r.get("totalCount"));
        assertEquals(new java.math.BigDecimal("10"), r.get("totalArea"));
        List<Map<String, Object>> byStatus = (List<Map<String, Object>>) r.get("byStatus");
        assertEquals(2, byStatus.get(0).get("count")); // UNGOVERNED 出现2次, 排第一
        List<Map<String, Object>> byYear = (List<Map<String, Object>>) r.get("byYear");
        assertEquals(2023, byYear.get(0).get("year")); // 按年份升序
        assertEquals(2024, byYear.get(1).get("year"));
    }

    private com.nyzy.abandon.entity.AbandonTask template(String respUnit, String respPerson) {
        com.nyzy.abandon.entity.AbandonTask t = new com.nyzy.abandon.entity.AbandonTask();
        t.setRespUnit(respUnit);
        t.setRespPerson(respPerson);
        t.setStandard("恢复耕种, 种植一季粮食作物");
        return t;
    }

    @Test
    void batchCreateTasks_noIds_rejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.batchCreateTasks(null, template("镇政府", "张三")));
        assertTrue(ex.getMessage().contains("请选择"));
    }

    @Test
    void batchCreateTasks_missingRespUnit_rejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.batchCreateTasks(List.of(1L, 2L), template(null, "张三")));
        assertTrue(ex.getMessage().contains("责任单位"));
    }

    @Test
    void batchCreateTasks_nonUngovernedParcel_rejected() {
        when(parcelMapper.selectById(1L)).thenReturn(parcel(1L, "UNGOVERNED"));
        when(parcelMapper.selectById(2L)).thenReturn(parcel(2L, "GOVERNING"));
        ApiException ex = assertThrows(ApiException.class,
                () -> service.batchCreateTasks(List.of(1L, 2L), template("镇政府", "张三")));
        assertTrue(ex.getMessage().contains("当前状态非"));
    }

    @Test
    void batchCreateTasks_success_createsOneTaskPerParcel() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
        when(parcelMapper.selectById(1L)).thenReturn(parcel(1L, "UNGOVERNED"));
        when(parcelMapper.selectById(2L)).thenReturn(parcel(2L, "UNGOVERNED"));
        List<Long> ids = service.batchCreateTasks(List.of(1L, 2L), template("镇政府", "张三"));
        assertEquals(2, ids.size());
        verify(taskMapper, times(2)).insert(any());
    }
}
