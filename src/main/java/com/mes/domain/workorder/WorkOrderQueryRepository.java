package com.mes.domain.workorder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface WorkOrderQueryRepository {

    Page<WorkOrder> search(LocalDate startDate, LocalDate endDate, WorkOrderStatus status, Pageable pageable);

    List<WorkOrder> findByDateRange(LocalDate startDate, LocalDate endDate);
}
