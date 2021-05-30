package com.rtsio.kubemonitor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "watchers.enabled=false")
class KubeMonitorApplicationTests {

	@Test
	void contextLoads() {
	}
}
