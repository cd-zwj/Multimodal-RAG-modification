package com.example.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DemoApplicationTests {

	@Test
	void mainClassIsLoadable() {
		assertDoesNotThrow(() -> Class.forName(DemoApplication.class.getName()));
	}

}
