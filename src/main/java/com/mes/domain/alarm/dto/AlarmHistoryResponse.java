package com.mes.domain.alarm.dto;

import com.mes.domain.alarm.AlarmHistory;

import java.time.LocalDateTime;

public record AlarmHistoryResponse(
        Long id,
        String equipmentId,
        String metric,
        Double currentValue,
        Double threshold,
        Boolean discordSent,
        LocalDateTime sentAt
) {
    public static AlarmHistoryResponse from(AlarmHistory alarm) {
        return new AlarmHistoryResponse(
                alarm.getId(),
                alarm.getEquipmentId(),
                alarm.getMetric(),
                alarm.getCurrentValue(),
                alarm.getThreshold(),
                alarm.getDiscordSent(),
                alarm.getSentAt()
        );
    }
}
