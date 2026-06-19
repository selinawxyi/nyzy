package com.nyzy.cultivation;

import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.cultivation.entity.LandQuality;
import com.nyzy.cultivation.mapper.LandQualityMapper;
import com.nyzy.land.mapper.LandParcelMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class QualityServiceTest {

    private final LandQualityMapper mapper = Mockito.mock(LandQualityMapper.class);
    private final LandParcelMapper parcelMapper = Mockito.mock(LandParcelMapper.class);
    private final com.nyzy.auth.DataScope dataScope = Mockito.mock(com.nyzy.auth.DataScope.class);
    private final QualityService service = new QualityService(mapper, parcelMapper, dataScope);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private LandQuality q(int grade) {
        LandQuality e = new LandQuality();
        e.setParcelCode("JY-T001");
        e.setEvalYear(2024);
        e.setGrade(grade);
        return e;
    }

    @Test
    void create_gradeOutOfRange_rejected() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
        ApiException ex = assertThrows(ApiException.class, () -> service.create(q(11)));
        assertTrue(ex.getMessage().contains("1-10"));
    }

    @Test
    void create_negativeOrganic_rejected() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
        LandQuality e = q(3);
        e.setOrganicMatter(new BigDecimal("-1"));
        ApiException ex = assertThrows(ApiException.class, () -> service.create(e));
        assertTrue(ex.getMessage().contains("不能为负数"));
    }

    @Test
    void batch_noIds_rejected() {
        ApiException ex = assertThrows(ApiException.class, () -> service.batchUpdate(null, q(3)));
        assertTrue(ex.getMessage().contains("请选择"));
    }

    @Test
    void batch_noFields_rejected() {
        LandQuality empty = new LandQuality();   // 全空
        ApiException ex = assertThrows(ApiException.class,
                () -> service.batchUpdate(java.util.Arrays.asList(1L, 2L), empty));
        assertTrue(ex.getMessage().contains("至少填写一个"));
    }

    @Test
    void batch_invalidGrade_rejected() {
        LandQuality u = new LandQuality();
        u.setGrade(15);
        ApiException ex = assertThrows(ApiException.class,
                () -> service.batchUpdate(java.util.Arrays.asList(1L), u));
        assertTrue(ex.getMessage().contains("1-10"));
    }

    @Test
    void delete_nonAdmin_forbidden() {
        UserContext.set(new UserContext.CurrentUser(2L, "operator", "operator"));
        ApiException ex = assertThrows(ApiException.class, () -> service.delete(1L, "过期数据"));
        assertEquals(403, ex.getCode());
    }
}
