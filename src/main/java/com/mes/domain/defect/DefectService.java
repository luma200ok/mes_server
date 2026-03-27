package com.mes.domain.defect;

import com.mes.domain.defect.dto.DefectRequest;
import com.mes.domain.defect.dto.DefectResponse;
import com.mes.domain.workorder.WorkOrder;
import com.mes.domain.workorder.WorkOrderHistoryRepository;
import com.mes.domain.workorder.WorkOrderHistory;
import com.mes.domain.workorder.WorkOrderRepository;
import com.mes.domain.workorder.WorkOrderStatus;
import com.mes.global.exception.CustomException;
import com.mes.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefectService {

    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderHistoryRepository workOrderHistoryRepository;

    public List<DefectResponse> findByWorkOrder(Long workOrderId) {
        return defectRepository.findByWorkOrder_Id(workOrderId).stream()
                .map(DefectResponse::from)
                .toList();
    }

    @Transactional
    public DefectResponse register(DefectRequest request) {
        WorkOrder workOrder = workOrderRepository.findById(request.workOrderId())
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_ORDER_NOT_FOUND));

        int totalDefect = defectRepository.sumQtyByWorkOrderId(workOrder.getId()) + request.qty();
        if (workOrder.getCompletedQty() != null && totalDefect > workOrder.getCompletedQty()) {
            throw new CustomException(ErrorCode.DEFECT_QTY_EXCEEDS_COMPLETED);
        }

        Defect defect = Defect.builder()
                .workOrder(workOrder)
                .equipment(workOrder.getEquipment())
                .defectType(request.defectType())
                .qty(request.qty())
                .note(request.note())
                .build();

        defectRepository.save(defect);

        // WorkOrder 자동 DEFECTIVE 전이
        if (workOrder.getStatus() == WorkOrderStatus.IN_PROGRESS) {
            WorkOrderStatus fromStatus = workOrder.getStatus();
            workOrder.markDefective();
            workOrderHistoryRepository.save(WorkOrderHistory.builder()
                    .workOrder(workOrder)
                    .fromStatus(fromStatus)
                    .toStatus(WorkOrderStatus.DEFECTIVE)
                    .changedBy("SYSTEM")
                    .build());
        }

        return DefectResponse.from(defect);
    }
}
