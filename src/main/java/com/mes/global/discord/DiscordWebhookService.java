package com.mes.global.discord;

import com.mes.domain.alarm.AlarmHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordWebhookService {

    @Value("${discord.webhook-url:}")
    private String webhookUrl;

    private final WebClient.Builder webClientBuilder;
    private final AlarmHistoryService alarmHistoryService;

    public void sendAlert(String equipmentId, String metric, double value, double threshold) {
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
