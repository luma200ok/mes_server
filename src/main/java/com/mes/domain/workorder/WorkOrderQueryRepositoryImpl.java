package com.mes.domain.workorder;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class WorkOrderQueryRepositoryImpl implements WorkOrderQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<WorkOrder> findByDateRange(LocalDate startDate, LocalDate endDate, WorkOrderStatus status) {
        QWorkOrder wo = QWorkOrder.workOrder;
        BooleanBuilder builder = new BooleanBuilder();

        if (startDate != null) {
            builder.and(wo.createdAt.goe(startDate.atStartOfDay()));
        }
        if (endDate != null) {
            builder.and(wo.createdAt.lt(endDate.plusDays(1).atStartOfDay()));
        }
        if (status != null) {
            builder.and(wo.status.eq(status));
        }

        return queryFactory
                .selectFrom(wo)
                .join(wo.equipment).fetchJoin()
                .where(builder)
                .orderBy(wo.createdAt.desc())
                .fetch();
    }
}
