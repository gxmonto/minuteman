package com.baseline.timetrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TimetrackApplication {

	public static void main(String[] args) {
		SpringApplication.run(TimetrackApplication.class, args);
	}

}
