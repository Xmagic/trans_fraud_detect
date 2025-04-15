package com.example.frauddetection.controller;

import com.example.frauddetection.dto.FraudDetectionResult;
import com.example.frauddetection.dto.TransactionRequest;
import com.example.frauddetection.messaging.SqsAwsProducer;
import com.example.frauddetection.model.Transaction;
import com.example.frauddetection.repository.TransactionRepository;
import com.example.frauddetection.service.FraudDetectionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping("/mock")
public class SqsFraudDetectionController {

    private final FraudDetectionService fraudDetectionService;
    private final TransactionRepository transactionRepository;
    private final SqsAwsProducer sqsAwsProducer;
    private final AtomicBoolean isMocking = new AtomicBoolean(false);
    private final Random random = new Random();

    @Autowired
    public SqsFraudDetectionController(
            FraudDetectionService fraudDetectionService,
            TransactionRepository transactionRepository,
            SqsAwsProducer sqsAwsProducer) {
        this.fraudDetectionService = fraudDetectionService;
        this.transactionRepository = transactionRepository;
        this.sqsAwsProducer = sqsAwsProducer;
        log.info("SQS欺诈检测控制器已初始化 - SQS功能已启用");
    }

    /**
     * 启动mock数据发送
     */
    @GetMapping("/queue/start")
    public String startMocking() {
        if (isMocking.compareAndSet(false, true)) {
            log.info("开始发送mock交易数据");
            return "Mock数据发送已启动";
        }
        return "Mock数据发送已经在运行中";
    }

    /**
     * 停止mock数据发送
     */
    @GetMapping("/queue/stop")
    public String stopMocking() {
        if (isMocking.compareAndSet(true, false)) {
            log.info("停止发送mock交易数据");
            return "Mock数据发送已停止";
        }
        return "Mock数据发送未在运行";
    }

    /**
     * 每15秒发送一次mock数据
     */
    @Scheduled(fixedRate = 15000)
    public void sendMockTransaction() {
        if (!isMocking.get()) {
            return;
        }

        try {
            // 生成请求ID
            String requestId = UUID.randomUUID().toString();
            MDC.put("requestId", requestId);
            
            TransactionRequest request = generateMockTransaction();
            // 将requestId设置到交易请求中
            request.setRequestId(requestId);
            
            sqsAwsProducer.sendTransaction(request);
            log.info("已发送mock交易数据: {}", request.getTransactionId());
        } catch (JsonProcessingException e) {
            log.error("序列化mock交易数据失败", e);
        } catch (Exception e) {
            log.error("发送mock交易数据失败", e);
        } finally {
            MDC.remove("requestId");
        }
    }

    /**
     * 生成mock交易数据
     */
    private TransactionRequest generateMockTransaction() {
        // 随机决定是否生成可疑交易
        boolean isSuspicious = random.nextDouble() < 0.3; // 30%的概率生成可疑交易
        
        // 基础交易数据
        TransactionRequest request = TransactionRequest.builder()
                .transactionId(String.format("TXN-%d", System.currentTimeMillis()))
                .accountId("ACC-" + random.nextInt(1000))
                .timestamp(LocalDateTime.now())
                .accountCreationDate(LocalDateTime.now().minusDays(random.nextInt(365)))
                .build();

        if (isSuspicious) {
            // 生成可疑交易的特征
            switch (random.nextInt(4)) {
                case 0: // 大额交易
                    request.setAmount(BigDecimal.valueOf(50000 + random.nextDouble() * 50000)); // 5万-10万
                    request.setCurrency("USD");
                    request.setSourceCountry("US");
                    request.setDestinationCountry("CN");
                    request.setIpAddress(String.format("192.168.%d.%d", random.nextInt(255), random.nextInt(255)));
                    request.setDeviceId("DEV-" + random.nextInt(10000));
                    break;
                    
                case 1: // 频繁交易（短时间内多次交易）
                    request.setAmount(BigDecimal.valueOf(1000 + random.nextDouble() * 4000)); // 1千-5千
                    request.setCurrency("USD");
                    request.setSourceCountry("US");
                    request.setDestinationCountry("US");
                    request.setIpAddress(String.format("10.0.%d.%d", random.nextInt(255), random.nextInt(255)));
                    request.setDeviceId("DEV-" + random.nextInt(10000));
                    break;
                    
                case 2: // 跨境交易
                    request.setAmount(BigDecimal.valueOf(10000 + random.nextDouble() * 20000)); // 1万-3万
                    request.setCurrency("EUR");
                    request.setSourceCountry("DE");
                    request.setDestinationCountry("CN");
                    request.setIpAddress(String.format("172.16.%d.%d", random.nextInt(255), random.nextInt(255)));
                    request.setDeviceId("DEV-" + random.nextInt(10000));
                    break;
                    
                case 3: // 异常时间交易
                    request.setAmount(BigDecimal.valueOf(2000 + random.nextDouble() * 3000)); // 2千-5千
                    request.setCurrency("USD");
                    request.setSourceCountry("US");
                    request.setDestinationCountry("US");
                    // 设置异常时间（凌晨2-5点）
                    request.setTimestamp(LocalDateTime.now().withHour(2 + random.nextInt(3)).withMinute(random.nextInt(60)));
                    request.setIpAddress(String.format("192.168.%d.%d", random.nextInt(255), random.nextInt(255)));
                    request.setDeviceId("DEV-" + random.nextInt(10000));
                    break;
            }
        } else {
            // 生成正常交易
            request.setAmount(BigDecimal.valueOf(100 + random.nextDouble() * 9900)); // 100-10000
            request.setCurrency("USD");
            request.setSourceCountry("US");
            request.setDestinationCountry("US");
            request.setIpAddress(String.format("192.168.%d.%d", random.nextInt(255), random.nextInt(255)));
            request.setDeviceId("DEV-" + random.nextInt(10000));
        }

        return request;
    }
} 