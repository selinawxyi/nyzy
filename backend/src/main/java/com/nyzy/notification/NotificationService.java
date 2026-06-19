package com.nyzy.notification;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nyzy.auth.UserContext;
import com.nyzy.common.PageResult;
import com.nyzy.notification.mapper.NotificationMapper;
import com.nyzy.system.entity.User;
import com.nyzy.system.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationMapper mapper;
    private final UserMapper userMapper;

    public NotificationService(NotificationMapper mapper, UserMapper userMapper) {
        this.mapper = mapper;
        this.userMapper = userMapper;
    }

    /** 发送给指定用户 */
    public void notifyUser(String recipient, String title, String content, String bizType, Long bizId) {
        if (!StringUtils.hasText(recipient)) return;
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setTitle(title);
        n.setContent(content);
        n.setBizType(bizType);
        n.setBizId(bizId);
        n.setIsRead(0);
        mapper.insert(n);
    }

    /** 发送给所有管理员 */
    public void notifyAdmins(String title, String content, String bizType, Long bizId) {
        List<User> admins = userMapper.selectList(new QueryWrapper<User>().eq("role", "admin"));
        for (User a : admins) {
            notifyUser(a.getUsername(), title, content, bizType, bizId);
        }
    }

    public long unreadCount() {
        return mapper.selectCount(new QueryWrapper<Notification>()
                .eq("recipient", UserContext.username()).eq("is_read", 0));
    }

    public PageResult<Notification> page(boolean onlyUnread, long page, long size) {
        QueryWrapper<Notification> w = new QueryWrapper<Notification>()
                .eq("recipient", UserContext.username());
        if (onlyUnread) w.eq("is_read", 0);
        w.orderByDesc("id");
        IPage<Notification> p = mapper.selectPage(new Page<>(page, size), w);
        return new PageResult<>(p.getTotal(), p.getRecords());
    }

    public void markRead(Long id) {
        Notification n = mapper.selectById(id);
        if (n == null || !UserContext.username().equals(n.getRecipient())) return;
        Notification upd = new Notification();
        upd.setId(id);
        upd.setIsRead(1);
        mapper.updateById(upd);
    }

    public void markAllRead() {
        Notification upd = new Notification();
        upd.setIsRead(1);
        mapper.update(upd, new QueryWrapper<Notification>()
                .eq("recipient", UserContext.username()).eq("is_read", 0));
    }
}
