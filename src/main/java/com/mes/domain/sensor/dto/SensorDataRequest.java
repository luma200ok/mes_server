package com.mes.domain.sensor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SensorDataRequest(
        @NotBlank String equipmentId,
        @NotNull @Positive Double temperature,
        @NotNull @Positive Double vibration,
        @NotNull @Positive Double rpm
) {}
