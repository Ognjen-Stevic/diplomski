package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.AuthResponse;
import com.ognjen.diplomski.dto.LoginRequest;
import com.ognjen.diplomski.dto.RegisterRequest;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest request);
}
