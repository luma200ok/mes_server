package com.mes.domain.alarm;

import com.mes.domain.alarm.dto.AlarmHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AlarmHistoryService {

    private final AlarmHistoryRepository alarmHistoryRepository;

    @Transactional
    public AlarmHistory save(String equipmentId, String metric, double currentValue, double threshold, boolean discordSent) {
        return alarmHistoryRepository.save(AlarmHistory.builder()
                .equipmentId(equipmentId)
                .metric(metric)
                .currentValue(currentValue)
                .threshold(threshold)
                .discordSent(discordSent)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<AlarmHistoryResponse> getByEquipment(String equipmentId, Pageable pageable) {
        return alarmHistoryRepository
                .findByEquipmentIdOrderBySentAtDesc(equipmentId, pageable)
                .map(AlarmHistoryResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<AlarmHistoryResponse> getByPeriod(LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return alarmHistoryRepository
                .findBySentAtBetweenOrderBySentAtDesc(from, to, pageable)
                .map(AlarmHistoryResponse::from);
    }

    @Transactional(readOnly = true)
    public long countRecent(String equipmentId, LocalDateTime since) {
        return alarmHistoryRepository.countByEquipmentIdAndSentAtAfter(equipmentId, since);
    }
}
