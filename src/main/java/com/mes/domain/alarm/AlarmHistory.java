package com.mes.domain.alarm;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "alarm_history", indexes = {
        @Index(name = "idx_alarm_equipment_id", columnList = "equipment_id"),
        @Index(name = "idx_alarm_sent_at", columnList = "sent_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AlarmHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false, length = 50)
    private String equipmentId;

    @Column(nullable = false, length = 20)
    private String metric;        // 온도 / 진동 / RPM

    @Column(nullable = false)
    private Double currentValue;

    @Column(nullable = false)
    private Double threshold;

    @Column(nullable = false)
    private Boolean discordSent;  // Discord 전송 성공 여부

    @CreationTimestamp
    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt;
}
