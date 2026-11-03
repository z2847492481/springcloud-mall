package com.mall.api.user;

import com.mall.api.user.dto.LoginRequest;
import com.mall.api.user.dto.LoginResponse;
import com.mall.api.user.dto.UserDTO;
import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "mall-user", path = "/user")
public interface UserApi {

    @PostMapping("/login")
    Result<LoginResponse> login(@RequestBody LoginRequest request);

    @GetMapping("/{id}")
    Result<UserDTO> getById(@PathVariable("id") Long id);
}
