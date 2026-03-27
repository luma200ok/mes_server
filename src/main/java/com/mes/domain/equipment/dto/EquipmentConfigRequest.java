package com.mes.domain.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EquipmentConfigRequest(
        @NotBlank String equipmentId,
        @NotNull @Positive Double maxTemperature,
        @NotNull @Positive Double maxVibration,
        @NotNull @Positive Double maxRpm
) {}
