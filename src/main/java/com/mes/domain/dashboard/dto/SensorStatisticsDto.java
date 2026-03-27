package com.mes.domain.dashboard.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

@Getter
public class SensorStatisticsDto {

    private final String equipmentId;
    private final int totalDefectQty;

    @QueryProjection
    public SensorStatisticsDto(String equipmentId, Integer totalDefectQty) {
        this.equipmentId = equipmentId;
        this.totalDefectQty = totalDefectQty != null ? totalDefectQty : 0;
    }
}
