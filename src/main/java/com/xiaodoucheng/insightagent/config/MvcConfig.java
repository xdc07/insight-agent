package com.xiaodoucheng.insightagent.config;

import com.xiaodoucheng.insightagent.utils.LoginInterceptor;
import com.xiaodoucheng.insightagent.utils.RefreshTokenInterceptor;
import com.xiaodoucheng.insightagent.utils.RoleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    private final StringRedisTemplate stringRedisTemplate;

    public MvcConfig(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns("/user/code", "/user/login", "/doc.html", "/", "/index.html", "/webjars/**", "/v3/**", "/favicon.ico")
                .order(1);
        // 角色校验只拦 /admin/**，order 是 2，在登录校验之后跑。
        // 没登录先是 LoginInterceptor 给 401，登录了但不是管理员才是这里给 403。
        registry.addInterceptor(new RoleInterceptor())
                .addPathPatterns("/admin/**")
                .order(2);
    }
}
