package com.xiaodoucheng.insightagent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaodoucheng.insightagent.common.RequireRole;
import com.xiaodoucheng.insightagent.common.Result;
import com.xiaodoucheng.insightagent.entity.Dataset;
import com.xiaodoucheng.insightagent.entity.DatasetRow;
import com.xiaodoucheng.insightagent.entity.User;
import com.xiaodoucheng.insightagent.mapper.DatasetMapper;
import com.xiaodoucheng.insightagent.mapper.DatasetRowMapper;
import com.xiaodoucheng.insightagent.mapper.UserMapper;
import com.xiaodoucheng.insightagent.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * 管理员接口，整个类都要求 ADMIN 角色。
 * 普通用户调这里会先被 LoginInterceptor 拦（没登录是 401），
 * 登录了但不是管理员会被 RoleInterceptor 拦（403）。
 * 注意：改了某个用户的角色之后，那个用户要重新登录一次，
 * 新 Token 里才有新角色，老 Token 里还是旧角色，这是 Token 方案本身的特点。
 */
@RestController
@RequestMapping("/admin")
@RequireRole("ADMIN")
public class AdminController {

    private final UserMapper userMapper;
    private final DatasetMapper datasetMapper;
    private final DatasetRowMapper datasetRowMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public AdminController(UserMapper userMapper, DatasetMapper datasetMapper,
                           DatasetRowMapper datasetRowMapper, StringRedisTemplate stringRedisTemplate) {
        this.userMapper = userMapper;
        this.datasetMapper = datasetMapper;
        this.datasetRowMapper = datasetRowMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @GetMapping("/users")
    public Result users() {
        // LambdaQueryWrapper 只能用方法引用排序，不能传字符串 "id"，字符串版是 QueryWrapper 才有的。
        return Result.ok(userMapper.selectList(
                new LambdaQueryWrapper<User>().orderByDesc(User::getId)));
    }

    @GetMapping("/datasets")
    public Result datasets() {
        return Result.ok(datasetMapper.selectList(
                new LambdaQueryWrapper<Dataset>().orderByDesc(Dataset::getId)));
    }

    @DeleteMapping("/datasets/{id}")
    public Result deleteDataset(@PathVariable Long id) {
        Dataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            return Result.fail("数据集不存在");
        }
        if ("PROCESSING".equals(dataset.getStatus())) {
            return Result.fail("数据还在处理中，稍后再删");
        }
        datasetRowMapper.delete(new LambdaQueryWrapper<DatasetRow>()
                .eq(DatasetRow::getDatasetId, id));
        datasetMapper.deleteById(id);
        stringRedisTemplate.delete("insight:dataset:overview:" + id);
        // 顺手把这个数据集的对话记忆也清掉，普通删除那条路现在还没清，这里管理员删就一起清了。
        Set<String> memoryKeys = stringRedisTemplate.keys("insight:chat:memory:*:" + id);
        if (memoryKeys != null && !memoryKeys.isEmpty()) {
            stringRedisTemplate.delete(memoryKeys);
        }
        return Result.ok();
    }

    @PutMapping("/users/{id}/role")
    public Result updateRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (role == null || (!"USER".equals(role) && !"ADMIN".equals(role))) {
            return Result.fail("角色只能是 USER 或 ADMIN");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        if (UserHolder.getUser().getId().equals(id) && "USER".equals(role)) {
            return Result.fail("不能把自己的管理员身份去掉");
        }
        user.setRole(role);
        userMapper.updateById(user);
        return Result.ok();
    }
}
