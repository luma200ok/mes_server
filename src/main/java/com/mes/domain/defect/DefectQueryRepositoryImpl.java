package com.mes.domain.defect;

import com.mes.domain.defect.dto.DefectTypeStatDto;
import com.mes.domain.defect.dto.QDefectTypeStatDto;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DefectQueryRepositoryImpl implements DefectQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Defect> findFiltered(LocalDate startDate, LocalDate endDate,
                                     DefectType defectType, String equipmentId,
                                     Pageable pageable) {
        QDefect d = QDefect.defect;
        BooleanBuilder builder = buildPredicate(d, startDate, endDate, defectType, equipmentId);

        List<Defect> content = queryFactory
                .selectFrom(d)
                .join(d.workOrder).fetchJoin()
                .join(d.equipment).fetchJoin()
                .where(builder)
                .orderBy(d.detectedAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        long total = Optional.ofNullable(queryFactory
                .select(d.count())
                .from(d)
                .where(builder)
                .fetchOne()).orElse(0L);

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<DefectTypeStatDto> aggregateByType(LocalDate startDate, LocalDate endDate, String equipmentId) {
        QDefect d = QDefect.defect;
        BooleanBuilder builder = buildPredicate(d, startDate, endDate, null, equipmentId);

        return queryFactory
                .select(new QDefectTypeStatDto(d.defectType, d.count(), d.qty.sum().castToNum(Long.class)))
                .from(d)
                .where(builder)
                .groupBy(d.defectType)
                .fetch();
    }

    private BooleanBuilder buildPredicate(QDefect d, LocalDate startDate, LocalDate endDate,
                                          DefectType defectType, String equipmentId) {
        BooleanBuilder builder = new BooleanBuilder();
        if (startDate != null) {
            builder.and(d.detectedAt.goe(startDate.atStartOfDay()));
        }
        if (endDate != null) {
            builder.and(d.detectedAt.lt(endDate.plusDays(1).atStartOfDay()));
        }
        if (defectType != null) {
            builder.and(d.defectType.eq(defectType));
        }
        if (equipmentId != null && !equipmentId.isBlank()) {
            builder.and(d.equipment.equipmentId.eq(equipmentId));
        }
        return builder;
    }
}
