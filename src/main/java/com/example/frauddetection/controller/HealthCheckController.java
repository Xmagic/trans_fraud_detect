package com.example.frauddetection.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 简单的健康检查控制器
 */
@Slf4j
@RestController
public class HealthCheckController {

    private final AtomicBoolean isCpuBoomRunning = new AtomicBoolean(false);
    private Thread cpuBoomThread;

    /**
     * 基本健康检查接口
     * @return 简单的问候消息
     */
    @GetMapping("/check")
    public String healthCheck() {
        log.info("接收到健康检查请求");
        return "Hello world from FraudDetect";
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    @GetMapping("/cpu/boom")
    public String cpuBoom() {
        if (isCpuBoomRunning.compareAndSet(false, true)) {
            cpuBoomThread = new Thread(() -> {
                while (isCpuBoomRunning.get()) {
                    // 空循环消耗CPU
                }
            });
            cpuBoomThread.setName("cpu-boom-thread");
            cpuBoomThread.start();
            return "CPU压力测试已启动";
        }
        return "CPU压力测试已经在运行中";
    }

    @GetMapping("/cpu/stop")
    public String stopCpuBoom() {
        if (isCpuBoomRunning.compareAndSet(true, false)) {
            if (cpuBoomThread != null) {
                cpuBoomThread.interrupt();
            }
            return "CPU压力测试已停止";
        }
        return "CPU压力测试未在运行";
    }
} 