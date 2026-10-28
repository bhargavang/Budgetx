package com.budgetx.expense.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fixed list of categories, seeded from data.sql. */
@Entity
@Table(name = "expense_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseCategory {

    @Id
    private Long categoryId;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    /** Whether the category is a need (rent, groceries) rather than a want (dining out). */
    @Column(nullable = false)
    private boolean essential;
}
