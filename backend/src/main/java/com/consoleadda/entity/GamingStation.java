package com.consoleadda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "gaming_stations")
public class GamingStation extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "console_label", length = 50)
    private String consoleLabel;

    @Column(name = "tv_label", length = 50)
    private String tvLabel;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "notes", length = 500)
    private String notes;
}
