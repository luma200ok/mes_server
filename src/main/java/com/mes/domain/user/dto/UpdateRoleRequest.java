package com.mes.domain.user.dto;

import com.mes.domain.user.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
        @NotNull UserRole role
) {}
