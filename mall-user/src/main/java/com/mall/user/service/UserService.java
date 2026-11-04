package com.mall.user.service;

import com.mall.api.user.dto.LoginRequest;
import com.mall.api.user.dto.LoginResponse;
import com.mall.api.user.dto.UserDTO;

public interface UserService {

    UserDTO getById(Long id);

    LoginResponse login(LoginRequest request);
}
