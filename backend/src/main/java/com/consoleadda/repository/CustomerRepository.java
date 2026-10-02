package com.consoleadda.repository;

import com.consoleadda.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByMobile(String mobile);

    boolean existsByMobileAndIdNot(String mobile, Long id);

    /** Name or mobile contains {@code q}, or the customer has a bill whose number contains it. */
    @Query(value = """
            select c from Customer c
            where :q is null or lower(c.name) like :q or c.mobile like :q
               or exists (select 1 from Bill b where b.customer = c and lower(b.billNumber) like :q)
            order by c.lastVisitAt desc nulls last, c.id desc""",
            countQuery = """
            select count(c) from Customer c
            where :q is null or lower(c.name) like :q or c.mobile like :q
               or exists (select 1 from Bill b where b.customer = c and lower(b.billNumber) like :q)""")
    Page<Customer> search(@Param("q") String q, Pageable pageable);

    /**
     * Atomic increment, so two bills completing at the same moment for one customer never conflict or lose
     * an update.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("""
            update Customer c set c.totalSessions = c.totalSessions + 1,
                c.totalGamingMinutes = c.totalGamingMinutes + :minutes,
                c.totalSpent = c.totalSpent + :amount,
                c.lastVisitAt = case when c.lastVisitAt is null or c.lastVisitAt < :at then :at else c.lastVisitAt end
            where c.id = :id""")
    int recordVisit(@Param("id") Long id, @Param("minutes") int minutes, @Param("amount") BigDecimal amount,
                    @Param("at") Instant at);

    /** Walk-in purchase: adds spend and updates last visit without counting a gaming session. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Customer c set c.totalSpent = c.totalSpent + :amount,
                c.lastVisitAt = case when c.lastVisitAt is null or c.lastVisitAt < :at then :at else c.lastVisitAt end
            where c.id = :id""")
    int recordPurchase(@Param("id") Long id, @Param("amount") BigDecimal amount, @Param("at") Instant at);

    /** Reverses a cancelled bill's contribution to the cached totals. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Customer c set c.totalSessions = c.totalSessions - :sessions,
                c.totalGamingMinutes = c.totalGamingMinutes - :minutes,
                c.totalSpent = c.totalSpent - :amount
            where c.id = :id""")
    int reverseTotals(@Param("id") Long id, @Param("sessions") int sessions, @Param("minutes") int minutes,
                      @Param("amount") BigDecimal amount);
}
