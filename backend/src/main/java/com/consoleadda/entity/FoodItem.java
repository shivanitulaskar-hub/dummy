package com.consoleadda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Menu item. {@code stockQuantity} is a cache of the inventory ledger, changed only by atomic SQL updates in
 * {@code InventoryService}; never set it directly.
 */
@Getter
@Setter
@Entity
@Table(name = "food_items")
public class FoodItem extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private FoodCategory category;

    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "stock_quantity", nullable = false, insertable = false, updatable = false)
    private int stockQuantity;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    @Column(name = "track_inventory", nullable = false)
    private boolean trackInventory = true;

    /** Free gaming minutes per unit sold (e.g. 15 for the qualifying mojito). */
    @Column(name = "gaming_credit_minutes", nullable = false)
    private int gamingCreditMinutes;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
