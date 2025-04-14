# AWS SQS 集成使用指南

本文档提供了关于如何使用欺诈检测系统与AWS SQS进行集成的详细说明。

## 配置项目

项目支持两种与AWS SQS集成的方式：
1. 使用 Amazon SQS Java Messaging Library (JMS接口)
2. 使用 AWS SDK for Java v2 (原生SDK接口)

## 配置选项

在`application.yml`或`application.properties`中配置以下参数：

```properties
# 启用AWS SQS SDK集成
fraud-detection.aws.sqs.sdk.enabled=true
# 禁用基于JMS的SQS集成
fraud-detection.aws.sqs.enabled=false
# 禁用Kafka
fraud-detection.kafka.enabled=false

# AWS区域配置
aws.region=eu-north-1

# SQS队列URL (FIFO队列)
fraud-detection.aws.sqs.transaction-queue-url=https://sqs.eu-north-1.amazonaws.com/399423262812/SQS_QUEUE.fifo
fraud-detection.aws.sqs.fraud-alert-queue-url=https://sqs.eu-north-1.amazonaws.com/399423262812/SQS_QUEUE.fifo
fraud-detection.aws.sqs.result-queue-url=https://sqs.eu-north-1.amazonaws.com/399423262812/SQS_QUEUE.fifo
```

## 使用方式

### 1. 在Spring Boot应用中发送消息

```java
@Autowired
private SqsAwsProducer sqsAwsProducer;

public void sendTransaction(TransactionRequest request) {
    try {
        String messageId = sqsAwsProducer.sendTransaction(request);
        log.info("消息已发送，ID: {}", messageId);
    } catch (JsonProcessingException e) {
        log.error("发送消息失败", e);
    }
}
```

### 2. 使用命令行工具测试

项目提供了一个命令行工具，用于快速测试与SQS的集成。使用以下命令启动：

```bash
# 使用cli配置文件启动应用
java -jar fraud-detection-system.jar --spring.profiles.active=cli
```

命令行工具提供了直观的界面，允许您：
- 发送测试交易消息到SQS队列
- 发送模拟欺诈交易消息进行测试

### 3. 使用AWS控制台查看消息

您可以通过AWS SQS控制台查看发送的消息：
1. 登录AWS管理控制台
2. 导航到SQS服务
3. 选择`SQS_QUEUE.fifo`队列
4. 点击"发送和接收消息"进行测试

## FIFO队列特性说明

系统使用了AWS SQS FIFO队列，具有以下特性：
- 严格的消息顺序保证
- 只处理一次语义（不会重复处理）
- 需要指定消息组ID和消息去重ID

系统自动处理这些FIFO特定的属性：
- 对交易消息，使用交易ID作为去重ID
- 对欺诈警报消息，使用唯一生成的ID作为去重ID
- 使用静态消息组ID来确保消息按顺序处理

## 故障排除

### 权限问题

确保您的AWS凭证有足够的权限访问SQS队列。需要的最小权限包括：
- `sqs:SendMessage`
- `sqs:ReceiveMessage`
- `sqs:DeleteMessage`

### 可见性超时设置

如果消息处理后又重新出现在队列中，请检查队列的可见性超时设置。确保设置的时间足够长，让处理器可以完成消息处理。

### 连接问题

如果出现连接错误，请检查：
1. AWS凭证是否正确
2. 网络连接是否正常
3. 队列URL是否正确指向了eu-north-1区域的队列

## 参考资料

- [AWS SQS开发者指南](https://docs.aws.amazon.com/sqs/latest/developerguide/welcome.html)
- [AWS SDK for Java V2文档](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)
- [AWS SQS FIFO队列](https://docs.aws.amazon.com/sqs/latest/developerguide/FIFO-queues.html) 