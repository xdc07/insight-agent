package com.xiaodoucheng.insightagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaodoucheng.insightagent.common.RedisConstants;
import com.xiaodoucheng.insightagent.common.Result;
import com.xiaodoucheng.insightagent.dto.LoginFormDTO;
import com.xiaodoucheng.insightagent.entity.User;
import com.xiaodoucheng.insightagent.mapper.UserMapper;
import com.xiaodoucheng.insightagent.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserService {

    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public UserService(UserMapper userMapper, StringRedisTemplate stringRedisTemplate) {
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Result sendCode(String phone) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            return Result.fail("手机号格式错误");
        }
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code,
                RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("发送短信验证码成功，手机号：{}，验证码：{}", phone, code);
        return Result.ok();
    }

    public Result login(LoginFormDTO form) {
        String phone = form.getPhone();
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            return Result.fail("手机号格式错误");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(form.getCode())) {
            return Result.fail("验证码错误");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setNickName("user_" + phone.substring(7));
            userMapper.insert(user);
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> userMap = new HashMap<>();
        userMap.put("id", user.getId().toString());
        userMap.put("phone", user.getPhone());
        userMap.put("nickName", user.getNickName());
        if (user.getIcon() != null) {
            userMap.put("icon", user.getIcon());
        }
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }
    public Result updateProfile(String nickName, String icon, String token) {
        User user = userMapper.selectById(UserHolder.getUser().getId());
        if (user == null) {
            return Result.fail("用户不存在");
        }
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        if (nickName != null && !nickName.isBlank()) {
            user.setNickName(nickName.trim());
            stringRedisTemplate.opsForHash().put(tokenKey, "nickName", user.getNickName());
        }
        if (icon != null) {
            user.setIcon(icon);
            stringRedisTemplate.opsForHash().put(tokenKey, "icon", icon);
        }
        userMapper.updateById(user);
        return Result.ok();
    }

}

