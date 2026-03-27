package com.mes.domain.equipment;

import com.mes.domain.equipment.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Equipment", description = "설비 관리 API")
@RestController
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    @Operation(summary = "설비 목록 조회")
    @GetMapping("/api/equipment")
    public ResponseEntity<List<EquipmentResponse>> getAll() {
        return ResponseEntity.ok(equipmentService.findAll());
    }

    @Operation(summary = "설비 단건 조회")
    @GetMapping("/api/equipment/{equipmentId}")
    public ResponseEntity<EquipmentResponse> getOne(@PathVariable String equipmentId) {
        return ResponseEntity.ok(equipmentService.findByEquipmentId(equipmentId));
    }

    @Operation(summary = "설비 등록")
    @PostMapping("/api/equipment")
    public ResponseEntity<EquipmentResponse> create(@Valid @RequestBody EquipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(equipmentService.create(request));
    }

    @Operation(summary = "설비 삭제 (소프트 딜리트)")
    @DeleteMapping("/api/equipment/{equipmentId}")
    public ResponseEntity<Void> delete(@PathVariable String equipmentId) {
        equipmentService.delete(equipmentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "설비 설정 조회")
    @GetMapping("/api/equipment-config/{equipmentId}")
    public ResponseEntity<EquipmentConfigResponse> getConfig(@PathVariable String equipmentId) {
        return ResponseEntity.ok(equipmentService.findConfig(equipmentId));
    }

    @Operation(summary = "설비 설정 등록/수정")
    @PostMapping("/api/equipment-config")
    public ResponseEntity<EquipmentConfigResponse> saveConfig(@Valid @RequestBody EquipmentConfigRequest request) {
        return ResponseEntity.ok(equipmentService.saveConfig(request));
    }
}
