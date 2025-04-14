package com.example.frauddetection.controller;

import com.example.frauddetection.dto.FraudDetectionResult;
import com.example.frauddetection.dto.TransactionRequest;
import com.example.frauddetection.model.Transaction;
import com.example.frauddetection.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FraudDetectionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionRepository transactionRepository;

    @AfterEach
    void tearDown() {
        transactionRepository.deleteAll();
    }

    @Test
    void shouldDetectFraudTransactionAndSaveResult() throws Exception {
        // 创建一个欺诈交易请求
        TransactionRequest request = TransactionRequest.builder()
                .transactionId("TX127")
                .accountId("ACC459")
                .amount(new BigDecimal("20000.00")) // 超过阈值的金额
                .currency("USD")
                .sourceCountry("US")
                .timestamp(LocalDateTime.now())
                .accountCreationDate(LocalDateTime.now().minusDays(60))
                .build();

        // 调用分析API
        MvcResult result = mockMvc.perform(post("/api/fraud-detection/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // 解析响应结果
        FraudDetectionResult detectionResult = objectMapper.readValue(
                result.getResponse().getContentAsString(), FraudDetectionResult.class);

        // 验证结果
        assertTrue(detectionResult.isFraudulent());
        assertEquals("交易金额超过阈值", detectionResult.getFraudReason());

        // 验证交易已保存到数据库
        Transaction savedTransaction = transactionRepository.findByTransactionId("TX127");
        assertNotNull(savedTransaction);
        assertTrue(savedTransaction.isFraudulent());
        assertEquals("交易金额超过阈值", savedTransaction.getFraudReason());
    }

    @Test
    void shouldRetrieveFraudulentTransactions() throws Exception {
        // 先创建并保存一个欺诈交易
        TransactionRequest fraudRequest = TransactionRequest.builder()
                .transactionId("TX128")
                .accountId("ACC460")
                .amount(new BigDecimal("20000.00")) // 超过阈值的金额
                .currency("USD")
                .sourceCountry("US")
                .timestamp(LocalDateTime.now())
                .accountCreationDate(LocalDateTime.now().minusDays(60))
                .build();

        mockMvc.perform(post("/api/fraud-detection/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fraudRequest)))
                .andExpect(status().isOk());

        // 再创建并保存一个正常交易
        TransactionRequest legitimateRequest = TransactionRequest.builder()
                .transactionId("TX129")
                .accountId("ACC461")
                .amount(new BigDecimal("5000.00"))
                .currency("USD")
                .sourceCountry("US")
                .timestamp(LocalDateTime.now())
                .accountCreationDate(LocalDateTime.now().minusDays(60))
                .build();

        mockMvc.perform(post("/api/fraud-detection/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(legitimateRequest)))
                .andExpect(status().isOk());

        // 获取所有欺诈交易
        MvcResult result = mockMvc.perform(get("/api/fraud-detection/transactions/fraudulent"))
                .andExpect(status().isOk())
                .andReturn();

        // 解析响应结果
        Transaction[] fraudulentTransactions = objectMapper.readValue(
                result.getResponse().getContentAsString(), Transaction[].class);

        // 验证结果
        assertEquals(1, fraudulentTransactions.length);
        assertEquals("TX128", fraudulentTransactions[0].getTransactionId());
    }
} 