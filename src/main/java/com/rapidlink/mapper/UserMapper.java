package com.rapidlink.mapper;

import com.rapidlink.dto.request.auth.RegisterRequest;
import com.rapidlink.dto.response.auth.RegisterResponse;
import com.rapidlink.entity.User;

public final class UserMapper {

    private UserMapper() {}

    public static User toEntity(RegisterRequest request, String passwordHash) {
        return User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .passwordHash(passwordHash)
                .build();
    }

    public static RegisterResponse toRegisterResponse(User user) {
        return new RegisterResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
