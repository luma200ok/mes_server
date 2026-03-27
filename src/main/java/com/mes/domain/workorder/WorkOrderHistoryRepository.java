package com.mes.domain.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderHistoryRepository extends JpaRepository<WorkOrderHistory, Long> {

    List<WorkOrderHistory> findByWorkOrder_IdOrderByChangedAtAsc(Long workOrderId);
}
