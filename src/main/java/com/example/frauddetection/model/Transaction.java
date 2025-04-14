package com.example.frauddetection.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String transactionId;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String sourceCountry;
    private String destinationCountry;
    private LocalDateTime timestamp;
    private LocalDateTime accountCreationDate;
    private String ipAddress;
    private String deviceId;
    
    // 欺诈检测结果
    private boolean fraudulent;
    private String fraudReason;
} 