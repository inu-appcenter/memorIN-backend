package com.memorin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "fcm.enabled=false")
class MemorinApplicationTests {

	@Test
	void contextLoads() {
	}

}
