package com.thejobseekr.crawler.task.model;

import java.util.List;
import java.util.Map;

public class Department extends Document {
	private String name;
	private List<String> jobTitles;

	public Department(String reference, Metadata metadata, String content, String name) {
		super(reference, metadata, content);
		setName(name);
	}
	
	@SuppressWarnings("unchecked")
	public static Department fromMap(Map<String, Object> map) {
		String reference = (String)map.get("reference");
		Map<String, Object> metaMap = (Map<String, Object>)map.get("metadata");
		String title = (String)metaMap.get("title");
		String location = (String)metaMap.get("location");
		String content = (String)map.get("content");
		Metadata metadata = new Metadata(title, location);
		String name = (String)map.get("name");
		return new Department(reference, metadata, content, name);
	}

	/**
	 * Returns value of name
	 * @return name of Department
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets new value of name
	 * @param name of Department
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns value of jobTitles
	 * @return
	 */
	public List<String> getJobTitles() {
		return jobTitles;
	}

	/**
	 * Sets new value of jobTitles
	 * @param
	 */
	public void setJobTitles(List<String> jobTitles) {
		this.jobTitles = jobTitles;
	}
}
