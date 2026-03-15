package com.rapidlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class RapidLinkApplication {

	public static void main(String[] args) {

        // Set timezone at JVM level
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(RapidLinkApplication.class, args);
	}

}
