package com.mes.domain.equipment.dto;

import com.mes.domain.equipment.EquipmentConfig;

public record EquipmentConfigResponse(
        Long id,
        String equipmentId,
        Double maxTemperature,
        Double maxVibration,
        Double maxRpm
) {
    public static EquipmentConfigResponse from(EquipmentConfig config) {
        return new EquipmentConfigResponse(
                config.getId(),
                config.getEquipment().getEquipmentId(),
                config.getMaxTemperature(),
                config.getMaxVibration(),
                config.getMaxRpm()
        );
    }
}
