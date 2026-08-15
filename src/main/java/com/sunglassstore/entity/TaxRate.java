package com.sunglassstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "TAX_RATES")
@Getter
@Setter
@NoArgsConstructor
public class TaxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TAX_RATE_ID")
    private Long taxRateId;

    @Column(name = "TAX_NAME", nullable = false, length = 100)
    private String taxName;

    @Column(name = "COUNTRY", length = 100)
    private String country;

    @Column(name = "STATE", length = 100)
    private String state;

    @Column(name = "RATE_PERCENT", nullable = false, precision = 7, scale = 4)
    private BigDecimal ratePercent;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive = true;

    @Column(name = "VALID_FROM")
    private LocalDateTime validFrom;

    @Column(name = "VALID_TO")
    private LocalDateTime validTo;
}
