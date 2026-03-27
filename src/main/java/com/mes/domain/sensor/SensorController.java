package com.mes.domain.sensor;

import com.mes.domain.sensor.dto.SensorDataRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Sensor", description = "센서 데이터 API")
@RestController
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;
    private final SseEmitterService sseEmitterService;

    @Operation(summary = "센서 데이터 수신 (시뮬레이터용, 인증 불필요)")
    @PostMapping("/api/sensor/data")
    public ResponseEntity<Void> receiveSensorData(@Valid @RequestBody SensorDataRequest request) {
        sensorService.receiveSensorData(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "SSE 실시간 모니터링 구독")
    @GetMapping(value = "/api/sse/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam(required = false) String equipmentId) {
        return sseEmitterService.subscribe(equipmentId);
    }
}
