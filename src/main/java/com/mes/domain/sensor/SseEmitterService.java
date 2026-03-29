package com.mes.domain.sensor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class SseEmitterService {

    @Value("${mes.sse.timeout-ms}")
    private long timeoutMs;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private static final String ALL = "__ALL__";

    public SseEmitter subscribe(String equipmentId) {
        String key = (equipmentId == null || equipmentId.isBlank()) ? ALL : equipmentId;
        SseEmitter emitter = new SseEmitter(timeoutMs);
        emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            remove(key, emitter);
        });
        emitter.onError(e -> remove(key, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("connected to " + key));
        } catch (IOException e) {
            log.warn("SSE 초기 전송 실패: {}", e.getMessage());
        }

        return emitter;
    }

    public void broadcast(String equipmentId, Object data) {
        sendToGroup(equipmentId, data);
        sendToGroup(ALL, data);  // 전체 구독자에게도 전송
    }

    private void sendToGroup(String key, Object data) {
        List<SseEmitter> list = emitters.get(key);
        if (list == null) return;

        list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("sensorData").data(data));
            } catch (IOException e) {
                remove(key, emitter);
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        });
    }

    private void remove(String equipmentId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(equipmentId);
        if (list != null) {
            list.remove(emitter);
        }
    }
}
