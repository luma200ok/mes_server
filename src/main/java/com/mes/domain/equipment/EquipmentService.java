package com.mes.domain.equipment;

import com.mes.domain.equipment.dto.*;
import com.mes.global.exception.CustomException;
import com.mes.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentConfigRepository configRepository;

    public List<EquipmentResponse> findAll() {
        return equipmentRepository.findAll().stream()
                .map(EquipmentResponse::from)
                .toList();
    }

    public EquipmentResponse findByEquipmentId(String equipmentId) {
        return equipmentRepository.findByEquipmentId(equipmentId)
                .map(EquipmentResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND));
    }

    @Transactional
    public EquipmentResponse create(EquipmentRequest request) {
        if (equipmentRepository.existsByEquipmentId(request.equipmentId())) {
            throw new CustomException(ErrorCode.EQUIPMENT_ID_DUPLICATE);
        }
        Equipment equipment = Equipment.builder()
                .equipmentId(request.equipmentId())
                .name(request.name())
                .location(request.location())
                .build();
        return EquipmentResponse.from(equipmentRepository.save(equipment));
    }

    @Transactional
    public void delete(String equipmentId) {
        Equipment equipment = equipmentRepository.findByEquipmentId(equipmentId)
                .orElseThrow(() -> new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND));
        equipmentRepository.delete(equipment);
    }

    @Cacheable(value = "equipmentConfig", key = "#equipmentId")
    public EquipmentConfigResponse findConfig(String equipmentId) {
        return configRepository.findByEquipment_EquipmentId(equipmentId)
                .map(EquipmentConfigResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.EQUIPMENT_CONFIG_NOT_FOUND));
    }

    @Transactional
    @CacheEvict(value = "equipmentConfig", key = "#request.equipmentId()")
    public EquipmentConfigResponse saveConfig(EquipmentConfigRequest request) {
        Equipment equipment = equipmentRepository.findByEquipmentId(request.equipmentId())
                .orElseThrow(() -> new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND));

        EquipmentConfig config = configRepository.findByEquipment_EquipmentId(request.equipmentId())
                .orElse(null);

        if (config == null) {
            config = EquipmentConfig.builder()
                    .equipment(equipment)
                    .maxTemperature(request.maxTemperature())
                    .maxVibration(request.maxVibration())
                    .maxRpm(request.maxRpm())
                    .build();
        } else {
            config.update(request.maxTemperature(), request.maxVibration(), request.maxRpm());
        }

        return EquipmentConfigResponse.from(configRepository.save(config));
    }
}
