package com.nyzy.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nyzy.system.entity.User;
import com.nyzy.system.mapper.UserMapper;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时将种子数据中以 'INIT:明文' 占位的密码替换为 BCrypt 密文.
 * 保证种子 SQL 无需硬编码 hash, 登录稳定可用.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public DataInitializer(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<User> users = userMapper.selectList(
                new QueryWrapper<User>().likeRight("password", "INIT:"));
        for (User u : users) {
            String plain = u.getPassword().substring("INIT:".length());
            u.setPassword(encoder.encode(plain));
            userMapper.updateById(u);
        }
        if (!users.isEmpty()) {
            System.out.println("[DataInitializer] 已初始化 " + users.size() + " 个用户密码");
        }
    }
}
