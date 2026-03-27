package com.mes.global.scheduler;

import com.mes.domain.sensor.SensorHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleanupScheduler {

    private static final int RETENTION_DAYS = 30;

    private final SensorHistoryRepository sensorHistoryRepository;

    /**
     * 매일 03:00 — 소프트 딜리트 후 30일 경과한 SensorHistory 하드 삭제
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void hardDeleteOldSensorHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = sensorHistoryRepository.hardDeleteBefore(cutoff);
        log.info("SensorHistory 하드 삭제 완료: {}건 (기준일: {})", deleted, cutoff);
    }

    /**
     * 매일 02:00 — 삭제된 Equipment에 속한 SensorHistory 소프트 딜리트
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void softDeleteOrphanedSensorHistory() {
        LocalDateTime now = LocalDateTime.now();
        int updated = sensorHistoryRepository.softDeleteOrphanedByEquipment(now);
        log.info("고아 SensorHistory 소프트 딜리트 완료: {}건", updated);
    }
}
