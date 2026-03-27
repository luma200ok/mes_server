package com.mes.domain.workorder.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorkOrderRequest(
        @NotBlank String equipmentId,
        @NotNull @Min(1) Integer plannedQty
) {}
