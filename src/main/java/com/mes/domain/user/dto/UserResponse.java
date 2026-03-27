package com.mes.domain.user.dto;

import com.mes.domain.user.User;
import com.mes.domain.user.UserRole;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        UserRole role,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt());
    }
}
