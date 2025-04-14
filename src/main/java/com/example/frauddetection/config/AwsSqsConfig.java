package com.example.frauddetection.config;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.jms.support.destination.DynamicDestinationResolver;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import javax.jms.Session;

/**
 * 统一的AWS SQS配置类
 * 同时支持JMS接口和AWS SDK v2接口
 * 可通过配置项启用或禁用相关功能
 */
@Configuration
@EnableJms
public class AwsSqsConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;
    
    @Value("${aws.credentials.access-key:#{null}}")
    private String accessKey;
    
    @Value("${aws.credentials.secret-key:#{null}}")
    private String secretKey;

    /**
     * AWS SQS客户端 (v1) - 用于JMS接口
     */
    @Bean
    @Primary
    public AmazonSQS amazonSQSClient() {
        if (accessKey != null && secretKey != null) {
            // 使用显式提供的凭证
            BasicAWSCredentials awsCredentials = new BasicAWSCredentials(accessKey, secretKey);
            return AmazonSQSClientBuilder.standard()
                    .withRegion(awsRegion)
                    .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                    .build();
        } else {
            // 尝试使用默认凭证链
            return AmazonSQSClientBuilder.standard()
                    .withRegion(awsRegion)
                    .withCredentials(new DefaultAWSCredentialsProviderChain())
                    .build();
        }
    }

    /**
     * AWS SQS客户端 (v2) - 直接使用SDK
     */
    @Bean
    public SqsClient sqsClient() {
        if (accessKey != null && secretKey != null) {
            // 使用显式提供的凭证
            AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);
            return SqsClient.builder()
                    .region(Region.of(awsRegion))
                    .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                    .build();
        } else {
            // 尝试使用默认凭证链
            return SqsClient.builder()
                    .region(Region.of(awsRegion))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        }
    }

    /**
     * SQS连接工厂 - 用于JMS接口
     */
    @Bean
    public SQSConnectionFactory sqsConnectionFactory(AmazonSQS amazonSQSClient) {
        return new SQSConnectionFactory(new ProviderConfiguration(), amazonSQSClient);
    }

    /**
     * JMS模板 - 用于发送JMS消息
     */
    @Bean
    @Primary
    public JmsTemplate jmsTemplate(SQSConnectionFactory sqsConnectionFactory) {
        JmsTemplate jmsTemplate = new JmsTemplate(sqsConnectionFactory);
        jmsTemplate.setMessageConverter(messageConverter());
        return jmsTemplate;
    }

    /**
     * JMS监听器容器工厂 - 用于接收JMS消息
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(SQSConnectionFactory sqsConnectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(sqsConnectionFactory);
        factory.setDestinationResolver(new DynamicDestinationResolver());
        factory.setConcurrency("3-10");
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);
        factory.setMessageConverter(messageConverter());
        return factory;
    }

    /**
     * JMS消息转换器 - 用于JSON序列化
     */
    @Bean
    public MessageConverter messageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        return converter;
    }
}