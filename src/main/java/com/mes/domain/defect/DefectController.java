package com.mes.domain.defect;

import com.mes.domain.defect.dto.DefectRequest;
import com.mes.domain.defect.dto.DefectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Defect", description = "불량 관리 API")
@RestController
@RequestMapping("/api/defects")
@RequiredArgsConstructor
public class DefectController {

    private final DefectService defectService;

    @Operation(summary = "불량 목록 조회 (작업지시 기준)")
    @GetMapping
    public ResponseEntity<List<DefectResponse>> getByWorkOrder(@RequestParam Long workOrderId) {
        return ResponseEntity.ok(defectService.findByWorkOrder(workOrderId));
    }

    @Operation(summary = "불량 등록")
    @PostMapping
    public ResponseEntity<DefectResponse> register(@Valid @RequestBody DefectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(defectService.register(request));
    }
}
