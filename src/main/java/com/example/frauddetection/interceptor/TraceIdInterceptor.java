package com.example.frauddetection.interceptor;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * 请求追踪拦截器
 * 为每个HTTP请求生成唯一的traceId
 */
@Component
public class TraceIdInterceptor implements HandlerInterceptor {

    private static final String TRACE_ID = "traceId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 生成traceId
        String traceId = UUID.randomUUID().toString();
        
        // 将traceId放入MDC
        MDC.put(TRACE_ID, traceId);
        
        // 将traceId添加到响应头，方便前端追踪
        response.addHeader("X-Trace-Id", traceId);
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求完成后清理MDC
        MDC.remove(TRACE_ID);
    }
} 