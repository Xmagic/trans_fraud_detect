package com.example.frauddetection.controller;

import com.example.frauddetection.dto.FraudDetectionResult;
import com.example.frauddetection.dto.TransactionRequest;
import com.example.frauddetection.model.Transaction;
import com.example.frauddetection.repository.TransactionRepository;
import com.example.frauddetection.service.FraudDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/simple-fraud-detection")
public class SimpleFraudDetectionController {

    private final FraudDetectionService fraudDetectionService;
    private final TransactionRepository transactionRepository;

    @Autowired
    public SimpleFraudDetectionController(
            FraudDetectionService fraudDetectionService,
            TransactionRepository transactionRepository) {
        this.fraudDetectionService = fraudDetectionService;
        this.transactionRepository = transactionRepository;
        log.info("SimpleFraudDetectionController已初始化");
    }

    /**
     * 接收交易请求并直接执行欺诈检测，不使用任何消息队列
     * @param request 交易请求
     * @return 欺诈检测结果
     */
    @PostMapping("/detect")
    public ResponseEntity<FraudDetectionResult> detectFraud(@RequestBody TransactionRequest request) {
        log.info("收到欺诈检测请求: {}", request.getTransactionId());
        
        // 直接执行欺诈检测
        FraudDetectionResult result = fraudDetectionService.analyzeTransaction(request);
        
        log.info("完成欺诈检测: {}, 结果: {}", request.getTransactionId(), result.isFraudulent());
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/transaction/{transactionId}")
    public ResponseEntity<Transaction> getTransactionById(@PathVariable String transactionId) {
        Transaction transaction = transactionRepository.findByTransactionId(transactionId);
        if (transaction != null) {
            return ResponseEntity.ok(transaction);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/transactions/account/{accountId}")
    public ResponseEntity<List<Transaction>> getTransactionsByAccount(@PathVariable String accountId) {
        List<Transaction> transactions = transactionRepository.findByAccountId(accountId);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/transactions/fraudulent")
    public ResponseEntity<List<Transaction>> getFraudulentTransactions() {
        List<Transaction> fraudulentTransactions = transactionRepository.findByFraudulentIsTrue();
        return ResponseEntity.ok(fraudulentTransactions);
    }
} 