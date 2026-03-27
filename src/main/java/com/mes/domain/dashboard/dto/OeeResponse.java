package com.mes.domain.dashboard.dto;

public record OeeResponse(
        String equipmentId,
        String date,
        double availability,
        double performance,
        double quality,
        double oee,
        long totalWorkOrders,
        long completedWorkOrders,
        long defectiveWorkOrders,
        int totalPlannedQty,
        int totalCompletedQty,
        int totalDefectQty
) {}
