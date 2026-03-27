package com.mes.domain.defect;

import com.mes.domain.equipment.Equipment;
import com.mes.domain.workorder.WorkOrder;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "defect")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Defect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DefectType defectType;

    @Column(nullable = false)
    private Integer qty;

    @Column(nullable = false)
    private LocalDateTime detectedAt;

    @Column(length = 500)
    private String note;

    @Builder
    public Defect(WorkOrder workOrder, Equipment equipment, DefectType defectType, Integer qty, String note) {
        this.workOrder = workOrder;
        this.equipment = equipment;
        this.defectType = defectType;
        this.qty = qty;
        this.note = note;
        this.detectedAt = LocalDateTime.now();
    }
}
