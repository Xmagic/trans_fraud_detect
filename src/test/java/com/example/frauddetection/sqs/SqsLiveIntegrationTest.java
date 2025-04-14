package com.example.frauddetection.sqs;

import com.example.frauddetection.dto.TransactionRequest;
import com.example.frauddetection.messaging.SqsAwsProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 直接使用真实AWS SQS进行集成测试
 * 
 * 此类专门用于测试与真实AWS SQS的直接集成，不使用mock组件，
 * 直接连接到配置的AWS SQS FIFO队列: 
 * https://sqs.eu-north-1.amazonaws.com/399423262812/SQS_QUEUE.fifo
 * 
 * 与SqsIntegrationLiveTest的主要区别:
 * - 使用aws-sdk配置文件
 * - 没有使用任何模拟组件，所有连接和交互都是真实的
 * - 包含了批量消息发送和接收的测试
 * - 等待逻辑使用Awaitility库实现，代码更简洁
 * 
 * 这是最接近生产环境的集成测试，验证整个消息发送接收流程都能正常工作
 */
@SpringBootTest
@ActiveProfiles("aws-sdk")
public class SqsLiveIntegrationTest {

    /**
     * 真实AWS SQS队列的URL
     * 通过配置文件application-aws-sdk.properties注入
     */
    @Value("${fraud-detection.aws.sqs.transaction-queue-url}")
    private String queueUrl;

    /**
     * 真实的AWS SQS客户端
     * 用于直接与AWS SQS服务交互
     */
    @Autowired
    private SqsClient sqsClient;

    /**
     * SQS消息生产者
     * 用于向队列发送消息
     */
    @Autowired
    private SqsAwsProducer sqsAwsProducer;
    
    /**
     * 用于消息序列化和反序列化的对象映射器
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 测试单条消息的发送和接收
     * 
     * 这个测试:
     * 1. 生成一个带有唯一ID的测试交易
     * 2. 发送到真实的AWS SQS FIFO队列
     * 3. 等待并接收消息来验证传输是否成功
     * 4. 确认收到的消息内容与发送的一致
     * 5. 删除队列中的消息以保持队列清洁
     * 
     * 整个过程使用真实AWS服务，没有模拟组件
     */
    @Test
    void testSendAndReceiveMessage() throws Exception {
        // 生成唯一测试交易ID
        String transactionId = "AWS-LIVE-TEST-" + UUID.randomUUID().toString();
        TransactionRequest request = createTestTransaction(transactionId);

        // 发送消息到真实AWS队列
        String messageId = sqsAwsProducer.sendTransaction(request);
        assertNotNull(messageId, "消息ID不应为空");
        System.out.println("已发送消息到AWS SQS，消息ID: " + messageId);

        // 使用Awaitility等待并接收消息
        AtomicReference<String> receivedTransactionId = new AtomicReference<>();
        
        // 最多等待30秒，每5秒检查一次
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(5, TimeUnit.SECONDS)
                .until(() -> {
                    // 从队列接收消息
                    ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(10)
                            .waitTimeSeconds(5)
                            .build();
                    
                    // 发送接收请求到真实AWS SQS
                    ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
                    List<Message> messages = response.messages();
                    System.out.println("收到 " + messages.size() + " 条消息");
                    
                    // 检查消息中是否有我们发送的交易
                    for (Message message : messages) {
                        try {
                            TransactionRequest receivedRequest = objectMapper.readValue(
                                    message.body(), TransactionRequest.class);
                            
                            System.out.println("收到交易ID: " + receivedRequest.getTransactionId());
                            
                            if (transactionId.equals(receivedRequest.getTransactionId())) {
                                receivedTransactionId.set(receivedRequest.getTransactionId());
                                
                                // 删除已处理的消息，保持队列干净
                                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                        .queueUrl(queueUrl)
                                        .receiptHandle(message.receiptHandle())
                                        .build());
                                
                                System.out.println("成功接收并删除测试消息: " + transactionId);
                                return true;
                            }
                        } catch (Exception e) {
                            System.err.println("解析消息时出错: " + e.getMessage());
                        }
                    }
                    return false;
                });

        // 验证是否收到正确的消息
        assertEquals(transactionId, receivedTransactionId.get(), 
                "应该接收到与发送的交易ID相同的消息");
    }

    /**
     * 测试多条消息的批量发送和接收
     * 
     * 这个测试:
     * 1. 生成多个带有唯一ID的测试交易
     * 2. 将它们发送到真实的AWS SQS FIFO队列
     * 3. 验证所有消息都能被正确接收
     * 4. 删除队列中的所有测试消息
     * 
     * 此测试验证系统能否处理多条消息的场景，
     * 更接近真实的生产环境负载
     */
    @Test
    void testSendAndReceiveMultipleMessages() throws Exception {
        // 发送3个测试消息
        final int MESSAGE_COUNT = 3;
        String[] transactionIds = new String[MESSAGE_COUNT];
        String[] messageIds = new String[MESSAGE_COUNT];
        
        // 发送多条消息到真实的AWS SQS
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            transactionIds[i] = "AWS-MULTI-" + (i + 1) + "-" + UUID.randomUUID().toString();
            TransactionRequest request = createTestTransaction(transactionIds[i]);
            messageIds[i] = sqsAwsProducer.sendTransaction(request);
            assertNotNull(messageIds[i], "消息ID不应为空");
            System.out.println("已发送消息" + (i + 1) + "，ID: " + messageIds[i]);
            
            // 连续发送消息时增加小延迟，避免可能的限流
            Thread.sleep(200);
        }

        // 接收并验证所有消息
        final int[] receivedCount = {0}; // 使用数组以便在lambda中修改
        final boolean[] messageReceived = new boolean[MESSAGE_COUNT];
        
        // 最多等待60秒，确保有足够时间接收所有消息
        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(5, TimeUnit.SECONDS)
                .until(() -> {
                    boolean allReceived = true;
                    
                    // 检查是否所有消息都已接收
                    for (int i = 0; i < MESSAGE_COUNT; i++) {
                        if (!messageReceived[i]) {
                            allReceived = false;
                            break;
                        }
                    }
                    
                    if (allReceived) {
                        return true;
                    }
                    
                    // 从真实AWS SQS接收消息
                    ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(10)
                            .waitTimeSeconds(5)
                            .build();
                    
                    ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
                    List<Message> messages = response.messages();
                    System.out.println("批量测试 - 收到 " + messages.size() + " 条消息");
                    
                    if (messages.isEmpty()) {
                        return false;
                    }
                    
                    // 处理收到的每条消息
                    for (Message message : messages) {
                        try {
                            TransactionRequest receivedRequest = objectMapper.readValue(
                                    message.body(), TransactionRequest.class);
                            String receivedId = receivedRequest.getTransactionId();
                            
                            // 检查是否是我们发送的测试消息
                            for (int i = 0; i < MESSAGE_COUNT; i++) {
                                if (transactionIds[i].equals(receivedId) && !messageReceived[i]) {
                                    System.out.println("接收到测试消息" + (i + 1) + ": " + receivedId);
                                    messageReceived[i] = true;
                                    receivedCount[0]++;
                                    
                                    // 删除已处理的消息，保持队列干净
                                    sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                            .queueUrl(queueUrl)
                                            .receiptHandle(message.receiptHandle())
                                            .build());
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("解析消息时出错: " + e.getMessage());
                        }
                    }
                    
                    return receivedCount[0] == MESSAGE_COUNT;
                });

        // 验证是否收到所有消息
        assertEquals(MESSAGE_COUNT, receivedCount[0], "应该接收到所有发送的消息");
        
        // 验证每条消息都被标记为已接收
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            assertEquals(true, messageReceived[i], "消息 " + (i + 1) + " 应该被接收到");
        }
    }

    /**
     * 创建测试交易对象
     * 
     * @param transactionId 唯一交易ID
     * @return 构建好的交易请求对象
     */
    private TransactionRequest createTestTransaction(String transactionId) {
        return TransactionRequest.builder()
                .transactionId(transactionId)
                .accountId("AWS-LIVE-ACC")
                .amount(new BigDecimal("888.88"))
                .currency("CNY")
                .sourceCountry("CN")
                .destinationCountry("US")
                .timestamp(LocalDateTime.now())
                .accountCreationDate(LocalDateTime.now().minusDays(30))
                .ipAddress("203.0.113.1")
                .deviceId("AWS-LIVE-DEV")
                .build();
    }
} 