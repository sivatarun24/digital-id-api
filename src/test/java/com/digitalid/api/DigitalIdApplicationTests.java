package com.digitalid.api;

import com.digitalid.api.service.MarketingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class DigitalIdApplicationTests {

	@MockitoBean MarketingService marketingService;

	@Test
	void contextLoads() {
	}
}
