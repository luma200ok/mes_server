package com.mes.domain.workorder;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class WorkOrderQueryRepositoryImpl implements WorkOrderQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<WorkOrder> search(LocalDate startDate, LocalDate endDate, WorkOrderStatus status, Pageable pageable) {
        QWorkOrder wo = QWorkOrder.workOrder;
        BooleanBuilder condition = buildCondition(wo, startDate, endDate, status);

        List<WorkOrder> content = queryFactory
                .selectFrom(wo)
                .join(wo.equipment).fetchJoin()
                .where(condition)
                .orderBy(wo.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(wo.count())
                .from(wo)
                .where(condition);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public List<WorkOrder> findByDateRange(LocalDate startDate, LocalDate endDate) {
        QWorkOrder wo = QWorkOrder.workOrder;
        BooleanBuilder condition = buildCondition(wo, startDate, endDate, null);

        return queryFactory
                .selectFrom(wo)
                .join(wo.equipment).fetchJoin()
                .where(condition)
                .orderBy(wo.createdAt.desc())
                .fetch();
    }

    private BooleanBuilder buildCondition(QWorkOrder wo, LocalDate startDate, LocalDate endDate, WorkOrderStatus status) {
        BooleanBuilder builder = new BooleanBuilder();

        if (startDate != null) {
            LocalDateTime start = startDate.atStartOfDay();
            builder.and(wo.createdAt.goe(start));
        }
        if (endDate != null) {
            LocalDateTime end = endDate.plusDays(1).atStartOfDay();
            builder.and(wo.createdAt.lt(end));
        }
        if (status != null) {
            builder.and(wo.status.eq(status));
        }

        return builder;
    }
}
