package com.mall.user.controller;

import com.mall.api.user.dto.LoginRequest;
import com.mall.api.user.dto.LoginResponse;
import com.mall.api.user.dto.UserDTO;
import com.mall.common.result.Result;
import com.mall.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request));
    }

    @GetMapping("/{id}")
    public Result<UserDTO> getById(@PathVariable("id") Long id) {
        return Result.ok(userService.getById(id));
    }
}
