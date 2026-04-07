package com.mes.domain.workorder;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long>, WorkOrderQueryRepository {

    @EntityGraph(attributePaths = "equipment")
    List<WorkOrder> findAll();

    Optional<WorkOrder> findByWorkOrderNo(String workOrderNo);

    long countByEquipment_EquipmentIdAndStatus(String equipmentId, WorkOrderStatus status);

    @EntityGraph(attributePaths = "equipment")
    Optional<WorkOrder> findFirstByEquipment_EquipmentIdAndStatus(String equipmentId, WorkOrderStatus status);

    boolean existsByEquipment_EquipmentIdAndStatusIn(String equipmentId, List<WorkOrderStatus> statuses);

    long countByWorkOrderNoStartingWith(String prefix);
}
