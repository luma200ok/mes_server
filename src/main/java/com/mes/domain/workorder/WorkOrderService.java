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
import org.apache.poi.ss.usermodel.*;
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

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderHistoryRepository historyRepository;
    private final EquipmentRepository equipmentRepository;
    private final DefectRepository defectRepository;

    private final AtomicInteger workOrderSeq = new AtomicInteger(0);

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
     * 센서 임계값 초과 시 자동 불량 처리.
     * IN_PROGRESS 상태 작업지시가 없으면 조용히 return.
     * completedQty 검증 없이 Defect qty=1 저장.
     */
    @Transactional
    public void autoMarkDefective(String equipmentId, DefectType defectType) {
        workOrderRepository
                .findFirstByEquipment_EquipmentIdAndStatus(equipmentId, WorkOrderStatus.IN_PROGRESS)
                .ifPresent(workOrder -> {
                    defectRepository.save(Defect.builder()
                            .workOrder(workOrder)
                            .equipment(workOrder.getEquipment())
                            .defectType(defectType)
                            .qty(1)
                            .note("[자동] 센서 임계값 초과 (" + defectType.name() + ")")
                            .build());

                    workOrder.markDefective();

                    historyRepository.save(WorkOrderHistory.builder()
                            .workOrder(workOrder)
                            .fromStatus(WorkOrderStatus.IN_PROGRESS)
                            .toStatus(WorkOrderStatus.DEFECTIVE)
                            .changedBy("SYSTEM_SENSOR")
                            .build());
                });
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
