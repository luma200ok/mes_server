package com.mes.domain.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EquipmentRequest(
        @NotBlank @Pattern(regexp = "EQ-\\d{3}", message = "설비 ID는 EQ-000 형식이어야 합니다.") String equipmentId,
        @NotBlank String name,
        @NotBlank String location
) {}
