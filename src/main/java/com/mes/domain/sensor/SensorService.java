package com.mes.domain.sensor;

import com.mes.domain.defect.DefectType;
import com.mes.domain.equipment.EquipmentService;
import com.mes.domain.equipment.dto.EquipmentConfigResponse;
import com.mes.domain.sensor.dto.SensorDataRequest;
import com.mes.domain.workorder.WorkOrderService;
import com.mes.global.discord.DiscordWebhookService;
import com.mes.global.exception.CustomException;
import com.mes.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorService {

    private static final String SENSOR_KEY_PREFIX = "sensor:";

    @Value("${mes.sensor.ttl-seconds}")
    private long sensorTtlSeconds;

    @Value("${mes.sensor.defect-cooldown-minutes}")
    private long defectCooldownMinutes;

    private final RedisTemplate<String, Object> redisTemplate;
    private final EquipmentService equipmentService;
    private final DiscordWebhookService discordWebhookService;
    private final SseEmitterService sseEmitterService;
    private final WorkOrderService workOrderService;

    /** equipmentId:DefectType → 마지막 불량 전환 시각 */
    private final ConcurrentHashMap<String, Instant> lastDefectMap = new ConcurrentHashMap<>();

    /**
     * 센서 데이터 수신 — Redis 저장 + SSE 브로드캐스트만 수행.
     * DB 접근 없음 (equipment 존재 확인은 캐시, 상태 업데이트는 스케줄러 위임).
     */
    public void receiveSensorData(SensorDataRequest request) {
        // 설비 존재 여부 확인 (캐시 히트 시 DB 접근 없음)
        equipmentService.validateExists(request.equipmentId());

        SensorData sensorData = SensorData.builder()
                .equipmentId(request.equipmentId())
                .temperature(request.temperature())
                .vibration(request.vibration())
                .rpm(request.rpm())
                .status("NORMAL")
                .timestamp(LocalDateTime.now())
                .build();

        checkThresholds(sensorData);

        String key = SENSOR_KEY_PREFIX + request.equipmentId();
        redisTemplate.opsForValue().set(key, sensorData, Duration.ofSeconds(sensorTtlSeconds));

        sseEmitterService.broadcast(request.equipmentId(), sensorData);
    }

    private void checkThresholds(SensorData data) {
        EquipmentConfigResponse config;
        try {
            config = equipmentService.findConfig(data.getEquipmentId());
        } catch (CustomException e) {
            return; // 설정 없으면 임계값 검사 스킵
        }
        if (data.getTemperature() > config.maxTemperature()) {
            data.setStatus("FAULT");
            discordWebhookService.sendAlert(data.getEquipmentId(), "온도",
                    data.getTemperature(), config.maxTemperature());
            triggerAutoDefect(data.getEquipmentId(), DefectType.TEMPERATURE);
        }
        if (data.getVibration() > config.maxVibration()) {
            data.setStatus("FAULT");
            discordWebhookService.sendAlert(data.getEquipmentId(), "진동",
                    data.getVibration(), config.maxVibration());
            triggerAutoDefect(data.getEquipmentId(), DefectType.VIBRATION);
        }
        if (data.getRpm() > config.maxRpm()) {
            data.setStatus("FAULT");
            discordWebhookService.sendAlert(data.getEquipmentId(), "RPM",
                    data.getRpm(), config.maxRpm());
            triggerAutoDefect(data.getEquipmentId(), DefectType.RPM);
        }
    }

    private void triggerAutoDefect(String equipmentId, DefectType defectType) {
        String key = equipmentId + ":" + defectType.name();
        Instant last = lastDefectMap.get(key);
        if (last != null && Duration.between(last, Instant.now()).toMinutes() < defectCooldownMinutes) {
            return; // 쿨다운 중 — 스킵
        }
        lastDefectMap.put(key, Instant.now());
        try {
            workOrderService.autoMarkDefective(equipmentId, defectType);
        } catch (Exception e) {
            lastDefectMap.remove(key); // 실패 시 쿨다운 초기화
            log.error("자동 불량 처리 실패 equipmentId={} type={}: {}", equipmentId, defectType, e.getMessage());
        }
    }

    public SensorData getLatest(String equipmentId) {
        String key = SENSOR_KEY_PREFIX + equipmentId;
        Object val = redisTemplate.opsForValue().get(key);
        if (val instanceof SensorData data) {
            return data;
        }
        throw new CustomException(ErrorCode.SENSOR_DATA_NOT_FOUND);
    }

    public Map<String, Object> getAllSensorKeys() {
        var keys = redisTemplate.keys(SENSOR_KEY_PREFIX + "*");
        if (keys == null) return Map.of();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (String key : keys) {
            result.put(key, redisTemplate.opsForValue().get(key));
        }
        return result;
    }
}
