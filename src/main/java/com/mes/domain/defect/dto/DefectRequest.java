package com.mes.domain.defect.dto;

import com.mes.domain.defect.DefectType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DefectRequest(
        @NotNull Long workOrderId,
        @NotNull DefectType defectType,
        @NotNull @Min(1) Integer qty,
        String note
) {}
