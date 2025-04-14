package com.example.frauddetection.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudDetectionResult {
    private String transactionId;
    private boolean fraudulent;
    private String fraudReason;
    private long processingTimeMs;
} 