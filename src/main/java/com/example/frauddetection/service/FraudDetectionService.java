package com.example.frauddetection.service;

import com.example.frauddetection.dto.FraudDetectionResult;
import com.example.frauddetection.dto.TransactionRequest;

public interface FraudDetectionService {
    
    /**
     * 分析交易并检测潜在欺诈
     *
     * @param transactionRequest 待分析的交易请求
     * @return 欺诈检测结果
     */
    FraudDetectionResult analyzeTransaction(TransactionRequest transactionRequest);
} 