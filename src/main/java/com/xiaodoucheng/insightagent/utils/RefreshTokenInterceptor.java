package com.xiaodoucheng.insightagent.utils;

import com.xiaodoucheng.insightagent.common.RedisConstants;
import com.xiaodoucheng.insightagent.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("authorization");
        if (token == null || token.isBlank()) {
            return true;
        }
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap.isEmpty()) {
            return true;
        }
        UserDTO user = new UserDTO();
        user.setId(Long.valueOf(userMap.get("id").toString()));
        user.setNickName((String) userMap.get("nickName"));
        user.setPhone((String) userMap.get("phone"));
        // 角色从 Redis 里的 Token 恢复，老 Token 里没有 role 字段，空就按 USER 算，不用让老用户重新注册。
        Object roleObj = userMap.get("role");
        String role = roleObj == null || roleObj.toString().isBlank() ? "USER" : roleObj.toString();
        user.setRole(role);
        UserHolder.saveUser(user);
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeUser();
    }
}
