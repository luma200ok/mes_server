package com.mes.domain.sensor;

import com.mes.domain.equipment.EquipmentConfigRepository;
import com.mes.domain.equipment.EquipmentRepository;
import com.mes.domain.equipment.dto.EquipmentConfigResponse;
import com.mes.domain.sensor.dto.SensorDataRequest;
import com.mes.global.discord.DiscordWebhookService;
import com.mes.global.exception.CustomException;
import com.mes.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorService {

    private static final String SENSOR_KEY_PREFIX = "sensor:";
    private static final Duration SENSOR_TTL = Duration.ofSeconds(60);

    private final RedisTemplate<String, Object> redisTemplate;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentConfigRepository configRepository;
    private final DiscordWebhookService discordWebhookService;
    private final SseEmitterService sseEmitterService;

    public void receiveSensorData(SensorDataRequest request) {
        if (!equipmentRepository.existsByEquipmentId(request.equipmentId())) {
            throw new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND);
        }

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
        redisTemplate.opsForValue().set(key, sensorData, SENSOR_TTL);

        sseEmitterService.broadcast(request.equipmentId(), sensorData);
    }

    private void checkThresholds(SensorData data) {
        configRepository.findByEquipment_EquipmentId(data.getEquipmentId()).ifPresent(config -> {
            if (data.getTemperature() > config.getMaxTemperature()) {
                data.setStatus("FAULT");
                discordWebhookService.sendAlert(data.getEquipmentId(), "온도",
                        data.getTemperature(), config.getMaxTemperature());
            }
            if (data.getVibration() > config.getMaxVibration()) {
                data.setStatus("FAULT");
                discordWebhookService.sendAlert(data.getEquipmentId(), "진동",
                        data.getVibration(), config.getMaxVibration());
            }
            if (data.getRpm() > config.getMaxRpm()) {
                data.setStatus("FAULT");
                discordWebhookService.sendAlert(data.getEquipmentId(), "RPM",
                        data.getRpm(), config.getMaxRpm());
            }
        });
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
