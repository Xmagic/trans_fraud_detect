package com.example.frauddetection.service;

import com.example.frauddetection.dto.FraudDetectionResult;
import com.example.frauddetection.dto.TransactionRequest;
import com.example.frauddetection.repository.TransactionRepository;
import com.example.frauddetection.service.impl.RuleBasedFraudDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

class RuleBasedFraudDetectionServiceTest {

    private RuleBasedFraudDetectionService fraudDetectionService;

    @Mock
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fraudDetectionService = new RuleBasedFraudDetectionService(transactionRepository);
        
        // 设置规则属性的值
        ReflectionTestUtils.setField(fraudDetectionService, "maxTransactionAmount", new BigDecimal("10000.00"));
        ReflectionTestUtils.setField(fraudDetectionService, "suspiciousCountries", "NG,RU,CN,VN");
        ReflectionTestUtils.setField(fraudDetectionService, "minAccountAgeDays", 30);
    }

    @Test
    void shouldDetectFraudWhenAmountExceedsThreshold() {
        // 创建一个超过金额阈值的交易
        TransactionRequest request = TransactionRequest.builder()
                .transactionId("TX123")
                .accountId("ACC456")
                .amount(new BigDecimal("15000.00"))
                .currency("USD")
                .sourceCountry("US")
                .timestamp(LocalDateTime.now())
                .accountCreationDate(LocalDateTime.now().minusDays(60))
                .build();

        // 执行欺诈检测
        FraudDetectionResult result = fraudDetectionService.analyzeTransaction(request);

        // 验证结果
        assertTrue(result.isFraudulent());
        assertEquals("交易金额超过阈值", result.getFraudReason());
        // 验证交易被保存
        verify(transactionRepository).save(any());
    }

    @Test
    void shouldDetectFraudWhenFromSuspiciousCountry() {
        // 创建一个来自可疑国家的交易
        TransactionRequest request = TransactionRequest.builder()
                .transactionId("TX124")
                .accountId("ACC456")
                .amount(new BigDecimal("5000.00"))
                .currency("USD")
                .sourceCountry("CN")
                .timestamp(LocalDateTime.now())
                .accountCreationDate(LocalDateTime.now().minusDays(60))
                .build();

        // 执行欺诈检测
        FraudDetectionResult result = fraudDetectionService.analyzeTransaction(request);

        // 验证结果
        assertTrue(result.isFraudulent());
        assertEquals("交易来自可疑国家", result.getFraudReason());
    }

    @Test
    void shouldDetectFraudWhenAccountIsTooNew() {
        // 创建一个账户年龄过短的交易
        TransactionRequest request = TransactionRequest.builder()
                .transactionId("TX125")
                .accountId("ACC457")
                .amount(new BigDecimal("5000.00"))
                .currency("USD")
                .sourceCountry("US")
                .timestamp(LocalDateTime.now())
                .accountCreationDate(LocalDateTime.now().minusDays(10))
                .build();

        // 执行欺诈检测
        FraudDetectionResult result = fraudDetectionService.analyzeTransaction(request);

        // 验证结果
        assertTrue(result.isFraudulent());
        assertEquals("账户创建时间过短", result.getFraudReason());
    }

    @Test
    void shouldNotDetectFraudForLegitimateTransaction() {
        // 创建一个合法的交易
        TransactionRequest request = TransactionRequest.builder()
                .transactionId("TX126")
                .accountId("ACC458")
                .amount(new BigDecimal("5000.00"))
                .currency("USD")
                .sourceCountry("US")
                .timestamp(LocalDateTime.now())
                .accountCreationDate(LocalDateTime.now().minusDays(60))
                .build();

        // 执行欺诈检测
        FraudDetectionResult result = fraudDetectionService.analyzeTransaction(request);

        // 验证结果
        assertFalse(result.isFraudulent());
        assertNull(result.getFraudReason());
    }
} 