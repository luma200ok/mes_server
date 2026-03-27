package com.mes.domain.workorder.dto;

import com.mes.domain.workorder.WorkOrderStatus;
import jakarta.validation.constraints.NotNull;

public record WorkOrderStatusRequest(
        @NotNull WorkOrderStatus status,
        Integer completedQty
) {}
