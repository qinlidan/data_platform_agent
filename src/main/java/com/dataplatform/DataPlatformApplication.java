package com.dataplatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据开发平台主应用类
 */
@SpringBootApplication
@MapperScan("com.dataplatform.mapper")
public class DataPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataPlatformApplication.class, args);
    }
}
