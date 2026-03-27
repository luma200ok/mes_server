package com.mes.domain.alarm;

import com.mes.domain.alarm.dto.AlarmHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "Alarm", description = "알람 이력 API")
@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
public class AlarmHistoryController {

    private final AlarmHistoryService alarmHistoryService;

    @Operation(summary = "설비별 알람 이력 조회")
    @GetMapping("/equipment/{equipmentId}")
    public ResponseEntity<Page<AlarmHistoryResponse>> getByEquipment(
            @PathVariable String equipmentId,
            @PageableDefault(size = 20, sort = "sentAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(alarmHistoryService.getByEquipment(equipmentId, pageable));
    }

    @Operation(summary = "기간별 알람 이력 조회")
    @GetMapping
    public ResponseEntity<Page<AlarmHistoryResponse>> getByPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "sentAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(alarmHistoryService.getByPeriod(from, to, pageable));
    }

    @Operation(summary = "설비 최근 알람 횟수 조회")
    @GetMapping("/equipment/{equipmentId}/count")
    public ResponseEntity<Long> countRecent(
            @PathVariable String equipmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        return ResponseEntity.ok(alarmHistoryService.countRecent(equipmentId, since));
    }
}
