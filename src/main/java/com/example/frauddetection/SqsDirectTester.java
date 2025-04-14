package com.example.frauddetection;

import com.example.frauddetection.dto.TransactionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

/**
 * 直接SQS测试器
 * 不依赖Spring Boot或任何其他框架
 * 可以直接运行来测试AWS SQS连接
 */
public class SqsDirectTester {

    // AWS SQS FIFO队列URL
    private static final String QUEUE_URL = "https://sqs.eu-north-1.amazonaws.com/399423262812/SQS_QUEUE.fifo";
    
    public static void main(String[] args) {
        try {
            System.out.println("=== AWS SQS 直接测试工具 ===");
            System.out.println("连接到队列: " + QUEUE_URL);
            
            // 创建SQS客户端
            SqsClient sqsClient = SqsClient.builder()
                    .region(Region.EU_NORTH_1) // 使用eu-north-1区域
                    .build();
            
            // 创建JSON序列化工具
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.findAndRegisterModules(); // 支持Java 8日期时间类
            
            // 显示菜单
            Scanner scanner = new Scanner(System.in);
            boolean running = true;
            
            while (running) {
                System.out.println("\n可用选项:");
                System.out.println("1. 发送测试消息");
                System.out.println("2. 接收消息");
                System.out.println("3. 清空队列");
                System.out.println("0. 退出");
                System.out.print("\n请选择: ");
                
                int choice = -1;
                try {
                    choice = Integer.parseInt(scanner.nextLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println("请输入有效的数字!");
                    continue;
                }
                
                switch (choice) {
                    case 0:
                        running = false;
                        break;
                    case 1:
                        sendTestMessage(sqsClient, objectMapper);
                        break;
                    case 2:
                        receiveMessages(sqsClient, objectMapper);
                        break;
                    case 3:
                        purgeQueue(sqsClient);
                        break;
                    default:
                        System.out.println("无效的选择!");
                }
            }
            
            System.out.println("程序已退出");
            
        } catch (Exception e) {
            System.err.println("发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 发送测试消息到SQS队列
     */
    private static void sendTestMessage(SqsClient sqsClient, ObjectMapper objectMapper) {
        try {
            // 创建测试交易
            String transactionId = "DIRECT-TEST-" + UUID.randomUUID().toString();
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId(transactionId)
                    .accountId("DIRECT-TEST-ACCOUNT")
                    .amount(new BigDecimal("1234.56"))
                    .currency("CNY")
                    .sourceCountry("CN")
                    .destinationCountry("US")
                    .timestamp(LocalDateTime.now())
                    .accountCreationDate(LocalDateTime.now().minusDays(30))
                    .ipAddress("192.168.1.1")
                    .deviceId("DIRECT-TEST-DEVICE")
                    .build();
            
            // 序列化为JSON
            String messageBody = objectMapper.writeValueAsString(request);
            
            // 创建SQS消息请求
            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(QUEUE_URL)
                    .messageBody(messageBody)
                    // 必须为FIFO队列提供这些属性
                    .messageGroupId("direct-test-group")
                    .messageDeduplicationId(transactionId) // 使用交易ID作为去重ID
                    .build();
            
            // 发送消息
            SendMessageResponse response = sqsClient.sendMessage(sendMessageRequest);
            
            // 显示结果
            System.out.println("消息已发送!");
            System.out.println("交易ID: " + transactionId);
            System.out.println("消息ID: " + response.messageId());
            System.out.println("序列号: " + response.sequenceNumber());
            
        } catch (Exception e) {
            System.err.println("发送消息时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 从SQS队列接收消息
     */
    private static void receiveMessages(SqsClient sqsClient, ObjectMapper objectMapper) {
        try {
            System.out.println("正在接收消息...");
            
            // 创建接收消息请求
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(QUEUE_URL)
                    .maxNumberOfMessages(10) // 最多接收10条消息
                    .waitTimeSeconds(5) // 等待5秒(长轮询)
                    .build();
            
            // 接收消息
            ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
            List<Message> messages = response.messages();
            
            if (messages.isEmpty()) {
                System.out.println("队列中没有消息");
                return;
            }
            
            System.out.println("收到 " + messages.size() + " 条消息:");
            
            // 处理每条消息
            for (int i = 0; i < messages.size(); i++) {
                Message message = messages.get(i);
                System.out.println("\n消息 #" + (i + 1) + ":");
                System.out.println("消息ID: " + message.messageId());
                
                try {
                    // 尝试解析为TransactionRequest
                    TransactionRequest request = objectMapper.readValue(
                            message.body(), TransactionRequest.class);
                    System.out.println("交易ID: " + request.getTransactionId());
                    System.out.println("账户ID: " + request.getAccountId());
                    System.out.println("金额: " + request.getAmount() + " " + request.getCurrency());
                } catch (Exception e) {
                    // 如果解析失败，显示原始消息
                    System.out.println("无法解析为交易请求，原始消息:");
                    System.out.println(message.body());
                }
                
                // 询问是否删除消息
                System.out.print("是否删除此消息? (y/n): ");
                Scanner scanner = new Scanner(System.in);
                String answer = scanner.nextLine().trim().toLowerCase();
                
                if ("y".equals(answer)) {
                    // 删除消息
                    DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                            .queueUrl(QUEUE_URL)
                            .receiptHandle(message.receiptHandle())
                            .build();
                    
                    sqsClient.deleteMessage(deleteRequest);
                    System.out.println("消息已删除");
                } else {
                    System.out.println("消息保留在队列中");
                }
            }
            
        } catch (Exception e) {
            System.err.println("接收消息时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 清空SQS队列中的所有消息
     */
    private static void purgeQueue(SqsClient sqsClient) {
        try {
            System.out.print("确定要清空队列中的所有消息吗? (y/n): ");
            Scanner scanner = new Scanner(System.in);
            String answer = scanner.nextLine().trim().toLowerCase();
            
            if ("y".equals(answer)) {
                PurgeQueueRequest purgeRequest = PurgeQueueRequest.builder()
                        .queueUrl(QUEUE_URL)
                        .build();
                
                sqsClient.purgeQueue(purgeRequest);
                System.out.println("队列已清空");
            } else {
                System.out.println("操作已取消");
            }
        } catch (Exception e) {
            System.err.println("清空队列时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 