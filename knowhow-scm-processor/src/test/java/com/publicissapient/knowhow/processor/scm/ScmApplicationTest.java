package com.publicissapient.knowhow.processor.scm;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ScmApplicationTest {

	@Autowired
	private ApplicationContext context;

	@Test
	public void contextLoads() {
		assertNotNull(context);
	}

	@Test
	public void testMainMethod() {
		ScmApplication.main(new String[] {});
	}
}
