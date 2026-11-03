package com.mall.user.controller;

import com.mall.api.user.dto.UserDTO;
import com.mall.common.result.Result;
import com.mall.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public Result<UserDTO> getById(@PathVariable("id") Long id) {
        return Result.ok(userService.getById(id));
    }
}
