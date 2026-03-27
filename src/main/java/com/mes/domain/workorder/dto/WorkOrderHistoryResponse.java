package com.mes.domain.workorder.dto;

import com.mes.domain.workorder.WorkOrderHistory;
import com.mes.domain.workorder.WorkOrderStatus;

import java.time.LocalDateTime;

public record WorkOrderHistoryResponse(
        Long id,
        WorkOrderStatus fromStatus,
        WorkOrderStatus toStatus,
        LocalDateTime changedAt,
        String changedBy
) {
    public static WorkOrderHistoryResponse from(WorkOrderHistory history) {
        return new WorkOrderHistoryResponse(
                history.getId(),
                history.getFromStatus(),
                history.getToStatus(),
                history.getChangedAt(),
                history.getChangedBy()
        );
    }
}
