package com.cursor_springa_ai.playground;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"zerodha.api-key=test-key",
		"zerodha.api-secret=test-secret"
})
class PlaygroundApplicationTests {

	@Test
	void contextLoads() {
	}

}
