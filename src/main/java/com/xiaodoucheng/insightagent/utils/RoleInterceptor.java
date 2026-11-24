package com.xiaodoucheng.insightagent.utils;

import com.xiaodoucheng.insightagent.common.RequireRole;
import com.xiaodoucheng.insightagent.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 角色校验拦截器，在 LoginInterceptor 之后跑，order 是 2。
 * 只看贴了 @RequireRole 的接口，没贴的不管。
 * 没登录的情况交给 LoginInterceptor 返回 401，这里直接放行，不抢它的活。
 * 登录了但角色不对，返回 403，body 跟项目里的 Result 格式保持一致。
 */
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole == null) {
            return true;
        }
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return true;
        }
        String role = user.getRole();
        if (role == null || role.isBlank()) {
            role = "USER";
        }
        if (requireRole.value().equals(role)) {
            return true;
        }
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"errorMsg\":\"权限不足，需要管理员角色\"}");
        return false;
    }
}
