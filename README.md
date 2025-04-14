# 实时欺诈检测系统

这是一个基于Spring Boot的实时欺诈检测系统，可以部署在Kubernetes集群上，用于检测和处理可疑的金融交易。

## 功能特点

- 使用基于规则的机制实时分析交易数据
- 支持通过REST API直接分析交易
- 支持通过Kafka消息队列异步处理交易
- 实现高可用性和弹性扩展
- 提供完整的日志记录和监控

## 技术栈

- Java 8
- Spring Boot 2.7.x
- Spring Data JPA
- Spring Kafka
- H2数据库 (开发和测试)
- JUnit 5 (测试)
- Docker
- Kubernetes

## 系统架构

系统包含以下主要组件：

1. **REST API** - 接收实时交易分析请求
2. **Kafka 消费者** - 从消息队列中异步处理交易
3. **规则引擎** - 应用预定义规则检测欺诈行为
4. **数据存储** - 存储交易和分析结果
5. **告警机制** - 检测到欺诈行为时触发告警

## 如何构建

```bash
./mvnw clean package
```

## 本地运行

### 前提条件

- Java 8+
- Maven 3.6+
- Docker (可选，用于本地Kafka)

### 启动Kafka (使用Docker)

```bash
docker-compose up -d
```

### 启动应用程序

```bash
./mvnw spring-boot:run
```

应用程序将在 http://localhost:8080 上运行。

## API 端点

- `POST /api/fraud-detection/analyze` - 实时分析交易
- `POST /api/fraud-detection/queue` - 将交易排队等待异步分析
- `GET /api/fraud-detection/transaction/{transactionId}` - 获取特定交易详情
- `GET /api/fraud-detection/transactions/account/{accountId}` - 获取特定账户的所有交易
- `GET /api/fraud-detection/transactions/fraudulent` - 获取所有欺诈交易

## Kubernetes部署

### 前提条件

- Kubernetes集群 (如AWS EKS, GCP GKE或Alibaba ACK)
- kubectl CLI

### 部署步骤

1. 构建并推送Docker镜像到镜像仓库

```bash
docker build -t your-registry/fraud-detection-service:latest .
docker push your-registry/fraud-detection-service:latest
```

2. 应用Kubernetes配置

```bash
kubectl apply -f kubernetes/
```

这将部署以下资源：
- Deployment
- Service
- HorizontalPodAutoscaler
- ConfigMap
- Secret (如果需要)

## 测试

### 单元测试

```bash
./mvnw test
```

### 集成测试

```bash
./mvnw verify
```

## 性能指标

系统设计目标：
- 平均响应时间: <100ms
- 每秒处理交易数: >1000 TPS
- 欺诈检测准确率: >95%

## 贡献指南

1. Fork 仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建Pull Request

## 许可证

MIT License

## AWS SQS配置说明

在使用AWS SQS进行消息队列集成时，需要正确配置AWS凭证。错误的示例如下：

```
2025-04-13 20:11:15.338 DEBUG 27476 --- [   scheduling-1] c.e.f.messaging.SqsAwsConsumer           : 从FIFO队列接收消息
2025-04-13 20:11:15.740 ERROR 27476 --- [   scheduling-1] c.e.f.messaging.SqsAwsConsumer           : 从SQS接收消息时发生错误

software.amazon.awssdk.core.exception.SdkClientException: Unable to load credentials from any of the providers in the chain...
```

### 配置AWS凭证

有两种方式配置AWS凭证：

#### 1. 通过application.yml配置

在`application.yml`中添加以下配置：

```yaml
aws:
  region: eu-north-1  # 替换为您的AWS区域
  credentials:
    access-key: YOUR_AWS_ACCESS_KEY  # 替换为您的访问密钥
    secret-key: YOUR_AWS_SECRET_KEY  # 替换为您的秘密密钥
```

#### 2. 通过环境变量配置

设置以下环境变量：

```
AWS_ACCESS_KEY_ID=YOUR_AWS_ACCESS_KEY
AWS_SECRET_ACCESS_KEY=YOUR_AWS_SECRET_KEY
AWS_REGION=eu-north-1
```

### 注意事项

- 请确保使用的AWS凭证具有对SQS队列的完整操作权限
- 不要在公共代码仓库中提交包含实际AWS凭证的配置文件
- 在生产环境中，建议使用环境变量或AWS IAM角色提供凭证，而不是硬编码在配置文件中

### SQS队列配置

当前系统使用的SQS队列URL:

```
transaction-queue-url: https://sqs.eu-north-1.amazonaws.com/399423262812/transaction-queue.fifo
fraud-alert-queue-url: https://sqs.eu-north-1.amazonaws.com/399423262812/fraud-alert-queue.fifo
result-queue-url: https://sqs.eu-north-1.amazonaws.com/399423262812/fraud-result-queue.fifo
```

请确保这些队列已在AWS SQS控制台中创建，并且您的AWS凭证有权访问它们。 