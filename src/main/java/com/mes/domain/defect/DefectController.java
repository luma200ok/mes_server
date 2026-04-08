package com.mes.domain.defect;

import com.mes.domain.defect.dto.DefectRequest;
import com.mes.domain.defect.dto.DefectResponse;
import com.mes.domain.defect.dto.DefectSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Defect", description = "불량 관리 API")
@Validated
@RestController
@RequestMapping("/api/defects")
@RequiredArgsConstructor
public class DefectController {

    private final DefectService defectService;

    @Operation(summary = "불량 목록 조회 (날짜/유형/설비 필터 + 페이징)")
    @GetMapping
    public ResponseEntity<Page<DefectResponse>> getDefects(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) DefectType defectType,
            @RequestParam(required = false) String equipmentId,
            @RequestParam(defaultValue = "0")  @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Max(100) int size) {
        return ResponseEntity.ok(
                defectService.findFiltered(startDate, endDate, defectType, equipmentId, page, size));
    }

    @Operation(summary = "불량 요약 통계 (날짜/설비 필터)")
    @GetMapping("/summary")
    public ResponseEntity<DefectSummaryResponse> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String equipmentId) {
        return ResponseEntity.ok(defectService.getSummary(startDate, endDate, equipmentId));
    }

    @Operation(summary = "특정 작업지시의 불량 목록 조회")
    @GetMapping("/by-work-order/{workOrderId}")
    public ResponseEntity<List<DefectResponse>> getByWorkOrder(@PathVariable Long workOrderId) {
        return ResponseEntity.ok(defectService.findByWorkOrder(workOrderId));
    }

    @Operation(summary = "불량 등록")
    @PostMapping
    public ResponseEntity<DefectResponse> register(@Valid @RequestBody DefectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(defectService.register(request));
    }
}
