package com.mes.domain.dashboard;

import com.mes.domain.dashboard.dto.OeeResponse;
import com.mes.domain.dashboard.dto.SensorHistoryResponse;
import com.mes.domain.dashboard.dto.SensorStatisticsDto;
import com.mes.domain.dashboard.dto.WorkOrderStatisticsDto;
import com.mes.domain.sensor.SensorHistoryRepository;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    @Value("${mes.oee.planned-operation-hours}")
    private double plannedOperationHours;

    private final DashboardQueryRepository dashboardQueryRepository;
    private final SensorHistoryRepository sensorHistoryRepository;

    @Transactional(readOnly = true)
    public OeeResponse getOee(String equipmentId, LocalDate date) {
        WorkOrderStatisticsDto woStats = dashboardQueryRepository
                .findWorkOrderStats(equipmentId, date)
                .orElse(new WorkOrderStatisticsDto(equipmentId, 0L, 0L, 0L, 0, 0));

        SensorStatisticsDto defectStats = dashboardQueryRepository
                .findDefectStats(equipmentId, date)
                .orElse(new SensorStatisticsDto(equipmentId, 0));

        // DEFECTIVE도 설비가 가동된 것이므로 가용률에 포함 (품질 문제는 quality 지표로 반영)
        long processedWorkOrders = woStats.getCompletedWorkOrders() + woStats.getDefectiveWorkOrders();
        double availability = woStats.getTotalWorkOrders() == 0 ? 0.0
                : (double) processedWorkOrders / woStats.getTotalWorkOrders();

        double performance = woStats.getTotalPlannedQty() == 0 ? 0.0
                : (double) woStats.getTotalCompletedQty() / woStats.getTotalPlannedQty();

        int goodQty = woStats.getTotalCompletedQty() - defectStats.getTotalDefectQty();
        double quality = woStats.getTotalCompletedQty() == 0 ? 0.0
                : (double) Math.max(goodQty, 0) / woStats.getTotalCompletedQty();

        double oee = availability * performance * quality;

        return new OeeResponse(
                equipmentId,
                date.toString(),
                round(availability),
                round(performance),
                round(quality),
                round(oee),
                woStats.getTotalWorkOrders(),
                woStats.getCompletedWorkOrders(),
                woStats.getDefectiveWorkOrders(),
                woStats.getTotalPlannedQty(),
                woStats.getTotalCompletedQty(),
                defectStats.getTotalDefectQty()
        );
    }

    @Transactional(readOnly = true)
    public List<SensorHistoryResponse> getSensorHistory(String equipmentId,
                                                         LocalDateTime from,
                                                         LocalDateTime to) {
        return sensorHistoryRepository
                .findByEquipment_EquipmentIdAndRecordedAtBetweenOrderByRecordedAtAsc(equipmentId, from, to)
                .stream()
                .map(SensorHistoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] exportExcel(String equipmentId, LocalDateTime from, LocalDateTime to) throws IOException {
        List<SensorHistoryResponse> list = getSensorHistory(equipmentId, from, to);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("SensorHistory");

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row header = sheet.createRow(0);
            String[] columns = {"ID", "설비ID", "평균온도", "평균진동", "평균RPM", "기록시각"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (SensorHistoryResponse h : list) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(h.id());
                row.createCell(1).setCellValue(h.equipmentId());
                row.createCell(2).setCellValue(h.avgTemperature());
                row.createCell(3).setCellValue(h.avgVibration());
                row.createCell(4).setCellValue(h.avgRpm());
                row.createCell(5).setCellValue(h.recordedAt().toString());
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Transactional(readOnly = true)
    public String exportCsv(String equipmentId, LocalDateTime from, LocalDateTime to) throws IOException {
        List<SensorHistoryResponse> list = getSensorHistory(equipmentId, from, to);

        try (StringWriter sw = new StringWriter();
             CSVWriter writer = new CSVWriter(sw)) {

            writer.writeNext(new String[]{"ID", "설비ID", "평균온도", "평균진동", "평균RPM", "기록시각"});
            for (SensorHistoryResponse h : list) {
                writer.writeNext(new String[]{
                        String.valueOf(h.id()),
                        h.equipmentId(),
                        String.valueOf(h.avgTemperature()),
                        String.valueOf(h.avgVibration()),
                        String.valueOf(h.avgRpm()),
                        h.recordedAt().toString()
                });
            }
            return sw.toString();
        }
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
