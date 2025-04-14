package com.example.frauddetection.service.impl;

import com.example.frauddetection.dto.FraudDetectionResult;
import com.example.frauddetection.dto.TransactionRequest;
import com.example.frauddetection.model.Transaction;
import com.example.frauddetection.repository.TransactionRepository;
import com.example.frauddetection.service.FraudDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class RuleBasedFraudDetectionService implements FraudDetectionService {

    @Value("${fraud-detection.rules.max-transaction-amount}")
    private BigDecimal maxTransactionAmount;

    @Value("${fraud-detection.rules.suspicious-countries}")
    private String suspiciousCountries;

    @Value("${fraud-detection.rules.min-account-age-days}")
    private int minAccountAgeDays;

    private final TransactionRepository transactionRepository;

    @Autowired
    public RuleBasedFraudDetectionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public FraudDetectionResult analyzeTransaction(TransactionRequest request) {
        log.debug("开始分析交易: {}", request.getTransactionId());
        long startTime = System.currentTimeMillis();

        // 应用各种规则检测欺诈
        FraudDetectionResult result = applyRules(request);
        
        // 保存交易记录
        saveTransaction(request, result);
        
        // 计算处理时间
        long processingTime = System.currentTimeMillis() - startTime;
        result.setProcessingTimeMs(processingTime);
        
        log.info("交易 {} 分析完成，是否欺诈: {}, 原因: {}, 处理时间: {}ms", 
                request.getTransactionId(), result.isFraudulent(), 
                result.getFraudReason(), processingTime);
        
        return result;
    }
    
    private FraudDetectionResult applyRules(TransactionRequest request) {
        // 规则1: 检查交易金额是否超过阈值
        if (isAmountExceedingThreshold(request.getAmount())) {
            return buildFraudResult(request.getTransactionId(), "交易金额超过阈值");
        }
        
        // 规则2: 检查交易是否来自可疑国家
        if (isFromSuspiciousCountry(request.getSourceCountry())) {
            return buildFraudResult(request.getTransactionId(), "交易来自可疑国家");
        }
        
        // 规则3: 检查账户年龄是否过新（可能是欺诈账户）
        if (isAccountTooNew(request.getAccountCreationDate())) {
            return buildFraudResult(request.getTransactionId(), "账户创建时间过短");
        }
        
        // 通过所有规则，交易被认为是合法的
        return FraudDetectionResult.builder()
                .transactionId(request.getTransactionId())
                .fraudulent(false)
                .build();
    }
    
    private boolean isAmountExceedingThreshold(BigDecimal amount) {
        return amount.compareTo(maxTransactionAmount) > 0;
    }
    
    private boolean isFromSuspiciousCountry(String countryCode) {
        List<String> suspiciousCountryList = Arrays.asList(suspiciousCountries.split(","));
        return suspiciousCountryList.contains(countryCode);
    }
    
    private boolean isAccountTooNew(LocalDateTime accountCreationDate) {
        if (accountCreationDate == null) {
            return true; // 如果没有提供账户创建日期，视为可疑
        }
        
        // JDK 8兼容的方式计算天数
        long accountAgeInDays = ChronoUnit.DAYS.between(accountCreationDate, LocalDateTime.now());
        return accountAgeInDays < minAccountAgeDays;
    }
    
    private FraudDetectionResult buildFraudResult(String transactionId, String reason) {
        return FraudDetectionResult.builder()
                .transactionId(transactionId)
                .fraudulent(true)
                .fraudReason(reason)
                .build();
    }
    
    private void saveTransaction(TransactionRequest request, FraudDetectionResult result) {
        Transaction transaction = Transaction.builder()
                .transactionId(request.getTransactionId())
                .accountId(request.getAccountId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .sourceCountry(request.getSourceCountry())
                .destinationCountry(request.getDestinationCountry())
                .timestamp(request.getTimestamp())
                .accountCreationDate(request.getAccountCreationDate())
                .ipAddress(request.getIpAddress())
                .deviceId(request.getDeviceId())
                .fraudulent(result.isFraudulent())
                .fraudReason(result.getFraudReason())
                .build();
                
        transactionRepository.save(transaction);
    }
} 