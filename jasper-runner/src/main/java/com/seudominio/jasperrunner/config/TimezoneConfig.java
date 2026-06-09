package com.seudominio.jasperrunner.config;

import com.seudominio.jasperrunner.util.AppTimeZone;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class TimezoneConfig {

    @PostConstruct
    void configure() {
        TimeZone.setDefault(TimeZone.getTimeZone(AppTimeZone.DISPLAY_ZONE));
    }
}
