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
    private String requestId;        // 请求ID
    private String transactionId;    // 交易ID
    private String accountId;        // 账户ID
    private BigDecimal amount;       // 交易金额
    private String currency;         // 货币类型
    private String sourceCountry;    // 源国家
    private String destinationCountry; // 目标国家
    private LocalDateTime timestamp; // 交易时间
    private LocalDateTime accountCreationDate; // 账户创建时间
    private String ipAddress;        // IP地址
    private String deviceId;         // 设备ID
} 