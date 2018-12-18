package com.thejobseekr.crawler.task;

import com.norconex.collector.core.filter.impl.RegexReferenceFilter;
import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.HttpCollectorConfig;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.crawler.URLCrawlScopeStrategy;
import com.norconex.committer.core.impl.JSONFileCommitter;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.handler.tagger.impl.KeepOnlyTagger;
import com.norconex.importer.handler.tagger.impl.LanguageTagger;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;

public class NorconexHttpCrawler {

	public synchronized static void run() {
		cleanCrawler();

		HttpCollectorConfig collectorConfig = getHttpCollectorConfig();
		HttpCrawlerConfig airbnbCrawlerConfig = getAirBnbCrawlerConfig();
		collectorConfig.setCrawlerConfigs(airbnbCrawlerConfig);

		//start collecting
		HttpCollector collector = new HttpCollector(collectorConfig);
		collector.start(NorconexHttpCollectorBooleans.RESUME_NON_COLLECTED.getVal());
	}

	private static void cleanCrawler() {

		cleanCollector();
		cleanAirBnbCrawler();

	}

	private static void cleanCollector() {

		File outputDir = new File(NorconexHttpCollectorStrings.OUTPUT_DIR.toString());
		if(outputDir.exists()) {
			try {
				FileUtils.forceDelete(outputDir);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static void cleanAirBnbCrawler() {

		File airbnbOutputDir = new File(AirBnbCrawlerStrings.WORK_DIR.toString());
		File airbnbJSONOutput = new File(AirBnbCrawlerStrings.OUTPUT_DIR.toString());
		if(airbnbOutputDir.exists()) {
			try {
				FileUtils.forceDelete(airbnbOutputDir);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if(airbnbJSONOutput.exists()) {
			try {
				FileUtils.forceDelete(airbnbJSONOutput);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static HttpCollectorConfig getHttpCollectorConfig() {

		HttpCollectorConfig collectorConfig = new HttpCollectorConfig();
		collectorConfig.setId(NorconexHttpCollectorStrings.ID.toString());
		collectorConfig.setLogsDir(NorconexHttpCollectorStrings.LOGS_DIR.toString());
		collectorConfig.setProgressDir(NorconexHttpCollectorStrings.PROGRESS_DIR.toString());

		return collectorConfig;
	}

	private static HttpCrawlerConfig getAirBnbCrawlerConfig() {

		HttpCrawlerConfig airbnbCrawlerConfig = new HttpCrawlerConfig();
		airbnbCrawlerConfig.setId(AirBnbCrawlerStrings.ID.toString());

		URLCrawlScopeStrategy urlCrawlScopeStrategy = new URLCrawlScopeStrategy();
		urlCrawlScopeStrategy.setStayOnDomain(AirBnbCrawlerBooleans.STAY_ON_DOMAIN.getVal());
		urlCrawlScopeStrategy.setStayOnPort(AirBnbCrawlerBooleans.STAY_ON_PORT.getVal());
		urlCrawlScopeStrategy.setStayOnProtocol(AirBnbCrawlerBooleans.STAY_ON_PROTOCOL.getVal());

		RegexReferenceFilter referenceFilter = new RegexReferenceFilter();
		referenceFilter.setRegex(AirBnbCrawlerStrings.FILTER_REGEX.toString());
		referenceFilter.setCaseSensitive(AirBnbCrawlerBooleans.CASE_SENSITIVE.getVal());
		airbnbCrawlerConfig.setUrlCrawlScopeStrategy(urlCrawlScopeStrategy);
		airbnbCrawlerConfig.setStartURLs(DEPARTMENTS);
		airbnbCrawlerConfig.setMaxDepth(AirBnbCrawlerInts.MAX_DEPTH.getVal());
		airbnbCrawlerConfig.setReferenceFilters(referenceFilter);
		airbnbCrawlerConfig.setWorkDir(new File(AirBnbCrawlerStrings.WORK_DIR.toString()));
		
		LanguageTagger languageTagger = new LanguageTagger();
		
		KeepOnlyTagger airbnbTagger = new KeepOnlyTagger();
		airbnbTagger.addField(AirBnbCrawlerStrings.TITLE_TAG.toString());
		
		ImporterConfig airbnbImporterConfig = new ImporterConfig();
		airbnbImporterConfig.setPreParseHandlers(languageTagger);
		airbnbImporterConfig.setPostParseHandlers(airbnbTagger);
		airbnbCrawlerConfig.setImporterConfig(airbnbImporterConfig);

		JSONFileCommitter airbnbJSONCommitter = new JSONFileCommitter();
		airbnbJSONCommitter.setDirectory(AirBnbCrawlerStrings.OUTPUT_DIR.toString());
		airbnbJSONCommitter.setDocsPerFile(Integer.MAX_VALUE);
		airbnbJSONCommitter.setPretty(AirBnbCrawlerBooleans.PRETTY_PRINT.getVal());
		airbnbCrawlerConfig.setCommitter(airbnbJSONCommitter);

		return airbnbCrawlerConfig;
	}

	private enum NorconexHttpCollectorStrings {
		ID("TheJobSeekr Collector"),
		OUTPUT_DIR("./output"),
		LOGS_DIR("./output/logs/"),
		PROGRESS_DIR("./output/progress/");

		private final String text;

		NorconexHttpCollectorStrings(final String text) {
			this.text = text;
		}

		@Override
		public String toString() {
			return text;
		}
	}

	private enum NorconexHttpCollectorBooleans {
		RESUME_NON_COLLECTED(true);

		private final boolean value;

		NorconexHttpCollectorBooleans(final boolean value) {
			this.value = value;
		}

		public boolean getVal() {
			return value;
		}
	}

	private enum AirBnbCrawlerStrings {
		WORK_DIR("./airbnbOutput/"),
		FILTER_REGEX("https://www.airbnb.ca/careers/departments/engineering|"
		             + "https://www.airbnb.ca/careers/departments/data-science-analytics|"
		             + "https://www.airbnb.ca/careers/departments/finance-accounting|"
		             + "https://www.airbnb.ca/careers/departments/business-development|"
		             + "https://www.airbnb.ca/careers/departments/customer-experience|"
		             + "https://www.airbnb.ca/careers/departments/design|"
		             + "https://www.airbnb.ca/careers/departments/employee-experience|"
		             + "https://www.airbnb.ca/careers/departments/information-technology|"
		             + "https://www.airbnb.ca/careers/departments/legal|"
		             + "https://www.airbnb.ca/careers/departments/localization|"
		             + "https://www.airbnb.ca/careers/departments/luxury-retreats|"
		             + "https://www.airbnb.ca/careers/departments/magical-trips|"
		             + "https://www.airbnb.ca/careers/departments/marketing-communications|"
		             + "https://www.airbnb.ca/careers/departments/operations|"
		             + "https://www.airbnb.ca/careers/departments/photography|"
		             + "https://www.airbnb.ca/careers/departments/product|"
		             + "https://www.airbnb.ca/careers/departments/public-policy|"
		             + "https://www.airbnb.ca/careers/departments/research|"
		             + "https://www.airbnb.ca/careers/departments/samara|"
		             + "https://www.airbnb.ca/careers/departments/talent|"
		             + "https://www.airbnb.ca/careers/departments/the-art-department|"
		             + "https://www.airbnb.ca/careers/departments/trust-and-safety|"
		             + "https://www.airbnb.ca/careers/departments/other|"
		             + "https://boards.greenhouse.io/contractorjobs/|"
		             + "https://boards.greenhouse.io/contractorjobs/[a-z,/,0-9]+|"
		             + "https://www.airbnb.ca/careers/departments/position/\\d+"),
		ID("TheJobSeekr Airbnb Crawler"),
		TITLE_TAG("title"),
		DESCRIPTION_TAG("description"),
		OUTPUT_DIR("./output/airbnbCrawledJSONFiles");

		private final String text;

		AirBnbCrawlerStrings(final String text) {
			this.text = text;
		}

		@Override
		public String toString() {
			return text;
		}
	}

	private enum AirBnbCrawlerBooleans {
		STAY_ON_DOMAIN(true),
		STAY_ON_PORT(true),
		STAY_ON_PROTOCOL(true),
		CASE_SENSITIVE(false),
		PRETTY_PRINT(true);

		private final boolean value;

		AirBnbCrawlerBooleans(final boolean value) {
			this.value = value;
		}

		public boolean getVal() {
			return value;
		}
	}

	enum AirBnbCrawlerInts {
		MAX_DEPTH(1);

		private final int val;

		AirBnbCrawlerInts(final int val) {
			this.val = val;
		}

		public int getVal() {
			return val;
		}
	}

	private static final String[] DEPARTMENTS = {
		"https://www.airbnb.ca/careers/departments/engineering",
		"https://www.airbnb.ca/careers/departments/data-science-analytics",
		"https://www.airbnb.ca/careers/departments/finance-accounting",
		"https://www.airbnb.ca/careers/departments/business-development",
		"https://www.airbnb.ca/careers/departments/customer-experience",
		"https://www.airbnb.ca/careers/departments/design",
		"https://www.airbnb.ca/careers/departments/employee-experience",
		"https://www.airbnb.ca/careers/departments/information-technology",
		"https://www.airbnb.ca/careers/departments/legal",
		"https://www.airbnb.ca/careers/departments/localization",
		"https://www.airbnb.ca/careers/departments/luxury-retreats",
		"https://www.airbnb.ca/careers/departments/magical-trips",
		"https://www.airbnb.ca/careers/departments/marketing-communications",
		"https://www.airbnb.ca/careers/departments/operations",
		"https://www.airbnb.ca/careers/departments/photography",
		"https://www.airbnb.ca/careers/departments/product",
		"https://www.airbnb.ca/careers/departments/public-policy",
		"https://www.airbnb.ca/careers/departments/research",
		"https://www.airbnb.ca/careers/departments/samara",
		"https://www.airbnb.ca/careers/departments/talent",
		"https://www.airbnb.ca/careers/departments/the-art-department",
		"https://www.airbnb.ca/careers/departments/trust-and-safety",
		"https://www.airbnb.ca/careers/departments/other",
		"https://boards.greenhouse.io/contractorjobs/"
	};
}
