package com.example.frauddetection.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {
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
} 