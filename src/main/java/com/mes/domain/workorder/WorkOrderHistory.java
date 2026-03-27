package com.mes.domain.workorder;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "work_order_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkOrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderStatus toStatus;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    @Column(length = 100)
    private String changedBy;

    @Builder
    public WorkOrderHistory(WorkOrder workOrder, WorkOrderStatus fromStatus, WorkOrderStatus toStatus, String changedBy) {
        this.workOrder = workOrder;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = LocalDateTime.now();
        this.changedBy = changedBy;
    }
}
