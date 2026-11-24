package com.xiaodoucheng.insightagent.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色权限注解，贴在 Controller 的类或方法上。
 * 比如 @RequireRole("ADMIN") 表示只有管理员能进。
 * 校验由 RoleInterceptor 做，不用 Spring Security，也不用加 AOP 依赖。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    String value() default "ADMIN";
}
