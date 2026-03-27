package com.mes.domain.dashboard.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

@Getter
public class WorkOrderStatisticsDto {

    private final String equipmentId;
    private final long totalWorkOrders;
    private final long completedWorkOrders;
    private final long defectiveWorkOrders;
    private final int totalPlannedQty;
    private final int totalCompletedQty;

    @QueryProjection
    public WorkOrderStatisticsDto(String equipmentId,
                                  long totalWorkOrders,
                                  long completedWorkOrders,
                                  long defectiveWorkOrders,
                                  Integer totalPlannedQty,
                                  Integer totalCompletedQty) {
        this.equipmentId = equipmentId;
        this.totalWorkOrders = totalWorkOrders;
        this.completedWorkOrders = completedWorkOrders;
        this.defectiveWorkOrders = defectiveWorkOrders;
        this.totalPlannedQty = totalPlannedQty != null ? totalPlannedQty : 0;
        this.totalCompletedQty = totalCompletedQty != null ? totalCompletedQty : 0;
    }
}
