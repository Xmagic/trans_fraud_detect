package com.example.frauddetection.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简单的健康检查控制器
 */
@Slf4j
@RestController
public class HealthCheckController {

    /**
     * 基本健康检查接口
     * @return 简单的问候消息
     */
    @GetMapping("/check")
    public String healthCheck() {
        log.info("接收到健康检查请求");
        return "Hello world from FraudDetect";
    }
} 