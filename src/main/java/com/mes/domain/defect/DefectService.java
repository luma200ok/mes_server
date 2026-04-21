package com.mes.domain.defect;

import com.mes.domain.defect.dto.DefectRequest;
import com.mes.domain.defect.dto.DefectResponse;
import com.mes.domain.defect.dto.DefectSummaryResponse;
import com.mes.domain.defect.dto.DefectTypeStatDto;
import com.mes.domain.workorder.WorkOrder;
import com.mes.domain.workorder.WorkOrderHistoryRepository;
import com.mes.domain.workorder.WorkOrderHistory;
import com.mes.domain.workorder.WorkOrderRepository;
import com.mes.domain.workorder.WorkOrderStatus;
import com.mes.global.exception.CustomException;
import com.mes.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefectService {

    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderHistoryRepository workOrderHistoryRepository;

    public Page<DefectResponse> findFiltered(LocalDate startDate, LocalDate endDate,
                                             DefectType defectType, String equipmentId,
                                             int page, int size) {
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(30);
        if (start.isAfter(end)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return defectRepository.findFiltered(start, end, defectType, equipmentId,
                        PageRequest.of(page, size))
                .map(DefectResponse::from);
    }

    public DefectSummaryResponse getSummary(LocalDate startDate, LocalDate endDate, String equipmentId) {
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(30);
        if (start.isAfter(end)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // DB에서 직접 집계 — OOM 없이 단일 쿼리
        List<DefectTypeStatDto> stats = defectRepository.aggregateByType(start, end, equipmentId);

        Map<DefectType, DefectTypeStatDto> statMap = stats.stream()
                .collect(Collectors.toMap(DefectTypeStatDto::defectType, s -> s));

        long totalCount = stats.stream().mapToLong(DefectTypeStatDto::count).sum();
        long totalQty   = stats.stream().mapToLong(DefectTypeStatDto::totalQty).sum();

        // 모든 DefectType 포함 (0건 포함)
        Map<String, Long> countByType = new LinkedHashMap<>();
        Map<String, Long> qtyByType   = new LinkedHashMap<>();
        for (DefectType type : DefectType.values()) {
            DefectTypeStatDto stat = statMap.get(type);
            countByType.put(type.name(), stat != null ? stat.count()    : 0L);
            qtyByType.put(type.name(),   stat != null ? stat.totalQty() : 0L);
        }

        return new DefectSummaryResponse(totalCount, totalQty, countByType, qtyByType);
    }

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
        if (workOrder.getGoodQty() + totalDefect > workOrder.getPlannedQty()) {
            throw new CustomException(ErrorCode.DEFECT_QTY_EXCEEDS_PLANNED);
        }

        Defect defect = Defect.builder()
                .workOrder(workOrder)
                .equipment(workOrder.getEquipment())
                .defectType(request.defectType())
                .qty(request.qty())
                .note(request.note())
                .build();

        defectRepository.save(defect);
        workOrder.addDefectQty(request.qty());  // WorkOrder.defectQty 동기화

        if (workOrder.getStatus() == WorkOrderStatus.IN_PROGRESS
                || workOrder.getStatus() == WorkOrderStatus.COMPLETED) {
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
