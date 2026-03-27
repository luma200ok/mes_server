package com.mes.domain.equipment;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "equipment_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false, unique = true)
    private Equipment equipment;

    @Column(nullable = false)
    private Double maxTemperature;

    @Column(nullable = false)
    private Double maxVibration;

    @Column(nullable = false)
    private Double maxRpm;

    @Builder
    public EquipmentConfig(Equipment equipment, Double maxTemperature, Double maxVibration, Double maxRpm) {
        this.equipment = equipment;
        this.maxTemperature = maxTemperature;
        this.maxVibration = maxVibration;
        this.maxRpm = maxRpm;
    }

    public void update(Double maxTemperature, Double maxVibration, Double maxRpm) {
        if (maxTemperature != null) this.maxTemperature = maxTemperature;
        if (maxVibration != null) this.maxVibration = maxVibration;
        if (maxRpm != null) this.maxRpm = maxRpm;
    }
}
