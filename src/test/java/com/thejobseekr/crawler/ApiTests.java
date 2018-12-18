package com.thejobseekr.crawler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.json.JSONArray;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.thejobseekr.crawler.task.model.Job;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
classes = CrawlerApplication.class)
@AutoConfigureMockMvc
public class ApiTests {
	
	@Autowired
	private MockMvc mvc;
	
	@Test
	public void testQuery() throws Exception {
		MvcResult result = mvc.perform(post("http://localhost:8080/api/query")
				.content("software"))
				.andDo(print())
				.andExpect(status().is(200))
				.andReturn();
		
		assertEquals(result.getResponse().getContentType(), "application/json;charset=UTF-8");
		//software is pretty common, so it should easily be in all 20 results
		JSONArray resultJsonArray = new JSONArray(result.getResponse().getContentAsString());
		for(int i = 0; i < resultJsonArray.length(); i++) {
			Job job = Job.fromJSON(resultJsonArray.getJSONObject(i));
			assertTrue(job.getContent().toLowerCase().contains("software") || job.getMetadata().getTitle().toLowerCase().contains("software"));
		}
	}
}
