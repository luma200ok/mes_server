package com.mes.domain.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EquipmentConfigRepository extends JpaRepository<EquipmentConfig, Long> {

    Optional<EquipmentConfig> findByEquipment_EquipmentId(String equipmentId);
}
