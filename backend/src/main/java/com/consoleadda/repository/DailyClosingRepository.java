package com.consoleadda.repository;

import com.consoleadda.entity.DailyClosing;
import com.consoleadda.entity.DayStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DailyClosingRepository extends JpaRepository<DailyClosing, Long> {

    Optional<DailyClosing> findByBusinessDate(LocalDate businessDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DailyClosing d where d.businessDate = :date")
    Optional<DailyClosing> findByBusinessDateForUpdate(@Param("date") LocalDate date);

    List<DailyClosing> findByBusinessDateBetweenAndStatusInOrderByBusinessDateDesc(LocalDate from, LocalDate to,
                                                                                  Collection<DayStatus> statuses);
}
