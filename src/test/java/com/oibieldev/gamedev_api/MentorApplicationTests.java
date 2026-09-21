package com.oibieldev.gamedev_api;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.oibieldev.gamedev_api.repository.DailyUsageCheck;

@SpringBootTest(properties = {
        "gemini.api.key=test-key",
        "spring.sql.init.mode=never"
})
@MockitoBean(types = {DataSource.class, DailyUsageCheck.class})
class MentorApplicationTests {

	@Test
	void contextLoads() {
	}

}
