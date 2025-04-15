package com.example.frauddetection.messaging;

import com.example.frauddetection.dto.TransactionRequest;
import com.example.frauddetection.service.FraudDetectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用AWS SDK v2直接从SQS接收消息的消费者
 * 支持标准队列和FIFO队列
 */
@Slf4j
@Component
public class SqsAwsConsumer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final FraudDetectionService fraudDetectionService;
    private final SqsAwsProducer sqsAwsProducer;
    private final String transactionQueueUrl;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final ThreadPoolExecutor executorService;
    private final boolean isFifoQueue;

    @Autowired
    public SqsAwsConsumer(
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            FraudDetectionService fraudDetectionService,
            SqsAwsProducer sqsAwsProducer,
            @Value("${fraud-detection.aws.sqs.transaction-queue-url}") String transactionQueueUrl,
            @Value("${fraud-detection.aws.sqs.consumer.threads:5}") int threadCount,
            @Value("${fraud-detection.aws.sqs.consumer.queue-size:100}") int queueSize,
            @Value("${fraud-detection.aws.sqs.consumer.keep-alive-seconds:60}") int keepAliveSeconds) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.fraudDetectionService = fraudDetectionService;
        this.sqsAwsProducer = sqsAwsProducer;
        this.transactionQueueUrl = transactionQueueUrl;
        
        // 创建线程池
        this.executorService = new ThreadPoolExecutor(
                threadCount, // 核心线程数
                threadCount, // 最大线程数
                keepAliveSeconds, // 空闲线程存活时间
                TimeUnit.SECONDS, // 时间单位
                new ArrayBlockingQueue<>(queueSize), // 有界队列
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "sqs-consumer-thread-" + threadNumber.getAndIncrement());
                        thread.setDaemon(true); // 设置为守护线程
                        return thread;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者运行
        );
        
        // 允许核心线程超时
        this.executorService.allowCoreThreadTimeOut(true);
        
        // 检查队列URL是否以.fifo结尾
        this.isFifoQueue = transactionQueueUrl.endsWith(".fifo");
        log.info("AWS SDK SQS消费者已初始化，使用{}个线程，队列类型: {}", threadCount, isFifoQueue ? "FIFO" : "标准");
    }

    @PostConstruct
    public void init() {
        isRunning.set(true);
        log.info("AWS SDK SQS消费者已启动");
    }

    /**
     * 定期从SQS队列接收消息
     */
    @Scheduled(fixedDelayString = "${fraud-detection.aws.sqs.consumer.polling-interval-ms:1000}")
    public void receiveMessages() {
        if (!isRunning.get()) {
            return;
        }

        try {
            // 生成消息ID
            String messageId = UUID.randomUUID().toString();
            MDC.put("messageId", messageId);

            // 构建接收消息请求
            ReceiveMessageRequest.Builder requestBuilder = ReceiveMessageRequest.builder()
                    .queueUrl(transactionQueueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(10);
            
            // FIFO队列可以添加特定的属性，如消息组ID等
            if (isFifoQueue) {
                // 在FIFO队列上使用长轮询
                log.debug("从FIFO队列接收消息");
            }
            
            ReceiveMessageRequest receiveRequest = requestBuilder.build();
            ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
            List<Message> messages = response.messages();

            if (!messages.isEmpty()) {
                log.info("从SQS队列接收到{}条消息", messages.size());
                
                for (Message message : messages) {
                    // 对于FIFO队列，我们可以记录消息组ID和序列号
                    if (isFifoQueue) {
                        log.debug("处理来自消息组: {} 的消息, 序列号: {}", 
                            message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID),
                            message.attributes().get(MessageSystemAttributeName.SEQUENCE_NUMBER));
                    }
                    
                    executorService.submit(() -> {
                        try {
                            MDC.put("messageId", messageId);
                            processMessage(message);
                        } finally {
                            MDC.remove("messageId");
                        }
                    });
                }
            }
        } catch (Exception e) {
            log.error("从SQS接收消息时发生错误", e);
        } finally {
            MDC.remove("messageId");
        }
    }

    /**
     * 处理单个SQS消息
     *
     * @param message SQS消息
     */
    private void processMessage(Message message) {
        try {
            log.debug("处理SQS消息: {}", message.messageId());
            
            // 将消息转换为交易请求对象
            TransactionRequest request = objectMapper.readValue(
                    message.body(), TransactionRequest.class);
            
            log.info("处理交易: {}", request.getTransactionId());
            
            // 执行欺诈检测
            fraudDetectionService.analyzeTransaction(request);
            
            // 删除已处理的消息
            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                    .queueUrl(transactionQueueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build();
                    
            sqsClient.deleteMessage(deleteRequest);
            log.debug("SQS消息处理完成并已从队列删除: {}", message.messageId());
            
        } catch (Exception e) {
            log.error("处理SQS消息时发生错误: {}", message.messageId(), e);
        }
    }

    /**
     * 停止消费者
     */
    public void stop() {
        isRunning.set(false);
        executorService.shutdown();
        log.info("AWS SDK SQS消费者已停止");
    }
} 