package com.mes.domain.workorder;

import com.mes.domain.equipment.Equipment;
import com.mes.domain.equipment.EquipmentRepository;
import com.mes.domain.workorder.dto.WorkOrderUploadResult;
import com.mes.global.exception.CustomException;
import com.mes.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * WorkOrder Excel 업로드/다운로드 전용 서비스.
 * WorkOrderService에서 Excel I/O 책임을 분리.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkOrderExcelService {

    private final WorkOrderRepository workOrderRepository;
    private final EquipmentRepository equipmentRepository;
    private final WorkOrderNoGenerator noGenerator;

    /**
     * Excel 파일 파싱 → WorkOrder 일괄 등록.
     * 헤더(0번 행) 스킵, 1번 행부터 처리.
     * 행별 오류는 수집 후 결과에 포함 (전체 롤백 없이 성공 행만 저장).
     */
    @Transactional
    public WorkOrderUploadResult uploadExcel(MultipartFile file) {
        List<WorkOrderUploadResult.RowError> errors = new ArrayList<>();
        int successCount = 0;
        int rowIndex = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            for (rowIndex = 1; rowIndex <= lastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowEmpty(row)) continue;

                try {
                    String equipmentId = getCellString(row.getCell(0));
                    int plannedQty     = (int) getCellNumeric(row.getCell(1));

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
                            .workOrderNo(noGenerator.generate())
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

    /**
     * 업로드 Excel 템플릿 파일 생성 (헤더 + 예시 1행).
     */
    public XSSFWorkbook downloadTemplate() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("작업지시 업로드");

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

        Row example = sheet.createRow(1);
        example.createCell(0).setCellValue("EQ-001");
        example.createCell(1).setCellValue(100);

        return workbook;
    }

    // ── 헬퍼 ────────────────────────────────────────────

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
}
