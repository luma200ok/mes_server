package com.mes.domain.workorder;

import com.mes.domain.equipment.Equipment;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "work_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE work_order SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String workOrderNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderStatus status;

    @Column(nullable = false)
    private Integer plannedQty;

    private Integer completedQty;

    @Column(nullable = false)
    private int goodQty = 0;

    @Column(nullable = false)
    private int defectQty = 0;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = WorkOrderStatus.PENDING;
        }
    }

    @Builder
    public WorkOrder(String workOrderNo, Equipment equipment, Integer plannedQty) {
        this.workOrderNo = workOrderNo;
        this.equipment = equipment;
        this.plannedQty = plannedQty;
        this.status = WorkOrderStatus.PENDING;
    }

    public void transitionTo(WorkOrderStatus newStatus, Integer completedQty) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;

        if (newStatus == WorkOrderStatus.IN_PROGRESS) {
            this.startedAt = LocalDateTime.now();
        }
        if (newStatus == WorkOrderStatus.COMPLETED || newStatus == WorkOrderStatus.DEFECTIVE) {
            this.completedAt = LocalDateTime.now();
            if (completedQty != null) {
                this.completedQty = completedQty;
            }
        }
    }

    public void markDefective() {
        if (this.status == WorkOrderStatus.IN_PROGRESS || this.status == WorkOrderStatus.COMPLETED) {
            this.status = WorkOrderStatus.DEFECTIVE;
            if (this.completedAt == null) {
                this.completedAt = LocalDateTime.now();
            }
        }
    }

    public void addGoodQty(int qty) {
        this.goodQty += qty;
    }

    public void addDefectQty(int qty) {
        this.defectQty += qty;
    }

    /** 양품 + 불량이 계획 수량에 도달했는지 여부 */
    public boolean isComplete() {
        return this.goodQty + this.defectQty >= this.plannedQty;
    }
}
