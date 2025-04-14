package com.example.frauddetection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 欺诈检测系统应用程序入口
 * 
 * 支持多种模式：
 * 1. 普通REST API模式
 * 2. AWS SQS集成模式
 */
@SpringBootApplication
@EnableScheduling
public class FraudDetectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudDetectionApplication.class, args);
    }
} 