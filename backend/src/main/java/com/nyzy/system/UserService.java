package com.nyzy.system;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.common.PageResult;
import com.nyzy.system.entity.User;
import com.nyzy.system.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private static final String DEFAULT_PASSWORD = "123456";

    private final UserMapper mapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserService(UserMapper mapper) {
        this.mapper = mapper;
    }

    public PageResult<User> page(String keyword, String role, Integer status, long page, long size) {
        requireAdmin();
        QueryWrapper<User> w = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            w.and(x -> x.like("username", kw).or().like("nickname", kw).or().like("phone", kw));
        }
        if (StringUtils.hasText(role)) w.eq("role", role);
        if (status != null) w.eq("status", status);
        w.orderByDesc("id");
        IPage<User> p = mapper.selectPage(new Page<>(page, size), w);
        p.getRecords().forEach(u -> u.setPassword(null));   // 不外泄密码
        return new PageResult<>(p.getTotal(), p.getRecords());
    }

    @Transactional
    public Long create(User u, String password) {
        requireAdmin();
        if (!StringUtils.hasText(u.getUsername())) throw new ApiException("用户名不能为空");
        if (!StringUtils.hasText(u.getNickname())) throw new ApiException("显示名不能为空");
        if (mapper.selectCount(new QueryWrapper<User>().eq("username", u.getUsername())) > 0) {
            throw new ApiException("用户名已存在: " + u.getUsername());
        }
        String pwd = StringUtils.hasText(password) ? password : DEFAULT_PASSWORD;
        u.setPassword(encoder.encode(pwd));
        if (!StringUtils.hasText(u.getRole())) u.setRole("operator");
        if (u.getStatus() == null) u.setStatus(1);
        mapper.insert(u);
        return u.getId();
    }

    @Transactional
    public void update(User u) {
        requireAdmin();
        User old = require(u.getId());
        // 仅允许修改资料字段, 用户名与密码不通过本接口修改
        old.setNickname(u.getNickname());
        old.setRole(u.getRole());
        old.setPhone(u.getPhone());
        old.setRegionId(u.getRegionId());
        if (u.getStatus() != null) {
            if (old.getId().equals(currentUserId()) && u.getStatus() == 0) {
                throw new ApiException("不能停用当前登录账号");
            }
            old.setStatus(u.getStatus());
        }
        old.setPassword(null);   // 避免覆盖密码
        mapper.updateById(old);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        requireAdmin();
        require(id);
        String pwd = StringUtils.hasText(newPassword) ? newPassword : DEFAULT_PASSWORD;
        User u = new User();
        u.setId(id);
        u.setPassword(encoder.encode(pwd));
        mapper.updateById(u);
    }

    @Transactional
    public void delete(Long id) {
        requireAdmin();
        User old = require(id);
        if (old.getId().equals(currentUserId())) {
            throw new ApiException("不能删除当前登录账号");
        }
        if ("admin".equals(old.getRole())
                && mapper.selectCount(new QueryWrapper<User>().eq("role", "admin")) <= 1) {
            throw new ApiException("系统至少保留一个管理员账号");
        }
        mapper.deleteById(id);
    }

    private User require(Long id) {
        User u = mapper.selectById(id);
        if (u == null) throw new ApiException(404, "用户不存在");
        return u;
    }

    private void requireAdmin() {
        if (!UserContext.isAdmin()) throw new ApiException(403, "仅管理员可管理用户");
    }

    private Long currentUserId() {
        return UserContext.get() == null ? null : UserContext.get().id;
    }
}
