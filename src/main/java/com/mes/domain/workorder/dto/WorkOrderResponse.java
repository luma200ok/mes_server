package com.mes.domain.workorder.dto;

import com.mes.domain.workorder.WorkOrder;
import com.mes.domain.workorder.WorkOrderStatus;

import java.time.LocalDateTime;

public record WorkOrderResponse(
        Long id,
        String workOrderNo,
        String equipmentId,
        WorkOrderStatus status,
        Integer plannedQty,
        Integer completedQty,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {
    public static WorkOrderResponse from(WorkOrder wo) {
        return new WorkOrderResponse(
                wo.getId(),
                wo.getWorkOrderNo(),
                wo.getEquipment().getEquipmentId(),
                wo.getStatus(),
                wo.getPlannedQty(),
                wo.getCompletedQty(),
                wo.getStartedAt(),
                wo.getCompletedAt(),
                wo.getCreatedAt()
        );
    }
}
