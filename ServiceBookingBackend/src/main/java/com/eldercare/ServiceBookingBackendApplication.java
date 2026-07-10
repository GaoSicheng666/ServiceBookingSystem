package com.eldercare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 老年人服务预约系统 —— 后端启动类。
 * 运行此类即可启动内嵌 Tomcat,对外提供 REST API。
 */
@SpringBootApplication
public class ServiceBookingBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceBookingBackendApplication.class, args);
    }
}
