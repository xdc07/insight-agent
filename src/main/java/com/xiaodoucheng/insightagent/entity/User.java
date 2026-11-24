package com.xiaodoucheng.insightagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String phone;
    private String nickName;
    private LocalDateTime createTime;
    private String icon;
    // 角色权限：USER 是普通用户，ADMIN 是管理员，数据库里是 VARCHAR，默认 USER。
    // 用 String 而不用枚举存库，是为了不给 MyBatis-Plus 加枚举转换的负担。
    private String role;
}
