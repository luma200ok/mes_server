package com.mes.domain.defect.dto;

import com.mes.domain.defect.DefectType;

import java.util.Map;

public record DefectSummaryResponse(
        long totalCount,
        long totalQty,
        Map<String, Long> countByType,
        Map<String, Long> qtyByType
) {}
