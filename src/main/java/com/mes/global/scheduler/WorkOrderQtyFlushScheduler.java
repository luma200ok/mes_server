package com.mes.global.scheduler;

import com.mes.domain.workorder.WorkOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Redis WO 카운터 → DB 반영 스케줄러.
 * 매 1분(aggregation-delay-ms)마다 wo:good:* / wo:defect:* 키를 읽어
 * WorkOrder.goodQty / defectQty 를 일괄 업데이트하고 Redis 키를 삭제.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderQtyFlushScheduler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final WorkOrderService workOrderService;

    @Scheduled(fixedDelayString = "${mes.sensor.aggregation-delay-ms:60000}")
    public void flush() {
        Set<String> goodKeys = redisTemplate.keys(WorkOrderService.WO_GOOD_PREFIX + "*");
        if (goodKeys == null || goodKeys.isEmpty()) return;

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
}
