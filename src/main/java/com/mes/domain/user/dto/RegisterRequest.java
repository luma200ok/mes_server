package com.mes.domain.user.dto;

import com.mes.domain.user.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 4, max = 50) String username,
        @NotBlank @Size(min = 6) String password,
        @NotNull UserRole role
) {}
