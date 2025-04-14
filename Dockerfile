FROM registry.cn-hangzhou.aliyuncs.com/library/openjdk:8-jre-alpine

LABEL maintainer="xmagic1986@163.com"

WORKDIR /app

# 添加应用JAR包
COPY target/fraud-detection-service.jar fraud-detection-service.jar

# 创建日志目录
RUN mkdir -p /app/logs

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 设置JVM参数 - 为Undertow优化
ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+UseStringDeduplication -Dio.undertow.buffer-pool-size=1024 -Dio.undertow.direct-buffers=true -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heap-dump.hprof"

# 暴露应用端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar fraud-detection-service.jar"] 