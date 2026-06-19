package com.rapidlink.dto.response.auth;

import com.rapidlink.enums.Role;

import java.time.LocalDateTime;
import java.util.UUID;

public record RegisterResponse(

        UUID id,

        String firstName,

        String lastName,

        String email,

        Role role,

        LocalDateTime createdAt

) {}
