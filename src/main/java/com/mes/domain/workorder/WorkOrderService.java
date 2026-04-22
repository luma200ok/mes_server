package com.mes.domain.workorder;

import com.mes.domain.defect.Defect;
import com.mes.domain.defect.DefectRepository;
import com.mes.domain.defect.DefectType;
import com.mes.domain.equipment.Equipment;
import com.mes.domain.equipment.EquipmentRepository;
import com.mes.domain.workorder.dto.*;
import com.mes.global.exception.CustomException;
import com.mes.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkOrderService {

    // ── Redis 키 상수 ─────────────────────────────────
    public static final String WO_ACTIVE_PREFIX  = "wo:active:";   // 설비별 활성 WO ID
    public static final String WO_GOOD_PREFIX    = "wo:good:";     // WO별 양품 카운터
    public static final String WO_DEFECT_PREFIX  = "wo:defect:";   // WO별 불량 카운터
    public static final String WO_DEFECTS_PREFIX = "wo:defects:";  // WO별 불량 타입 로그 (List)
    // ─────────────────────────────────────────────────

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderHistoryRepository historyRepository;
    private final EquipmentRepository equipmentRepository;
    private final DefectRepository defectRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final WorkOrderNoGenerator noGenerator;
    private final WorkOrderExcelService excelService;

    public List<WorkOrderResponse> findFiltered(LocalDate startDate, LocalDate endDate, WorkOrderStatus status) {
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(30);
        return workOrderRepository.findByDateRange(start, end, status).stream()
                .map(WorkOrderResponse::from)
                .toList();
    }

    public Map<String, List<WorkOrderResponse>> findGroupedByDate(LocalDate startDate, LocalDate endDate) {
        LocalDate end   = endDate   != null ? endDate   : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(30);
        if (start.isAfter(end)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        List<WorkOrder> workOrders = workOrderRepository.findByDateRange(start, end, null);
        TreeMap<String, List<WorkOrderResponse>> grouped = new TreeMap<>(java.util.Comparator.reverseOrder());
        for (WorkOrder wo : workOrders) {
            String dateKey = wo.getCreatedAt().toLocalDate().toString();
            grouped.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(WorkOrderResponse.from(wo));
        }
        return grouped;
    }

    @Transactional
    public WorkOrderResponse create(WorkOrderRequest request) {
        Equipment equipment = equipmentRepository.findByEquipmentId(request.equipmentId())
                .orElseThrow(() -> new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND));

        WorkOrder workOrder = WorkOrder.builder()
                .workOrderNo(noGenerator.generate())
                .equipment(equipment)
                .plannedQty(request.plannedQty())
                .build();

        return WorkOrderResponse.from(workOrderRepository.save(workOrder));
    }

    @Transactional
    public WorkOrderResponse changeStatus(Long id, WorkOrderStatusRequest request) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_ORDER_NOT_FOUND));

        WorkOrderStatus fromStatus = workOrder.getStatus();
        workOrder.transitionTo(request.status(), request.completedQty());

        String changedBy = getCurrentUsername();
        historyRepository.save(WorkOrderHistory.builder()
                .workOrder(workOrder)
                .fromStatus(fromStatus)
                .toStatus(request.status())
                .changedBy(changedBy)
                .build());

        // Redis active 키 갱신
        String equipmentId = workOrder.getEquipment().getEquipmentId();
        if (request.status() == WorkOrderStatus.IN_PROGRESS) {
            redisTemplate.opsForValue().set(WO_ACTIVE_PREFIX + equipmentId, workOrder.getId());
        } else if (request.status() == WorkOrderStatus.COMPLETED || request.status() == WorkOrderStatus.DEFECTIVE) {
            redisTemplate.delete(WO_ACTIVE_PREFIX + equipmentId);
        }

        return WorkOrderResponse.from(workOrder);
    }

    public List<WorkOrderHistoryResponse> findHistory(Long id) {
        if (!workOrderRepository.existsById(id)) {
            throw new CustomException(ErrorCode.WORK_ORDER_NOT_FOUND);
        }
        return historyRepository.findByWorkOrder_IdOrderByChangedAtAsc(id).stream()
                .map(WorkOrderHistoryResponse::from)
                .toList();
    }

    /** Excel 업로드 — WorkOrderExcelService 위임 */
    @Transactional
    public WorkOrderUploadResult uploadExcel(MultipartFile file) {
        return excelService.uploadExcel(file);
    }

    /** 업로드 템플릿 다운로드 — WorkOrderExcelService 위임 */
    public XSSFWorkbook downloadTemplate() {
        return excelService.downloadTemplate();
    }

    /**
     * 정상 센서값 수신 → Redis goodQty 카운터 +1 (DB 접근 없음).
     */
    public void recordGoodCount(String equipmentId) {
        Long woId = getActiveWoId(equipmentId);
        if (woId == null) return;
        redisTemplate.opsForValue().increment(WO_GOOD_PREFIX + woId);
    }

    /**
     * FAULT 센서값 수신 → Redis defectQty 카운터 +1 + 불량 타입 기록 (DB 접근 없음).
     */
    public void recordDefectCount(String equipmentId, DefectType defectType) {
        Long woId = getActiveWoId(equipmentId);
        if (woId == null) return;
        redisTemplate.opsForValue().increment(WO_DEFECT_PREFIX + woId);
        redisTemplate.opsForList().leftPush(WO_DEFECTS_PREFIX + woId, defectType.name());
    }

    /**
     * 활성(IN_PROGRESS) 작업지시 ID 조회.
     * Redis 캐시 우선, 미스 시 DB 폴백 후 캐싱.
     */
    public Long getActiveWoId(String equipmentId) {
        Object cached = redisTemplate.opsForValue().get(WO_ACTIVE_PREFIX + equipmentId);
        if (cached != null) {
            return ((Number) cached).longValue();
        }
        return workOrderRepository
                .findFirstByEquipment_EquipmentIdAndStatus(equipmentId, WorkOrderStatus.IN_PROGRESS)
                .map(wo -> {
                    redisTemplate.opsForValue().set(WO_ACTIVE_PREFIX + equipmentId, wo.getId());
                    return wo.getId();
                })
                .orElse(null);
    }

    /**
     * Redis 카운터 → DB 반영 (스케줄러 호출용).
     */
    @Transactional
    public void flushQtyCounts(Long woId) {
        int goodIncr   = popCounter(WO_GOOD_PREFIX   + woId);
        int defectIncr = popCounter(WO_DEFECT_PREFIX + woId);
        List<String> defectTypeNames = popDefectTypeList(WO_DEFECTS_PREFIX + woId);

        if (goodIncr == 0 && defectIncr == 0) return;

        workOrderRepository.findById(woId).ifPresent(workOrder -> {
            for (String typeName : defectTypeNames) {
                DefectType dt = DefectType.valueOf(typeName);
                defectRepository.save(Defect.builder()
                        .workOrder(workOrder)
                        .equipment(workOrder.getEquipment())
                        .defectType(dt)
                        .qty(1)
                        .note("[자동] 센서 임계값 초과 (" + dt.name() + ")")
                        .build());
            }
            int remaining    = workOrder.getPlannedQty() - workOrder.getGoodQty() - workOrder.getDefectQty();
            int cappedGood   = Math.min(goodIncr,   Math.max(0, remaining));
            int cappedDefect = Math.min(defectIncr, Math.max(0, remaining - cappedGood));
            workOrder.addGoodQty(cappedGood);
            workOrder.addDefectQty(cappedDefect);

            if (workOrder.isComplete()) {
                autoCompleteAndRecreate(workOrder);
            }
        });
    }

    private int popCounter(String key) {
        Object val = redisTemplate.opsForValue().getAndDelete(key);
        return val != null ? ((Number) val).intValue() : 0;
    }

    private List<String> popDefectTypeList(String key) {
        List<Object> vals = redisTemplate.opsForList().range(key, 0, -1);
        redisTemplate.delete(key);
        if (vals == null) return List.of();
        return vals.stream().map(Object::toString).toList();
    }

    private void autoCompleteAndRecreate(WorkOrder workOrder) {
        String equipmentId = workOrder.getEquipment().getEquipmentId();

        workOrder.transitionTo(WorkOrderStatus.COMPLETED, workOrder.getGoodQty());
        historyRepository.save(WorkOrderHistory.builder()
                .workOrder(workOrder)
                .fromStatus(WorkOrderStatus.IN_PROGRESS)
                .toStatus(WorkOrderStatus.COMPLETED)
                .changedBy("SYSTEM_AUTO")
                .build());

        redisTemplate.delete(WO_ACTIVE_PREFIX + equipmentId);
    }

    public boolean hasActiveWorkOrder(String equipmentId) {
        return workOrderRepository.existsByEquipment_EquipmentIdAndStatusIn(
                equipmentId, List.of(WorkOrderStatus.PENDING, WorkOrderStatus.IN_PROGRESS));
    }

    @Transactional
    public void rolloverDailyWorkOrder(String equipmentId, int plannedQty) {
        workOrderRepository.findFirstByEquipment_EquipmentIdAndStatus(equipmentId, WorkOrderStatus.IN_PROGRESS)
                .ifPresent(wo -> {
                    wo.transitionTo(WorkOrderStatus.COMPLETED, wo.getGoodQty());
                    historyRepository.save(WorkOrderHistory.builder()
                            .workOrder(wo)
                            .fromStatus(WorkOrderStatus.IN_PROGRESS)
                            .toStatus(WorkOrderStatus.COMPLETED)
                            .changedBy("SYSTEM_DAILY_ROLLOVER")
                            .build());
                    redisTemplate.delete(WO_ACTIVE_PREFIX + equipmentId);
                });

        workOrderRepository.findFirstByEquipment_EquipmentIdAndStatus(equipmentId, WorkOrderStatus.PENDING)
                .ifPresent(workOrderRepository::delete);

        equipmentRepository.findByEquipmentId(equipmentId).ifPresent(equipment ->
                workOrderRepository.save(WorkOrder.builder()
                        .workOrderNo(noGenerator.generate())
                        .equipment(equipment)
                        .plannedQty(plannedQty)
                        .build())
        );
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }
}
