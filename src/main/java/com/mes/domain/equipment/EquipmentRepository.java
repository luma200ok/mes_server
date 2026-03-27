package com.mes.domain.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByEquipmentId(String equipmentId);

    boolean existsByEquipmentId(String equipmentId);
}
