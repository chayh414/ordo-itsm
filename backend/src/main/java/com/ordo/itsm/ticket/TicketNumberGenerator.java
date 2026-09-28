package com.ordo.itsm.ticket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** ORD-20261001-0001 형식의 티켓 번호 발급 (일자별 순번) */
@Component
@RequiredArgsConstructor
public class TicketNumberGenerator {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final TicketRepository ticketRepository;

    public String next() {
        String prefix = "ORD-" + LocalDate.now(SEOUL).format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        long todayCount = ticketRepository.countByTicketNoStartingWith(prefix);
        return prefix + String.format("%04d", todayCount + 1);
    }
}
