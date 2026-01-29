package com.mycompany.ramesh.alertmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AlertMindApplication {

	public static void main(String[] args) {
		SpringApplication.run(AlertMindApplication.class, args);
	}

}
