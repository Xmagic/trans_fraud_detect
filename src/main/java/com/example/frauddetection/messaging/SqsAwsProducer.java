package com.example.frauddetection.messaging;

import com.example.frauddetection.dto.TransactionRequest;
import com.example.frauddetection.model.Transaction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.UUID;

/**
 * 使用AWS SDK直接与SQS交互的生产者
 * 与JMS模式的SqsTransactionProducer不同，这个实现直接使用AWS SDK
 * 支持FIFO队列的特性，包括消息组ID和消息去重ID
 */
@Slf4j
@Service
public class SqsAwsProducer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String transactionQueueUrl;
    private final boolean isFifoQueue;

    @Autowired
    public SqsAwsProducer(
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            @Value("${fraud-detection.aws.sqs.transaction-queue-url}") String transactionQueueUrl
            ) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.transactionQueueUrl = transactionQueueUrl;
        // 检查队列URL是否以.fifo结尾
        this.isFifoQueue = transactionQueueUrl.endsWith(".fifo");
        log.info("AWS SDK SQS消息生产者已初始化, 队列类型: {}", isFifoQueue ? "FIFO" : "标准");
    }

    /**
     * 发送交易请求到SQS队列
     *
     * @param request 交易请求
     * @return 消息ID
     * @throws JsonProcessingException 如果序列化失败
     */
    public String sendTransaction(TransactionRequest request) throws JsonProcessingException {
        log.info("发送交易到SQS队列: {}", request.getTransactionId());
        String messageBody = objectMapper.writeValueAsString(request);
        
        // 构建基本请求
        SendMessageRequest.Builder requestBuilder = SendMessageRequest.builder()
                .queueUrl(transactionQueueUrl)
                .messageBody(messageBody);
        
        // 如果是FIFO队列，添加必要的属性
        if (isFifoQueue) {
            String messageGroupId = "transaction-group"; // 可以根据交易类型或账户ID进行分组
            String messageDeduplicationId = request.getTransactionId(); // 使用交易ID作为去重ID
            
            requestBuilder
                .messageGroupId(messageGroupId)
                .messageDeduplicationId(messageDeduplicationId);
                
            log.debug("FIFO队列配置 - 消息组ID: {}, 消息去重ID: {}", messageGroupId, messageDeduplicationId);
        }
        
        SendMessageRequest sendMessageRequest = requestBuilder.build();
        SendMessageResponse response = sqsClient.sendMessage(sendMessageRequest);
        log.debug("交易消息已发送, 消息ID: {}", response.messageId());
        return response.messageId();
    }

    /**
     * 发送欺诈警报到SQS队列
     *
     * @param fraudTransaction 欺诈交易
     * @return 消息ID
     * @throws JsonProcessingException 如果序列化失败
     */
//    public String sendFraudAlert(Transaction fraudTransaction) throws JsonProcessingException {
//        if (!fraudTransaction.isFraudulent()) {
//            log.warn("尝试发送非欺诈交易到欺诈警报队列，已忽略");
//            return null;
//        }
//
//        log.warn("发送欺诈警报到SQS队列, 交易ID: {}, 原因: {}",
//                fraudTransaction.getTransactionId(), fraudTransaction.getFraudReason());
//
//        String messageBody = objectMapper.writeValueAsString(fraudTransaction);
//
//        // 构建基本请求
//        SendMessageRequest.Builder requestBuilder = SendMessageRequest.builder()
//                .queueUrl(fraudAlertQueueUrl)
//                .messageBody(messageBody);
//
//        // 如果是FIFO队列，添加必要的属性
//        if (isFifoQueue) {
//            String messageGroupId = "fraud-alert-group"; // 欺诈警报组
//            // 使用UUID和交易ID组合作为去重ID
//            String messageDeduplicationId = fraudTransaction.getTransactionId() + "-" + UUID.randomUUID();
//
//            requestBuilder
//                .messageGroupId(messageGroupId)
//                .messageDeduplicationId(messageDeduplicationId);
//
//            log.debug("FIFO队列配置 - 消息组ID: {}, 消息去重ID: {}", messageGroupId, messageDeduplicationId);
//        }
//
//        SendMessageRequest sendMessageRequest = requestBuilder.build();
//        SendMessageResponse response = sqsClient.sendMessage(sendMessageRequest);
//        log.debug("欺诈警报已发送, 消息ID: {}", response.messageId());
//        return response.messageId();
//    }
}