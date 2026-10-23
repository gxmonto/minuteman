package com.baseline.timetrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import com.baseline.timetrack.setup.FirstRunSetup;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TimetrackApplication {

	public static void main(String[] args) throws Exception {
		FirstRunSetup.runIfNeeded(args);
		SpringApplication.run(TimetrackApplication.class, args);
	}

}
