package com.mes.domain.defect.dto;

import com.mes.domain.defect.Defect;
import com.mes.domain.defect.DefectType;

import java.time.LocalDateTime;

public record DefectResponse(
        Long id,
        Long workOrderId,
        String equipmentId,
        DefectType defectType,
        Integer qty,
        LocalDateTime detectedAt,
        String note
) {
    public static DefectResponse from(Defect defect) {
        return new DefectResponse(
                defect.getId(),
                defect.getWorkOrder().getId(),
                defect.getEquipment().getEquipmentId(),
                defect.getDefectType(),
                defect.getQty(),
                defect.getDetectedAt(),
                defect.getNote()
        );
    }
}
