package com.example.frauddetection.controller;

import com.example.frauddetection.dto.FraudDetectionResult;
import com.example.frauddetection.dto.TransactionRequest;
//import com.example.frauddetection.messaging.SqsTransactionProducer;
import com.example.frauddetection.model.Transaction;
import com.example.frauddetection.repository.TransactionRepository;
import com.example.frauddetection.service.FraudDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/fraud-detection")
@ConditionalOnProperty(name = "fraud-detection.aws.sqs.enabled", havingValue = "true")
public class SqsFraudDetectionController {

    private final FraudDetectionService fraudDetectionService;
    private final TransactionRepository transactionRepository;
//    private final SqsTransactionProducer sqsTransactionProducer;

    @Autowired
    public SqsFraudDetectionController(
            FraudDetectionService fraudDetectionService,
            TransactionRepository transactionRepository ){
//            SqsTransactionProducer sqsTransactionProducer) {
        this.fraudDetectionService = fraudDetectionService;
        this.transactionRepository = transactionRepository;
//        this.sqsTransactionProducer = sqsTransactionProducer;
        log.info("SQS欺诈检测控制器已初始化 - SQS功能已启用");
    }

    @PostMapping("/analyze")
    public ResponseEntity<FraudDetectionResult> analyzeTransaction(@RequestBody TransactionRequest request) {
        log.info("收到分析交易请求: {}", request.getTransactionId());
        FraudDetectionResult result = fraudDetectionService.analyzeTransaction(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/queue")
    public ResponseEntity<String> queueTransaction(@RequestBody TransactionRequest request) {
        log.info("收到入队交易请求: {}", request.getTransactionId());
//        sqsTransactionProducer.sendTransaction(request);
        return ResponseEntity.ok("交易已入队到SQS进行异步分析");
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