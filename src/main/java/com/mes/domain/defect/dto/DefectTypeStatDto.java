package com.mes.domain.defect.dto;

import com.mes.domain.defect.DefectType;
import com.querydsl.core.annotations.QueryProjection;

public record DefectTypeStatDto(
        DefectType defectType,
        long count,
        long totalQty
) {
    @QueryProjection
    public DefectTypeStatDto {}
}
