package com.example.frauddetection.cli;

import com.example.frauddetection.dto.TransactionRequest;
import com.example.frauddetection.messaging.SqsAwsProducer;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Scanner;
import java.util.UUID;

/**
 * SQS命令行工具，用于直接测试与AWS SQS的集成
 * 仅在激活cli配置文件和启用AWS SDK SQS时运行
 */
@Slf4j
@Component
@Profile("cli")
@ConditionalOnProperty(name = "fraud-detection.aws.sqs.sdk.enabled", havingValue = "true")
public class SqsCommandLineRunner implements CommandLineRunner {

    private final SqsAwsProducer sqsAwsProducer;

    @Autowired
    public SqsCommandLineRunner(SqsAwsProducer sqsAwsProducer) {
        this.sqsAwsProducer = sqsAwsProducer;
    }

    @Override
    public void run(String... args) {
        log.info("启动SQS命令行工具...");
        System.out.println("\n=== AWS SQS FIFO队列测试工具 ===");
        System.out.println("此工具允许您发送测试消息到AWS SQS FIFO队列");
        
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        
        while (running) {
            System.out.println("\n可用命令:");
            System.out.println("1 - 发送测试交易消息");
            System.out.println("2 - 发送欺诈警报消息");
            System.out.println("q - 退出");
            System.out.print("\n请选择: ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    sendTestTransaction(scanner);
                    break;
                case "2":
                    sendTestFraudAlert(scanner);
                    break;
                case "q":
                    running = false;
                    break;
                default:
                    System.out.println("无效选择，请重试");
            }
        }
        
        System.out.println("SQS测试工具已退出");
    }
    
    private void sendTestTransaction(Scanner scanner) {
        System.out.println("\n=== 发送测试交易 ===");
        
        // 生成随机交易ID
        String transactionId = UUID.randomUUID().toString();
        System.out.println("交易ID: " + transactionId);
        
        // 设置账户ID
        System.out.print("账户ID (默认: TEST-ACC-123): ");
        String accountId = scanner.nextLine().trim();
        if (accountId.isEmpty()) {
            accountId = "TEST-ACC-123";
        }
        
        // 设置交易金额
        System.out.print("交易金额 (默认: 1000.00): ");
        String amountStr = scanner.nextLine().trim();
        BigDecimal amount = amountStr.isEmpty() 
                ? new BigDecimal("1000.00") 
                : new BigDecimal(amountStr);
        
        // 创建交易请求
        TransactionRequest request = TransactionRequest.builder()
                .transactionId(transactionId)
                .accountId(accountId)
                .amount(amount)
                .currency("CNY")
                .sourceCountry("CN")
                .destinationCountry("US")
                .timestamp(LocalDateTime.now())
                .accountCreationDate(LocalDateTime.now().minusDays(30))
                .ipAddress("192.168.1.1")
                .deviceId("TEST-DEVICE-001")
                .build();
        
        try {
            // 发送交易到SQS队列
            String messageId = sqsAwsProducer.sendTransaction(request);
            System.out.println("消息已发送，消息ID: " + messageId);
        } catch (JsonProcessingException e) {
            System.out.println("发送消息失败: " + e.getMessage());
            log.error("发送交易消息失败", e);
        }
    }
    
    private void sendTestFraudAlert(Scanner scanner) {
        System.out.println("\n在当前实现中，欺诈警报是通过检测服务自动发送的");
        System.out.println("要测试欺诈警报，您可以发送一个金额非常大的交易");
        
        System.out.print("是否要发送一个大金额交易? (y/n): ");
        String confirm = scanner.nextLine().trim().toLowerCase();
        
        if ("y".equals(confirm)) {
            // 设置交易金额为一个大值，触发欺诈检测
            System.out.print("输入欺诈交易金额 (默认: 50000.00): ");
            String amountStr = scanner.nextLine().trim();
            BigDecimal amount = amountStr.isEmpty() 
                    ? new BigDecimal("50000.00") 
                    : new BigDecimal(amountStr);
            
            // 创建交易请求
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId(UUID.randomUUID().toString())
                    .accountId("TEST-FRAUD-ACC")
                    .amount(amount)
                    .currency("USD")
                    .sourceCountry("RU") // 使用可疑国家
                    .destinationCountry("US")
                    .timestamp(LocalDateTime.now())
                    .accountCreationDate(LocalDateTime.now().minusDays(1)) // 新账户
                    .ipAddress("10.0.0.1")
                    .deviceId("TEST-DEVICE-FRAUD")
                    .build();
            
            try {
                // 发送交易到SQS队列
                String messageId = sqsAwsProducer.sendTransaction(request);
                System.out.println("可能触发欺诈检测的消息已发送，消息ID: " + messageId);
            } catch (JsonProcessingException e) {
                System.out.println("发送消息失败: " + e.getMessage());
                log.error("发送欺诈交易消息失败", e);
            }
        }
    }
} 