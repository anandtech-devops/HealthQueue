package com.healthqueue.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "departments")
public class Department {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String name;

    protected Department() {}

    public Long getId() { return id; }
    public String getName() { return name; }
}
