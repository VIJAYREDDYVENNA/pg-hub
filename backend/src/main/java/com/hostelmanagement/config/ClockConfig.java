package com.hostelmanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(AppProperties props) {
        return Clock.system(ZoneId.of(props.timeZone()));
    }
}
