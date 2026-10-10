package com.academy.paybridge.compliance.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "sanctions_entry")
public class SanctionsEntry {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "name_normalized", nullable = false) private String nameNormalized;
    @Column(nullable = false) private String source;

    protected SanctionsEntry() {}
}