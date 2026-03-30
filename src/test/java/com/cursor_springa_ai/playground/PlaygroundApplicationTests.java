package com.cursor_springa_ai.playground;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"zerodha.api-key=test-key",
		"zerodha.api-secret=test-secret",
		"spring.docker.compose.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class PlaygroundApplicationTests {

	@Test
	void contextLoads() {
	}

}
