package com.mes.global.discord;

import com.mes.domain.alarm.AlarmHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordWebhookService {

    @Value("${mes.discord.alarm-cooldown-minutes}")
    private long cooldownMinutes;

    @Value("${discord.webhook-url:}")
    private String webhookUrl;

    private final WebClient.Builder webClientBuilder;
    private final AlarmHistoryService alarmHistoryService;

    /** equipmentId:metric → 마지막 알람 발송 시각 */
    private final ConcurrentHashMap<String, Instant> lastAlarmMap = new ConcurrentHashMap<>();

    public void sendAlert(String equipmentId, String metric, double value, double threshold) {
        String cooldownKey = equipmentId + ":" + metric;
        Instant lastSent = lastAlarmMap.get(cooldownKey);
        if (lastSent != null && Duration.between(lastSent, Instant.now()).compareTo(Duration.ofMinutes(cooldownMinutes)) < 0) {
            return; // 쿨다운 중 — 중복 알람 차단
        }
        lastAlarmMap.put(cooldownKey, Instant.now());

        AtomicBoolean discordSent = new AtomicBoolean(false);

        if (webhookUrl != null && !webhookUrl.isBlank()) {
            String content = String.format(
                    ":warning: **[MES 임계값 초과 알림]**\n" +
                    "- 설비: `%s`\n" +
                    "- 항목: `%s`\n" +
                    "- 현재값: `%.2f`\n" +
                    "- 임계값: `%.2f`",
                    equipmentId, metric, value, threshold
            );

            webClientBuilder.build()
                    .post()
                    .uri(webhookUrl)
                    .bodyValue(Map.of("content", content))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .doOnSuccess(v -> discordSent.set(true))
                    .doOnError(e -> log.error("Discord 알림 전송 실패: {}", e.getMessage()))
                    .doFinally(signal -> alarmHistoryService.save(equipmentId, metric, value, threshold, discordSent.get()))
                    .subscribe();
        } else {
            log.warn("Discord webhook URL이 설정되지 않았습니다. 알람 이력만 저장합니다.");
            alarmHistoryService.save(equipmentId, metric, value, threshold, false);
        }
    }
}
