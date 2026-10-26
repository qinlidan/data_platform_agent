package com.dataplatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 数据开发平台主应用类
 */
@SpringBootApplication
@EnableAsync
@MapperScan({"com.dataplatform.mapper", "com.dataplatform.auth.mapper"})
public class DataPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataPlatformApplication.class, args);
    }
}
