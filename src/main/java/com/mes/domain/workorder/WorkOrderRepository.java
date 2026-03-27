package com.mes.domain.workorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    Optional<WorkOrder> findByWorkOrderNo(String workOrderNo);

    long countByEquipment_EquipmentIdAndStatus(String equipmentId, WorkOrderStatus status);
}
