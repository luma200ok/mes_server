package com.mes.domain.dashboard.dto;

import com.mes.domain.sensor.SensorHistory;

import java.time.LocalDateTime;

public record SensorHistoryResponse(
        Long id,
        String equipmentId,
        Double avgTemperature,
        Double avgVibration,
        Double avgRpm,
        LocalDateTime recordedAt
) {
    public static SensorHistoryResponse from(SensorHistory history) {
        return new SensorHistoryResponse(
                history.getId(),
                history.getEquipment().getEquipmentId(),
                history.getAvgTemperature(),
                history.getAvgVibration(),
                history.getAvgRpm(),
                history.getRecordedAt()
        );
    }
}
