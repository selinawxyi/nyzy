package com.nyzy.notification;

import com.nyzy.common.PageResult;
import com.nyzy.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount() {
        Map<String, Object> m = new HashMap<>();
        m.put("count", service.unreadCount());
        return Result.ok(m);
    }

    @GetMapping("/list")
    public Result<PageResult<Notification>> list(@RequestParam(defaultValue = "false") boolean onlyUnread,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        return Result.ok(service.page(onlyUnread, page, size));
    }

    @PostMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        service.markRead(id);
        return Result.ok();
    }

    @PostMapping("/read-all")
    public Result<Void> readAll() {
        service.markAllRead();
        return Result.ok();
    }
}
