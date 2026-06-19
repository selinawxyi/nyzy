package com.nyzy.land;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyzy.abandon.mapper.AbandonParcelMapper;
import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.cultivation.mapper.LandQualityMapper;
import com.nyzy.cultivation.mapper.PlantingRecordMapper;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.land.mapper.LandParcelHistoryMapper;
import com.nyzy.land.mapper.LandParcelMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ParcelServiceTest {

    private final LandParcelMapper mapper = Mockito.mock(LandParcelMapper.class);
    private final LandParcelHistoryMapper historyMapper = Mockito.mock(LandParcelHistoryMapper.class);
    private final PlantingRecordMapper plantingMapper = Mockito.mock(PlantingRecordMapper.class);
    private final LandQualityMapper qualityMapper = Mockito.mock(LandQualityMapper.class);
    private final AbandonParcelMapper abandonMapper = Mockito.mock(AbandonParcelMapper.class);

    private final com.nyzy.auth.DataScope dataScope = Mockito.mock(com.nyzy.auth.DataScope.class);
    private final ParcelService service = new ParcelService(
            mapper, historyMapper, plantingMapper, qualityMapper, abandonMapper, new ObjectMapper(), dataScope);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private LandParcel parcel(Long id, String code) {
        LandParcel p = new LandParcel();
        p.setId(id);
        p.setParcelCode(code);
        p.setName("测试地块");
        return p;
    }

    @Test
    void create_duplicateCode_rejected() {
        when(mapper.selectCount(any())).thenReturn(1L);   // 编码已存在
        ApiException ex = assertThrows(ApiException.class, () -> service.create(parcel(null, "JY-T001")));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    void update_keyFieldChangeWithoutReason_rejected() {
        LandParcel old = parcel(1L, "JY-T001");
        old.setContractorCode("OLD");
        when(mapper.selectById(1L)).thenReturn(old);
        when(mapper.selectCount(any())).thenReturn(0L);

        LandParcel upd = parcel(1L, "JY-T001");
        upd.setContractorCode("NEW");   // 关键字段变更
        ApiException ex = assertThrows(ApiException.class, () -> service.update(upd, null));
        assertTrue(ex.getMessage().contains("变更原因"));
    }

    @Test
    void delete_nonAdmin_forbidden() {
        UserContext.set(new UserContext.CurrentUser(2L, "operator", "operator"));
        ApiException ex = assertThrows(ApiException.class, () -> service.delete(1L, "test"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void importUpdate_codeNotFound_rejected() {
        when(mapper.selectOne(any())).thenReturn(null);
        LandParcel patch = parcel(null, "NOEXIST");
        patch.setContractorName("张三");
        ApiException ex = assertThrows(ApiException.class, () -> service.importUpdate(patch));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    void importUpdate_noFields_rejected() {
        when(mapper.selectOne(any())).thenReturn(parcel(1L, "JY-T001"));
        LandParcel patch = parcel(null, "JY-T001");   // 仅编码, 无可更新字段
        ApiException ex = assertThrows(ApiException.class, () -> service.importUpdate(patch));
        assertTrue(ex.getMessage().contains("无可更新字段"));
    }

    @Test
    void delete_withRelatedData_blocked() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
        when(mapper.selectById(1L)).thenReturn(parcel(1L, "JY-T001"));
        when(plantingMapper.selectCount(any())).thenReturn(2L);   // 有种植关联
        when(qualityMapper.selectCount(any())).thenReturn(0L);
        when(abandonMapper.selectCount(any())).thenReturn(1L);
        ApiException ex = assertThrows(ApiException.class, () -> service.delete(1L, "清理"));
        assertTrue(ex.getMessage().contains("不可删除"));
    }
}
