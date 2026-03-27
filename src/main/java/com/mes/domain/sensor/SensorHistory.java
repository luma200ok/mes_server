package com.mes.domain.sensor;

import com.mes.domain.equipment.Equipment;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "sensor_history", indexes = {
        @Index(name = "idx_sensor_history_equipment", columnList = "equipment_id"),
        @Index(name = "idx_sensor_history_recorded_at", columnList = "recorded_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE sensor_history SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class SensorHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(nullable = false)
    private Double avgTemperature;

    @Column(nullable = false)
    private Double avgVibration;

    @Column(nullable = false)
    private Double avgRpm;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    private LocalDateTime deletedAt;

    @Builder
    public SensorHistory(Equipment equipment, Double avgTemperature, Double avgVibration, Double avgRpm, LocalDateTime recordedAt) {
        this.equipment = equipment;
        this.avgTemperature = avgTemperature;
        this.avgVibration = avgVibration;
        this.avgRpm = avgRpm;
        this.recordedAt = recordedAt;
    }
}
