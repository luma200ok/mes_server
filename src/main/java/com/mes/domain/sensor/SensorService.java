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

    @Value("${mes.workorder.production-interval-seconds}")
    private long productionIntervalSeconds;

    // 설비별 마지막 생산 카운트 시각 (시간당 40개 속도 제어)
    private final ConcurrentHashMap<String, LocalDateTime> lastProductionTime = new ConcurrentHashMap<>();

    private final RedisTemplate<String, Object> redisTemplate;
    private final EquipmentService equipmentService;
    private final DiscordWebhookService discordWebhookService;
    private final SseEmitterService sseEmitterService;
    private final WorkOrderService workOrderService;

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

    /**
     * 임계값 판정 + 작업지시 수량 카운팅.
     * 센서 1회 수신 = 생산 1단위.
     * 하나라도 임계값 초과 → 불량 수량 +1 (첫 번째 초과 타입으로 Defect 저장)
     * 모두 정상 → 양품 수량 +1
     */
    private void checkThresholds(SensorData data) {
        EquipmentConfigResponse config;
        try {
            config = equipmentService.findConfig(data.getEquipmentId());
        } catch (CustomException e) {
            return; // 설정 없으면 임계값 검사 스킵
        }

        DefectType faultType = null;

        if (data.getTemperature() > config.maxTemperature()) {
            data.setStatus("FAULT");
            discordWebhookService.sendAlert(data.getEquipmentId(), "온도",
                    data.getTemperature(), config.maxTemperature());
            if (faultType == null) faultType = DefectType.TEMPERATURE;
        }
        if (data.getVibration() > config.maxVibration()) {
            data.setStatus("FAULT");
            discordWebhookService.sendAlert(data.getEquipmentId(), "진동",
                    data.getVibration(), config.maxVibration());
            if (faultType == null) faultType = DefectType.VIBRATION;
        }
        if (data.getRpm() > config.maxRpm()) {
            data.setStatus("FAULT");
            discordWebhookService.sendAlert(data.getEquipmentId(), "RPM",
                    data.getRpm(), config.maxRpm());
            if (faultType == null) faultType = DefectType.RPM;
        }

        // 생산 인터벌(기본 90초)마다 1단위 카운팅 → 시간당 약 40개
        LocalDateTime now  = LocalDateTime.now();
        LocalDateTime last = lastProductionTime.get(data.getEquipmentId());
        if (last == null || java.time.Duration.between(last, now).getSeconds() >= productionIntervalSeconds) {
            lastProductionTime.put(data.getEquipmentId(), now);
            if (faultType != null) {
                workOrderService.recordDefectCount(data.getEquipmentId(), faultType);
            } else {
                workOrderService.recordGoodCount(data.getEquipmentId());
            }
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
