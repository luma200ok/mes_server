package com.mes.global.scheduler;

import com.mes.domain.equipment.Equipment;
import com.mes.domain.equipment.EquipmentRepository;
import com.mes.domain.equipment.EquipmentStatus;
import com.mes.domain.sensor.SensorData;
import com.mes.domain.sensor.SensorHistory;
import com.mes.domain.sensor.SensorHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorAggregationScheduler {

    private static final String SENSOR_KEY_PREFIX = "sensor:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final EquipmentRepository equipmentRepository;
    private final SensorHistoryRepository sensorHistoryRepository;

    @Scheduled(fixedDelayString = "${mes.sensor.aggregation-delay-ms}")
    @Transactional
    public void aggregateAndPersist() {
        Set<String> keys = redisTemplate.keys(SENSOR_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        // 설비 전체 1회 조회 후 Map으로 캐싱 (N+1 방지)
        Map<String, Equipment> equipmentMap = equipmentRepository.findAll().stream()
                .collect(Collectors.toMap(Equipment::getEquipmentId, e -> e));

        int saved = 0;
        for (String key : keys) {
            try {
                Object raw = redisTemplate.opsForValue().get(key);
                if (!(raw instanceof SensorData data)) continue;

                String equipmentId = data.getEquipmentId();
                Equipment equipment = equipmentMap.get(equipmentId);
                if (equipment == null) {
                    redisTemplate.delete(key);
                    continue;
                }

                SensorHistory history = SensorHistory.builder()
                        .equipment(equipment)
                        .avgTemperature(data.getTemperature())
                        .avgVibration(data.getVibration())
                        .avgRpm(data.getRpm())
                        .recordedAt(LocalDateTime.now())
                        .build();

                sensorHistoryRepository.save(history);

                // equipment 상태 업데이트 (DB 쓰기는 스케줄러에서만)
                EquipmentStatus newStatus = "FAULT".equals(data.getStatus())
                        ? EquipmentStatus.FAULT
                        : EquipmentStatus.RUNNING;
                if (equipment.getStatus() != newStatus) {
                    equipment.updateStatus(newStatus);
                }

                redisTemplate.delete(key);
                saved++;

            } catch (Exception e) {
                log.error("센서 집계 실패 key={}: {}", key, e.getMessage());
            }
        }

        if (saved > 0) {
            log.info("센서 데이터 집계 완료: {}건 MySQL 저장", saved);
        }
    }
}
