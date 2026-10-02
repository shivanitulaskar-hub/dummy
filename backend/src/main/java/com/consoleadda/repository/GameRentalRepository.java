package com.consoleadda.repository;

import com.consoleadda.entity.GameRental;
import com.consoleadda.entity.RentalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GameRentalRepository extends JpaRepository<GameRental, Long> {

    boolean existsByGameIdAndStatus(Long gameId, RentalStatus status);

    List<GameRental> findByStatus(RentalStatus status);

    Optional<GameRental> findByBillId(Long billId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GameRental r where r.id = :id")
    Optional<GameRental> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select r from GameRental r join fetch r.game left join fetch r.bill left join fetch r.lateFeeBill
            where r.id = :id""")
    Optional<GameRental> findWithDetailsById(@Param("id") Long id);

    /**
     * Search by rental number, customer name/mobile or game name. {@code overdueBefore} (when set) keeps only
     * rentals whose expected return time is earlier, i.e. overdue ones.
     */
    @Query(value = """
            select r from GameRental r join fetch r.game g left join fetch r.bill left join fetch r.lateFeeBill
            where r.status in :statuses
              and (:overdueBefore is null or r.expectedReturnAt < :overdueBefore)
              and (:q is null or lower(r.rentalNumber) like :q or lower(r.customerName) like :q
                   or r.customerMobile like :q or lower(g.name) like :q)
            order by case when r.status = 'OUT' then 0 else 1 end, r.expectedReturnAt asc, r.id desc""",
            countQuery = """
            select count(r) from GameRental r join r.game g
            where r.status in :statuses
              and (:overdueBefore is null or r.expectedReturnAt < :overdueBefore)
              and (:q is null or lower(r.rentalNumber) like :q or lower(r.customerName) like :q
                   or r.customerMobile like :q or lower(g.name) like :q)""")
    Page<GameRental> search(@Param("statuses") Collection<RentalStatus> statuses,
                            @Param("overdueBefore") Instant overdueBefore, @Param("q") String q, Pageable pageable);

    @Query("""
            select r from GameRental r join fetch r.game left join fetch r.bill left join fetch r.lateFeeBill
            where r.customer.id = :customerId order by r.id desc""")
    List<GameRental> findByCustomer(@Param("customerId") Long customerId, Pageable pageable);
}
