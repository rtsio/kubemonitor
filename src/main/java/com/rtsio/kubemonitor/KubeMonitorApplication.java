package com.rtsio.kubemonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class KubeMonitorApplication {

	public static void main(String[] args) {
		SpringApplication.run(KubeMonitorApplication.class, args);
	}
}
