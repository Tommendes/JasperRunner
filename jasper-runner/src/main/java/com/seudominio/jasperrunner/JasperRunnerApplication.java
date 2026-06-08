package com.seudominio.jasperrunner;

import com.seudominio.jasperrunner.config.JasperRunnerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JasperRunnerProperties.class)
public class JasperRunnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JasperRunnerApplication.class, args);
    }
}
