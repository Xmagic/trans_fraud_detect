package com.example.frauddetection.sqs;

import com.example.frauddetection.dto.FraudDetectionResult;
import com.example.frauddetection.dto.TransactionRequest;
import com.example.frauddetection.messaging.SqsAwsConsumer;
import com.example.frauddetection.messaging.SqsAwsProducer;
import com.example.frauddetection.model.Transaction;
import com.example.frauddetection.repository.TransactionRepository;
import com.example.frauddetection.service.FraudDetectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * SQS集成测试类
 * 
 * 此类使用真实的AWS SQS连接进行消息发送和接收测试。
 * 测试使用"test"profile，配置在application-test.properties中，
 * 连接到在eu-north-1区域的实际AWS SQS队列：
 *
 * 特别说明：
 * - SqsClient是实际连接到AWS SQS的客户端，所以消息会真实发送到云端队列
 * - FraudDetectionService被mock以便测试，不会执行实际的欺诈检测逻辑
 * - 测试过程中会创建真实的SQS消息，测试完成后会将其删除
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
public class SqsIntegrationLiveTest {

    /**
     * SQS队列URL，通过配置文件注入
     * 这是真实的AWS SQS FIFO队列地址
     */
    @Value("${fraud-detection.aws.sqs.transaction-queue-url}")
    private String queueUrl;

    /**
     * AWS SQS客户端，由Spring注入
     * 这是一个真实连接到AWS的客户端
     */
    @Autowired
    private SqsClient sqsClient;

    /**
     * SQS消息生产者，用于发送消息到AWS SQS
     */
    @Autowired
    private SqsAwsProducer sqsAwsProducer;

    /**
     * 欺诈检测服务的模拟实现
     * 在测试中不会执行真实的欺诈检测逻辑
     */
    @Mock
    private FraudDetectionService fraudDetectionService;

    /**
     * 交易数据仓库，用于验证数据库中的交易记录
     */
    @Autowired
    private TransactionRepository transactionRepository;

    /**
     * SQS消息消费者，用于接收从队列发来的消息
     */
    private SqsAwsConsumer sqsAwsConsumer;
    
    /**
     * JSON对象映射器，用于消息的序列化和反序列化
     */
    private ObjectMapper objectMapper;
    
    /**
     * 用于测试异步消息处理的计数器
     */
    private CountDownLatch messageProcessedLatch;

    /**
     * 每个测试执行前的设置
     * 初始化ObjectMapper、计数器和SQS消费者
     */
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        messageProcessedLatch = new CountDownLatch(1);
        
        // 初始化消费者，使用mock的欺诈检测服务
        // 注意：这里使用的sqsClient是真实连接到AWS SQS的客户端
        sqsAwsConsumer = new SqsAwsConsumer(
                sqsClient, 
                objectMapper, 
                fraudDetectionService, 
                sqsAwsProducer, 
                queueUrl, 
                1);
        
        // 启动消费者
        sqsAwsConsumer.init();
    }

    /**
     * 每个测试执行后的清理
     * 确保停止SQS消费者
     */
    @AfterEach
    void tearDown() {
        // 确保测试后停止消费者
        if (sqsAwsConsumer != null) {
            sqsAwsConsumer.stop();
        }
    }

    /**
     * 测试发送消息并通过消费者接收消息
     * 
     * 这个测试验证：
     * 1. 消息能够成功发送到真实的AWS SQS队列
     * 2. 消费者能够从队列中接收到消息并处理
     * 3. 消息内容在发送和接收过程中保持一致
     * 
     * 此测试使用真实SQS队列，但使用mock的服务处理消息
     */
    @Test
    void testSendAndReceiveMessage() throws Exception {
        // 准备测试数据 - 创建唯一的交易ID
        String transactionId = "TEST-" + UUID.randomUUID().toString();
        TransactionRequest request = createTestTransactionRequest(transactionId);

        // 发送消息到真实的AWS SQS队列
        String messageId = sqsAwsProducer.sendTransaction(request);
        assertNotNull(messageId, "消息ID不应为空");
        System.out.println("已发送消息，ID: " + messageId);

        // 确保消费者处理消息 - 等待最多30秒
        Thread.sleep(5000); // 等待消费者处理消息

        // 验证欺诈检测服务是否接收到正确的请求
        // 直接从数据库中查询交易记录
        Transaction transaction = transactionRepository.findByTransactionId(transactionId);
                
        // 验证交易记录是否存在且内容正确
        assertNotNull(transaction, "应在数据库中找到交易记录");
        assertEquals(transactionId, transaction.getTransactionId(), "交易ID应该匹配");
        assertEquals(request.getAccountId(), transaction.getAccountId(), "账户ID应该匹配");
        assertEquals(0, request.getAmount().compareTo(transaction.getAmount()), "交易金额应该匹配");
        assertEquals(request.getCurrency(), transaction.getCurrency(), "货币类型应该匹配");
                
        System.out.println("成功验证交易已被处理并保存到数据库: " + transaction.getId());
    }

    /**
     * 测试手动发送和接收消息
     * 
     * 这个测试验证：
     * 1. 消息能够成功发送到真实的AWS SQS队列
     * 2. 可以使用SQS客户端直接接收消息，而不依赖消费者
     * 3. 消息内容在发送和接收过程中保持一致
     * 
     * 此测试完全使用真实的AWS SQS，直接调用API接收消息
     */
    @Test
    void testSendAndReceiveMessageManually() throws Exception {
        // 准备测试数据
        String transactionId = "MANUAL-" + UUID.randomUUID().toString();
        TransactionRequest request = createTestTransactionRequest(transactionId);

        // 发送消息到真实的AWS SQS队列
        String messageId = sqsAwsProducer.sendTransaction(request);
        assertNotNull(messageId, "消息ID不应为空");
        System.out.println("已发送消息，ID: " + messageId);

        // 直接使用SQS客户端接收消息 - 不依赖消费者
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(20) // 长轮询
                .build();

        // 等待接收消息
        AtomicReference<String> receivedTransactionId = new AtomicReference<>();
        boolean messageReceived = false;
        
        // 尝试最多3次，每次等待最多20秒
        for (int attempt = 0; attempt < 3 && !messageReceived; attempt++) {
            System.out.println("尝试接收消息，第" + (attempt + 1) + "次...");
            
            // 从真实的AWS SQS队列接收消息
            ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
            List<Message> messages = response.messages();
            
            for (Message message : messages) {
                String messageBody = message.body();
                TransactionRequest receivedRequest = objectMapper.readValue(messageBody, TransactionRequest.class);
                
                if (transactionId.equals(receivedRequest.getTransactionId())) {
                    receivedTransactionId.set(receivedRequest.getTransactionId());
                    messageReceived = true;
                    
                    // 删除已接收的消息，避免重复处理
                    sqsClient.deleteMessage(req -> req
                            .queueUrl(queueUrl)
                            .receiptHandle(message.receiptHandle()));
                    
                    System.out.println("成功接收并删除消息: " + receivedRequest.getTransactionId());
                    break;
                }
            }
            
            if (!messageReceived && attempt < 2) {
                // 如果没有收到消息并且还有重试机会，等待一段时间再重试
                Thread.sleep(5000);
            }
        }

        // 验证消息接收结果
        assertTrue(messageReceived, "应该成功接收消息");
        assertEquals(transactionId, receivedTransactionId.get(), "接收的交易ID应该与发送的一致");
    }

    /**
     * 创建测试用的交易请求对象
     * 
     * @param transactionId 交易ID
     * @return 构建好的交易请求对象
     */
    private TransactionRequest createTestTransactionRequest(String transactionId) {
        return TransactionRequest.builder()
                .transactionId(transactionId)
                .accountId("TEST-ACC-INT")
                .amount(new BigDecimal("1234.56"))
                .currency("CNY")
                .sourceCountry("CN")
                .destinationCountry("US")
                .timestamp(LocalDateTime.now())
                .accountCreationDate(LocalDateTime.now().minusDays(30))
                .ipAddress("192.168.1.100")
                .deviceId("TEST-DEVICE-INT")
                .build();
    }
} 