package com.mes.domain.workorder;

import java.time.LocalDate;
import java.util.List;

public interface WorkOrderQueryRepository {

    List<WorkOrder> findByDateRange(LocalDate startDate, LocalDate endDate, WorkOrderStatus status);
}
