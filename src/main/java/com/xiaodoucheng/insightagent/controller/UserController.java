package com.xiaodoucheng.insightagent.controller;

import com.xiaodoucheng.insightagent.common.Result;
import com.xiaodoucheng.insightagent.dto.LoginFormDTO;
import com.xiaodoucheng.insightagent.service.UserService;
import com.xiaodoucheng.insightagent.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone) {
        return userService.sendCode(phone);
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO form) {
        return userService.login(form);
    }
    @GetMapping("/me")
    public Result me() {
        return Result.ok(UserHolder.getUser());
    }

}
