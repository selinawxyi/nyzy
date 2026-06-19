package com.nyzy.notification;

import com.nyzy.auth.UserContext;
import com.nyzy.notification.mapper.NotificationMapper;
import com.nyzy.system.entity.User;
import com.nyzy.system.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private final NotificationMapper mapper = Mockito.mock(NotificationMapper.class);
    private final UserMapper userMapper = Mockito.mock(UserMapper.class);
    private final NotificationService service = new NotificationService(mapper, userMapper);

    @BeforeEach
    void setUp() {
        UserContext.set(new UserContext.CurrentUser(1L, "alice", "operator", null));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void notifyUser_blankRecipient_noInsert() {
        service.notifyUser("", "t", "c", "biz", 1L);
        service.notifyUser(null, "t", "c", "biz", 1L);
        verify(mapper, never()).insert(any());
    }

    @Test
    void notifyAdmins_insertsPerAdmin() {
        User a1 = new User(); a1.setUsername("admin1");
        User a2 = new User(); a2.setUsername("admin2");
        when(userMapper.selectList(any())).thenReturn(Arrays.asList(a1, a2));
        service.notifyAdmins("标题", "内容", "abandon", 5L);
        verify(mapper, times(2)).insert(any());
    }

    @Test
    void markRead_othersNotification_ignored() {
        Notification n = new Notification();
        n.setId(9L);
        n.setRecipient("bob");          // 当前用户是 alice
        when(mapper.selectById(9L)).thenReturn(n);
        service.markRead(9L);
        verify(mapper, never()).updateById(any());   // 不能标记他人通知
    }

    @Test
    void markRead_ownNotification_updates() {
        Notification n = new Notification();
        n.setId(9L);
        n.setRecipient("alice");
        when(mapper.selectById(9L)).thenReturn(n);
        service.markRead(9L);
        verify(mapper).updateById(any());
    }
}
