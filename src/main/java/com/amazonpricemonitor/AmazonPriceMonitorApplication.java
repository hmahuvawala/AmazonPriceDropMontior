package com.amazonpricemonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AmazonPriceMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmazonPriceMonitorApplication.class, args);
    }
}
