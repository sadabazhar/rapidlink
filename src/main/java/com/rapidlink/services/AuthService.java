package com.rapidlink.services;

import com.rapidlink.dto.request.auth.LoginRequest;
import com.rapidlink.dto.request.auth.RegisterRequest;
import com.rapidlink.dto.response.auth.LoginResponse;
import com.rapidlink.dto.response.auth.RegisterResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}
