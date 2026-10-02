package com.consoleadda.repository;

import com.consoleadda.entity.Bill;
import com.consoleadda.entity.BillStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Long> {

    Optional<Bill> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bill b where b.id = :id")
    Optional<Bill> findByIdForUpdate(@Param("id") Long id);

    /** Search by bill number, customer name or mobile (case-insensitive "contains"), with optional filters. */
    @Query(value = """
            select b from Bill b
            where b.status in :statuses
              and (:from is null or b.businessDate >= :from)
              and (:to is null or b.businessDate <= :to)
              and (:q is null or lower(b.billNumber) like :q or lower(b.customerName) like :q or b.customerMobile like :q)
            order by b.id desc""",
            countQuery = """
            select count(b) from Bill b
            where b.status in :statuses
              and (:from is null or b.businessDate >= :from)
              and (:to is null or b.businessDate <= :to)
              and (:q is null or lower(b.billNumber) like :q or lower(b.customerName) like :q or b.customerMobile like :q)""")
    Page<Bill> search(@Param("statuses") Collection<BillStatus> statuses, @Param("from") LocalDate from,
                      @Param("to") LocalDate to, @Param("q") String q, Pageable pageable);
}
