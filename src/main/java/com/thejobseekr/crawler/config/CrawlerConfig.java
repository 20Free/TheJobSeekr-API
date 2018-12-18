package com.thejobseekr.crawler.config;

import com.thejobseekr.crawler.task.JSONFilter;
import com.thejobseekr.crawler.task.NorconexHttpCrawler;


public class CrawlerConfig {

	public synchronized static void run() {
		//NorconexHttpCrawler.run();
		JSONFilter.run();
	}

}
