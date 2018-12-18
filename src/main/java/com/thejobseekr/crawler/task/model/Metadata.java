package com.thejobseekr.crawler.task.model;

public class Metadata {
	private final String title;
	private String location;

	public Metadata(String title) {
		this.title = title;
	}
	
	public Metadata(String title, String location) {
		this.title = title;
		setLocation(location);
	}

	public String getTitle() {
		return title;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

}
