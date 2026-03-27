package com.mes.global.scheduler;

import com.mes.domain.equipment.Equipment;
import com.mes.domain.equipment.EquipmentRepository;
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
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorAggregationScheduler {

    private static final String SENSOR_KEY_PREFIX = "sensor:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final EquipmentRepository equipmentRepository;
    private final SensorHistoryRepository sensorHistoryRepository;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void aggregateAndPersist() {
        Set<String> keys = redisTemplate.keys(SENSOR_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        int saved = 0;
        for (String key : keys) {
            try {
                Object raw = redisTemplate.opsForValue().get(key);
                if (!(raw instanceof SensorData data)) continue;

                String equipmentId = data.getEquipmentId();
                Equipment equipment = equipmentRepository.findByEquipmentId(equipmentId).orElse(null);
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
