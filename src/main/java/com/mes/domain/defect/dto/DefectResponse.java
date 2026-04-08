package com.mes.domain.defect.dto;

import com.mes.domain.defect.Defect;
import com.mes.domain.defect.DefectType;

import java.time.LocalDateTime;

public record DefectResponse(
        Long id,
        Long workOrderId,
        String workOrderNo,
        String equipmentId,
        String equipmentName,
        DefectType defectType,
        Integer qty,
        LocalDateTime detectedAt,
        String note
) {
    public static DefectResponse from(Defect defect) {
        return new DefectResponse(
                defect.getId(),
                defect.getWorkOrder().getId(),
                defect.getWorkOrder().getWorkOrderNo(),
                defect.getEquipment().getEquipmentId(),
                defect.getEquipment().getName(),
                defect.getDefectType(),
                defect.getQty(),
                defect.getDetectedAt(),
                defect.getNote()
        );
    }
}
