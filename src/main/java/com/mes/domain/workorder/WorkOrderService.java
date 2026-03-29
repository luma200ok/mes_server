package com.mes.domain.workorder;

import com.mes.domain.defect.Defect;
import com.mes.domain.defect.DefectRepository;
import com.mes.domain.defect.DefectType;
import com.mes.domain.equipment.Equipment;
import com.mes.domain.equipment.EquipmentRepository;
import com.mes.domain.workorder.dto.*;
import com.mes.global.exception.CustomException;
import com.mes.global.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

    private final AtomicInteger workOrderSeq = new AtomicInteger(0);

    /**
     * 서버 기동 시 오늘 날짜 작업지시 수로 seq 초기화.
     * 재시작 후 중복 번호 생성 방지.
     */
    @PostConstruct
    public void initSeq() {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = workOrderRepository.countByWorkOrderNoStartingWith("WO-" + today + "-");
        workOrderSeq.set((int) count);
    }

    public List<WorkOrderResponse> findAll() {
        return workOrderRepository.findAll().stream()
                .map(WorkOrderResponse::from)
                .toList();
    }

    @Transactional
    public WorkOrderResponse create(WorkOrderRequest request) {
        Equipment equipment = equipmentRepository.findByEquipmentId(request.equipmentId())
                .orElseThrow(() -> new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND));

        String workOrderNo = generateWorkOrderNo();
        WorkOrder workOrder = WorkOrder.builder()
                .workOrderNo(workOrderNo)
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

    @Transactional
    public WorkOrderUploadResult uploadExcel(MultipartFile file) {
        List<WorkOrderUploadResult.RowError> errors = new ArrayList<>();
        int successCount = 0;
        int rowIndex = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            // 헤더(0번 행) 스킵, 1번 행부터 처리
            for (rowIndex = 1; rowIndex <= lastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowEmpty(row)) continue;

                try {
                    String equipmentId = getCellString(row.getCell(0));
                    int plannedQty    = (int) getCellNumeric(row.getCell(1));

                    if (equipmentId.isBlank()) {
                        errors.add(new WorkOrderUploadResult.RowError(rowIndex + 1, "설비 ID가 비어 있습니다."));
                        continue;
                    }
                    if (plannedQty < 1) {
                        errors.add(new WorkOrderUploadResult.RowError(rowIndex + 1, "계획 수량은 1 이상이어야 합니다."));
                        continue;
                    }

                    Equipment equipment = equipmentRepository.findByEquipmentId(equipmentId)
                            .orElseThrow(() -> new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND));

                    workOrderRepository.save(WorkOrder.builder()
                            .workOrderNo(generateWorkOrderNo())
                            .equipment(equipment)
                            .plannedQty(plannedQty)
                            .build());
                    successCount++;

                } catch (CustomException e) {
                    errors.add(new WorkOrderUploadResult.RowError(rowIndex + 1, e.getMessage()));
                } catch (Exception e) {
                    errors.add(new WorkOrderUploadResult.RowError(rowIndex + 1, "데이터 형식 오류: " + e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return new WorkOrderUploadResult(rowIndex, successCount, errors.size(), errors);
    }

    public XSSFWorkbook downloadTemplate() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("작업지시 업로드");

        // 헤더 스타일
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row header = sheet.createRow(0);
        String[] headers = {"설비 ID (equipmentId)", "계획 수량 (plannedQty)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 6000);
        }

        // 예시 데이터 1행
        Row example = sheet.createRow(1);
        example.createCell(0).setCellValue("EQ-001");
        example.createCell(1).setCellValue(100);

        return workbook;
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default      -> "";
        };
    }

    private double getCellNumeric(Cell cell) {
        if (cell == null) return 0;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING  -> Double.parseDouble(cell.getStringCellValue().trim());
            default      -> 0;
        };
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
        // DB 폴백 (캐시 미스 시에만 쿼리)
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
     * goodQty / defectQty 누적 반영 후 계획 수량 달성 시 COMPLETED 자동 전환 + 새 WO 생성.
     */
    @Transactional
    public void flushQtyCounts(Long woId) {
        int goodIncr   = popCounter(WO_GOOD_PREFIX   + woId);
        int defectIncr = popCounter(WO_DEFECT_PREFIX + woId);
        List<String> defectTypeNames = popDefectTypeList(WO_DEFECTS_PREFIX + woId);

        if (goodIncr == 0 && defectIncr == 0) return;

        workOrderRepository.findById(woId).ifPresent(workOrder -> {
            // 불량 타입별 Defect 레코드 저장
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
            // 수량 일괄 반영
            workOrder.addGoodQty(goodIncr);
            workOrder.addDefectQty(defectIncr);

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

        // active 키 삭제 (새 WO는 PENDING 상태)
        redisTemplate.delete(WO_ACTIVE_PREFIX + equipmentId);

        // 동일 설비에 동일 계획 수량으로 즉시 새 작업지시 생성
        workOrderRepository.save(WorkOrder.builder()
                .workOrderNo(generateWorkOrderNo())
                .equipment(workOrder.getEquipment())
                .plannedQty(workOrder.getPlannedQty())
                .build());
    }

    /** 설비에 활성(PENDING/IN_PROGRESS) 작업지시가 있는지 여부 */
    public boolean hasActiveWorkOrder(String equipmentId) {
        return workOrderRepository.existsByEquipment_EquipmentIdAndStatusIn(
                equipmentId, List.of(WorkOrderStatus.PENDING, WorkOrderStatus.IN_PROGRESS));
    }

    private String generateWorkOrderNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seq = workOrderSeq.incrementAndGet();
        return String.format("WO-%s-%03d", date, seq);
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }
}
