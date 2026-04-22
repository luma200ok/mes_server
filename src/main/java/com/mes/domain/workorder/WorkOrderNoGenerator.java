package com.mes.domain.workorder;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 작업지시 번호 생성기.
 * 서버 기동 시 오늘 날짜 발급 수로 시퀀스를 초기화하여 재시작 후 중복 방지.
 * 형식: WO-{yyyyMMdd}-{seq:03d}
 */
@Component
@RequiredArgsConstructor
public class WorkOrderNoGenerator {

    private final WorkOrderRepository workOrderRepository;
    private final AtomicInteger seq = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = workOrderRepository.countByWorkOrderNoStartingWith("WO-" + today + "-");
        seq.set((int) count);
    }

    public String generate() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("WO-%s-%03d", date, seq.incrementAndGet());
    }
}
