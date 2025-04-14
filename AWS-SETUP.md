# AWS服务集成指南

## 概述

本文档描述了如何将欺诈检测系统与AWS的托管服务集成，以替代本地自建的Kafka和Zookeeper服务。

## 前提条件

- AWS账号及适当的权限
- AWS CLI已配置
- 已创建Amazon MSK集群或准备使用Amazon SQS

## 支持的AWS服务

1. **Amazon MSK** - 用于替代自建Kafka
2. **Amazon SQS** - 作为可选的消息队列服务
3. **Amazon RDS** - 用于替代H2内存数据库
4. **AWS Secrets Manager** - 用于管理敏感配置
5. **Amazon CloudWatch** - 用于日志管理

## 配置步骤

### 1. 准备环境变量

1. 复制示例环境变量文件:
   ```bash
   cp .env.aws.example .env
   ```

2. 编辑`.env`文件，填入实际的AWS服务配置:
   - AWS区域
   - MSK集群连接字符串
   - 数据库连接信息
   - 安全凭证配置

### 2. 准备Kafka信任库

如果使用Amazon MSK并启用了SSL:

1. 创建`kafka-secrets`目录:
   ```bash
   mkdir -p kafka-secrets
   ```

2. 下载Amazon信任证书:
   ```bash
   aws s3 cp s3://amazonroot-ca/SFSRootCAG2.pem kafka-secrets/
   ```

3. 创建信任库:
   ```bash
   keytool -import -file kafka-secrets/SFSRootCAG2.pem -alias rootca -keystore kafka-secrets/kafka.client.truststore.jks
   ```

### 3. 启动应用

使用AWS配置启动应用程序:

```bash
docker-compose -f docker-compose.yml -f docker-compose.aws.yml up -d
```

### 4. 验证连接

访问健康检查接口确认服务正常运行:

```bash
curl http://localhost:8080/check
```

## AWS服务配置详情

### Amazon MSK

欺诈检测系统使用Amazon MSK作为Kafka的托管替代方案:

1. 使用MSK集群中的引导服务器端点
2. 使用SSL安全连接
3. 利用IAM身份验证或SASL

配置通过环境变量`SPRING_KAFKA_BOOTSTRAP_SERVERS`和其他相关安全配置提供。

### AWS SQS (可选)

如果您选择使用SQS代替Kafka:

1. 需要修改服务配置以使用SQS客户端
2. 调整消息处理模式为轮询而非事件驱动

### AWS RDS

生产环境中，应使用AWS RDS替代内存数据库:

1. 支持PostgreSQL、MySQL或Aurora
2. 配置通过环境变量`SPRING_DATASOURCE_URL`提供

### AWS CloudWatch Logs

系统日志会自动推送到CloudWatch Logs:

1. 使用`awslogs`驱动
2. 日志组和流配置通过Docker Compose设置

## 常见问题

### 连接到MSK失败

检查:
- 安全组设置
- 网络ACL
- 服务端点是否正确
- 信任库配置

### SQS消息处理问题

检查:
- IAM权限
- 队列URL
- 可见性超时设置

### RDS连接失败

检查:
- 数据库安全组
- 凭证是否正确
- 数据库是否运行 