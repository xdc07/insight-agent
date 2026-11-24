package com.xiaodoucheng.insightagent.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String phone;
    // 跟 User 实体和 Redis 里 Token 的 role 保持一致，USER 或 ADMIN。
    private String role;
}
