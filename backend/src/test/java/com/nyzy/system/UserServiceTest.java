package com.nyzy.system;

import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.system.entity.User;
import com.nyzy.system.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserMapper mapper = Mockito.mock(UserMapper.class);
    private final UserService service = new UserService(mapper);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void asAdmin() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
    }

    private User user(Long id, String role) {
        User u = new User();
        u.setId(id);
        u.setUsername("u" + id);
        u.setRole(role);
        return u;
    }

    @Test
    void create_nonAdmin_forbidden() {
        UserContext.set(new UserContext.CurrentUser(2L, "operator", "operator"));
        ApiException ex = assertThrows(ApiException.class, () -> service.create(user(null, "operator"), "x"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void create_duplicateUsername_rejected() {
        asAdmin();
        User u = user(null, "operator");
        u.setNickname("张三");
        when(mapper.selectCount(any())).thenReturn(1L);
        ApiException ex = assertThrows(ApiException.class, () -> service.create(u, "123456"));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    void delete_self_rejected() {
        asAdmin();
        when(mapper.selectById(1L)).thenReturn(user(1L, "admin"));
        ApiException ex = assertThrows(ApiException.class, () -> service.delete(1L));
        assertTrue(ex.getMessage().contains("当前登录账号"));
    }

    @Test
    void delete_lastAdmin_rejected() {
        asAdmin();
        when(mapper.selectById(9L)).thenReturn(user(9L, "admin"));
        when(mapper.selectCount(any())).thenReturn(1L);   // 仅剩 1 个管理员
        ApiException ex = assertThrows(ApiException.class, () -> service.delete(9L));
        assertTrue(ex.getMessage().contains("至少保留一个管理员"));
    }
}
