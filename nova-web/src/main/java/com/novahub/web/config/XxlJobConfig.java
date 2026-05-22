package com.novahub.web.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class XxlJobConfig {

    @Value("${spring.application.name}")
    private String appName;

    @Value("${xxl-job.admin.addresses}")
    private String adminAddresses;

    @Value("${xxl-job.executor.appname}")
    private String executorAppName;

    @Value("${xxl-job.executor.port:0}")
    private int executorPort;

    @Value("${xxl-job.executor.ip:}")
    private String executorIp;

    @Value("${xxl-job.executor.logpath:}")
    private String logPath;

    @Value("${xxl-job.executor.logretentiondays:30}")
    private int logRetentionDays;

    @Value("${xxl-job.accessToken:}")
    private String accessToken;

    @Bean
    @ConditionalOnProperty(name = "xxl-job.enabled", havingValue = "true", matchIfMissing = true)
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info("初始化 XXL-Job 执行器...");
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAppname(executorAppName);
        xxlJobSpringExecutor.setPort(executorPort);
        xxlJobSpringExecutor.setIp(executorIp);
        xxlJobSpringExecutor.setLogPath(logPath);
        xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAccessToken(accessToken);

        log.info("XXL-Job 执行器初始化完成: appname={}, port={}, adminAddresses={}",
                executorAppName, executorPort, adminAddresses);
        return xxlJobSpringExecutor;
    }
}
