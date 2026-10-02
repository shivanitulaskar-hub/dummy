package com.consoleadda.repository;

import com.consoleadda.entity.GamingSession;
import com.consoleadda.entity.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GamingSessionRepository extends JpaRepository<GamingSession, Long> {

    boolean existsByStationIdAndStatus(Long stationId, SessionStatus status);

    boolean existsByStationId(Long stationId);

    boolean existsByPricingPlanIdAndStatus(Long pricingPlanId, SessionStatus status);

    boolean existsByPricingPlanId(Long pricingPlanId);

    @Query("""
            select s from GamingSession s
            join fetch s.station join fetch s.bill join fetch s.pricingPlan left join fetch s.customer
            where s.status = :status order by s.startedAt asc""")
    List<GamingSession> findWithDetailsByStatus(@Param("status") SessionStatus status);

    @Query("""
            select s from GamingSession s
            join fetch s.station join fetch s.bill join fetch s.pricingPlan left join fetch s.customer
            where s.id = :id""")
    Optional<GamingSession> findWithDetailsById(@Param("id") Long id);

    Optional<GamingSession> findByBillId(Long billId);

    @Query("""
            select s from GamingSession s join fetch s.station join fetch s.bill
            where s.customer.id = :customerId order by s.startedAt desc""")
    List<GamingSession> findByCustomer(@Param("customerId") Long customerId,
                                       org.springframework.data.domain.Pageable pageable);

    /** Row lock that serialises concurrent end/complete/cancel requests for the same session. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GamingSession s where s.id = :id")
    Optional<GamingSession> findByIdForUpdate(@Param("id") Long id);
}
