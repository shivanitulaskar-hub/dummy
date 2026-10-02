package com.consoleadda.repository;

import com.consoleadda.entity.SessionTimeCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SessionTimeCreditRepository extends JpaRepository<SessionTimeCredit, Long> {

    List<SessionTimeCredit> findBySessionIdOrderByIdAsc(Long sessionId);

    @Modifying(flushAutomatically = true)
    @Query("delete from SessionTimeCredit c where c.session.id = :sessionId")
    void deleteBySessionId(@Param("sessionId") Long sessionId);
}
