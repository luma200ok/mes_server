package com.mes.domain.workorder;

import com.mes.global.exception.CustomException;
import com.mes.global.exception.ErrorCode;

import java.util.Set;

public enum WorkOrderStatus {
    PENDING, IN_PROGRESS, COMPLETED, DEFECTIVE;

    public void validateTransitionTo(WorkOrderStatus next) {
        Set<WorkOrderStatus> allowed = switch (this) {
            case PENDING -> Set.of(IN_PROGRESS);
            case IN_PROGRESS -> Set.of(COMPLETED, DEFECTIVE);
            case COMPLETED, DEFECTIVE -> Set.of();
        };
        if (!allowed.contains(next)) {
            throw new CustomException(ErrorCode.WORK_ORDER_INVALID_STATUS_TRANSITION);
        }
    }
}
