package com.mes.domain.workorder;

import com.mes.domain.workorder.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Tag(name = "WorkOrder", description = "작업지시 API")
@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @Operation(summary = "작업지시 목록 조회")
    @GetMapping
    public ResponseEntity<List<WorkOrderResponse>> getAll() {
        return ResponseEntity.ok(workOrderService.findAll());
    }

    @Operation(summary = "작업지시 등록")
    @PostMapping
    public ResponseEntity<WorkOrderResponse> create(@Valid @RequestBody WorkOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workOrderService.create(request));
    }

    @Operation(summary = "작업지시 상태 전이")
    @PatchMapping("/{id}/status")
    public ResponseEntity<WorkOrderResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody WorkOrderStatusRequest request) {
        return ResponseEntity.ok(workOrderService.changeStatus(id, request));
    }

    @Operation(summary = "작업지시 상태 변경 이력 조회")
    @GetMapping("/{id}/history")
    public ResponseEntity<List<WorkOrderHistoryResponse>> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(workOrderService.findHistory(id));
    }

    @Operation(summary = "작업지시 Excel 일괄 업로드")
    @PostMapping("/upload")
    public ResponseEntity<WorkOrderUploadResult> uploadExcel(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(workOrderService.uploadExcel(file));
    }

    @Operation(summary = "작업지시 Excel 업로드 템플릿 다운로드")
    @GetMapping("/upload/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=work_order_template.xlsx");
        workOrderService.downloadTemplate().write(response.getOutputStream());
    }
}
