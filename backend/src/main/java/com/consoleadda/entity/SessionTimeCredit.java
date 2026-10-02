package com.consoleadda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Free gaming minutes earned by a session, with the reason and the bill line that earned them. */
@Getter
@Setter
@Entity
@Table(name = "session_time_credits")
public class SessionTimeCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private GamingSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_bill_item_id")
    private BillItem sourceBillItem;

    @Column(name = "minutes", nullable = false)
    private int minutes;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
