package com.mes.global.scheduler;

import com.mes.domain.workorder.WorkOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis WO 카운터 → DB 반영 스케줄러.
 * 매 1분(aggregation-delay-ms)마다 wo:good:* 키를 SCAN으로 탐색하여
 * WorkOrder.goodQty / defectQty 를 일괄 업데이트하고 Redis 키를 삭제.
 *
 * ※ KEYS 커맨드 대신 SCAN을 사용하여 프로덕션 환경 블로킹 위험 제거.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderQtyFlushScheduler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final WorkOrderService workOrderService;

    @Scheduled(fixedDelayString = "${mes.sensor.aggregation-delay-ms:60000}")
    public void flush() {
        List<String> goodKeys = scanKeys(WorkOrderService.WO_GOOD_PREFIX + "*");
        if (goodKeys.isEmpty()) return;

        int flushed = 0;
        for (String key : goodKeys) {
            try {
                Long woId = Long.parseLong(key.replace(WorkOrderService.WO_GOOD_PREFIX, ""));
                workOrderService.flushQtyCounts(woId);
                flushed++;
            } catch (Exception e) {
                log.error("[WO 카운터 플러시] WO 처리 실패 key={}: {}", key, e.getMessage());
            }
        }
        log.debug("[WO 카운터 플러시] {}건 DB 반영 완료", flushed);
    }

    /**
     * Redis SCAN으로 패턴에 매칭되는 키 목록을 비블로킹 방식으로 수집.
     * KEYS 커맨드는 O(N) 블로킹 → 운영 환경에서 Redis 레이턴시 스파이크 유발 가능.
     */
    private List<String> scanKeys(String pattern) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        List<String> keys = new ArrayList<>();

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                if (cursor != null) {
                    cursor.forEachRemaining(k -> keys.add(new String(k, StandardCharsets.UTF_8)));
                }
            } catch (Exception e) {
                log.error("[WO 카운터 플러시] Redis SCAN 실패: {}", e.getMessage());
            }
            return null;
        });

        return keys;
    }
}
