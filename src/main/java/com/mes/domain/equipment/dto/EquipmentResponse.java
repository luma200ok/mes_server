package com.mes.domain.equipment.dto;

import com.mes.domain.equipment.Equipment;
import com.mes.domain.equipment.EquipmentStatus;

import java.time.LocalDateTime;

public record EquipmentResponse(
        Long id,
        String equipmentId,
        String name,
        String location,
        EquipmentStatus status,
        LocalDateTime createdAt
) {
    public static EquipmentResponse from(Equipment equipment) {
        return new EquipmentResponse(
                equipment.getId(),
                equipment.getEquipmentId(),
                equipment.getName(),
                equipment.getLocation(),
                equipment.getStatus(),
                equipment.getCreatedAt()
        );
    }
}
