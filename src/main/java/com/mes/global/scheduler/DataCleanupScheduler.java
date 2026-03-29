package com.mes.global.scheduler;

import com.mes.domain.sensor.SensorHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleanupScheduler {

    @Value("${mes.cleanup.retention-days}")
    private int retentionDays;

    private final SensorHistoryRepository sensorHistoryRepository;

    /**
     * 매일 03:00 — 소프트 딜리트 후 N일 경과한 SensorHistory 하드 삭제
     */
    @Scheduled(cron = "${mes.cleanup.hard-delete-cron}")
    @Transactional
    public void hardDeleteOldSensorHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = sensorHistoryRepository.hardDeleteBefore(cutoff);
        log.info("SensorHistory 하드 삭제 완료: {}건 (기준일: {})", deleted, cutoff);
    }

    /**
     * 매일 02:00 — 삭제된 Equipment에 속한 SensorHistory 소프트 딜리트
     */
    @Scheduled(cron = "${mes.cleanup.soft-delete-cron}")
    @Transactional
    public void softDeleteOrphanedSensorHistory() {
        LocalDateTime now = LocalDateTime.now();
        int updated = sensorHistoryRepository.softDeleteOrphanedByEquipment(now);
        log.info("고아 SensorHistory 소프트 딜리트 완료: {}건", updated);
    }
}
