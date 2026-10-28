package com.hostelmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HostelManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(HostelManagementApplication.class, args);
    }
}
