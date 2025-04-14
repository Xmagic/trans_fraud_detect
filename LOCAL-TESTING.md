# 欺诈检测系统 - 本地测试指南

本文档提供了在本地环境中对AWS SQS集成进行测试的详细说明。

## 准备工作

### 1. 安装Docker

首先需要确保已安装Docker。可以从以下网址下载：
- [Docker Desktop](https://www.docker.com/products/docker-desktop/)

### 2. 启动LocalStack

[LocalStack](https://localstack.cloud/) 是一个模拟AWS云服务的本地开发环境，允许我们在本地测试AWS服务而无需实际连接到AWS云。

运行以下命令启动LocalStack：

```bash
docker run -p 4566:4566 localstack/localstack
```

等待LocalStack完全启动，当看到类似下面的消息时表示已就绪：

```
Ready.
```

### 3. 创建本地FIFO队列

在运行测试前，我们需要创建本地FIFO队列。可以使用AWS CLI配置为使用本地端点：

```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

# 创建FIFO队列
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name local-transaction-queue.fifo --attributes FifoQueue=true,ContentBasedDeduplication=true

aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name local-fraud-alert-queue.fifo --attributes FifoQueue=true,ContentBasedDeduplication=true

aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name local-result-queue.fifo --attributes FifoQueue=true,ContentBasedDeduplication=true
```

也可以依赖我们的测试类自动创建队列。

## 运行本地测试

### 方法1：运行自动化测试

我们提供了专门的测试类，用于在本地使用LocalStack进行SQS集成测试：

```bash
# 使用Maven运行特定测试类
mvn test -Dtest=SqsLocalStackTest

# 或者运行所有集成测试
mvn test -Dtest=*IntegrationTest
```

### 方法2：使用命令行工具

我们的项目包含一个命令行工具，可用于手动测试SQS集成：

```bash
# 使用localstack配置文件启动应用程序
mvn spring-boot:run -Dspring-boot.run.profiles=localstack,cli
```

启动后，按照屏幕提示操作，选择不同的选项来测试发送和接收消息。

### 方法3：使用AWS CLI验证

发送消息后，可以使用AWS CLI查看队列中的消息：

```bash
# 列出队列URL
aws --endpoint-url=http://localhost:4566 sqs list-queues

# 接收消息
aws --endpoint-url=http://localhost:4566 sqs receive-message --queue-url http://localhost:4566/000000000000/local-transaction-queue.fifo --attribute-names All --message-attribute-names All --max-number-of-messages 10
```

## 故障排除

### 常见问题

1. **LocalStack连接错误**

   错误信息: `Connection refused`
   
   解决方案: 确保LocalStack容器正在运行，并且4566端口可访问。

2. **FIFO队列错误**

   错误信息: `Missing required parameter MessageGroupId`
   
   解决方案: 确保在发送消息时包含了MessageGroupId和MessageDeduplicationId参数，或启用ContentBasedDeduplication属性。

3. **找不到队列**

   错误信息: `The specified queue does not exist`
   
   解决方案: 确保使用正确的队列URL或名称。本地队列URL通常格式为：`http://localhost:4566/000000000000/队列名称`。

## 参考资料

- [LocalStack 文档](https://docs.localstack.cloud/)
- [AWS SQS FIFO 队列](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues.html)
- [AWS CLI 文档](https://awscli.amazonaws.com/v2/documentation/api/latest/reference/sqs/index.html) 