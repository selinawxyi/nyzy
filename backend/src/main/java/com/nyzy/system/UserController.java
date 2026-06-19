package com.nyzy.system;

import com.nyzy.common.PageResult;
import com.nyzy.common.Result;
import com.nyzy.system.entity.User;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public Result<PageResult<User>> list(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String role,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "10") long size) {
        return Result.ok(service.page(keyword, role, status, page, size));
    }

    @PostMapping
    public Result<Long> create(@RequestBody Map<String, Object> body) {
        User u = new User();
        u.setUsername((String) body.get("username"));
        u.setNickname((String) body.get("nickname"));
        u.setRole((String) body.get("role"));
        u.setPhone((String) body.get("phone"));
        if (body.get("regionId") != null) u.setRegionId(Long.valueOf(body.get("regionId").toString()));
        if (body.get("status") != null) u.setStatus((Integer) body.get("status"));
        return Result.ok(service.create(u, (String) body.get("password")));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        service.update(user);
        return Result.ok();
    }

    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        service.resetPassword(id, body == null ? null : body.get("password"));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }
}
