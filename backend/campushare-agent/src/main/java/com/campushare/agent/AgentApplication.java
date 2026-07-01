package com.campushare.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Agent 智能问答服务启动类。
 *
 * 扫描包说明：
 *  - com.campushare.agent            自身包（控制器/服务/Mapper/Feign/配置等）
 *  - com.campushare.common.utils     JwtUtils（@Component，需要被扫描）
 *  - com.campushare.common.result    Result/ResultCode（纯 POJO，扫描无害）
 *  - com.campushare.common.constant  常量类（扫描无害）
 *
 * 故意不扫描 com.campushare.common.exception：
 *  GlobalExceptionHandler 强依赖 jakarta.servlet API（HttpServletRequest 等），
 *  与本服务的 WebFlux Reactor 栈冲突。本服务后续会自己实现 WebFlux 版异常处理器。
 */
@SpringBootApplication(scanBasePackages = {
        "com.campushare.agent",
        "com.campushare.common.utils",
        "com.campushare.common.result",
        "com.campushare.common.constant"
})
@MapperScan("com.campushare.agent.mapper")
@EnableFeignClients(basePackages = "com.campushare.agent.feign")
@EnableRetry
@EnableScheduling
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
