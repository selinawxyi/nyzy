package com.nyzy.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nyzy.common.ApiException;
import com.nyzy.common.Result;
import com.nyzy.system.entity.User;
import com.nyzy.system.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        public String username;
        @NotBlank(message = "密码不能为空")
        public String password;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @javax.validation.Valid LoginRequest req) {
        User user = userMapper.selectOne(
                new QueryWrapper<User>().eq("username", req.username));
        if (user == null || !encoder.matches(req.password, user.getPassword())) {
            throw new ApiException(401, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new ApiException(403, "账号已停用");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole(), user.getRegionId());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", buildUserInfo(user));
        return Result.ok(data);
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        User user = userMapper.selectById(UserContext.get().id);
        if (user == null) throw new ApiException(401, "用户不存在");
        return Result.ok(buildUserInfo(user));
    }

    private Map<String, Object> buildUserInfo(User user) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("nickname", user.getNickname());
        info.put("role", user.getRole());
        info.put("phone", user.getPhone());
        return info;
    }
}
