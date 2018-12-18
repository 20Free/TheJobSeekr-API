package com.thejobseekr.crawler.task.model;

import java.util.Map;
import java.util.HashMap;

public class Document {

	private final String reference;
	private Metadata metadata;
	private String content;

	public Document(String reference, Metadata metadata, String content) {
		this.reference = reference;
		this.metadata = metadata;
		this.content = content;
	}
	
	@SuppressWarnings("unchecked")
	public static Document fromMap(Map<String, Object> map) {
		String reference = (String)map.get("reference");
		HashMap<String, Object> metaMap = (HashMap<String, Object>)map.get("metadata");
		String title = (String)metaMap.get("title");
		String location = (String)metaMap.get("location");
		String content = (String)map.get("content");
		Metadata metadata = new Metadata(title, location);
		return new Document(reference, metadata, content);
	}

	/**
	 * Returns value of reference
	 * @return the url job refS
	 */
	public String getReference() {
		return reference;
	}

	/**
	 * Returns value of metadata
	 * @return metadata of doc
	 */
	public Metadata getMetadata() {
		return metadata;
	}

	/**
	 * Sets new value of metadata
	 * @param metadata of doc
	 */
	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}

	/**
	 * Returns value of content
	 * @return content of doc
	 */
	public String getContent() {
		return content;
	}

	/**
	 * Sets new value of content
	 * @param content of doc
	 */
	public void setContent(String content) {
		this.content = content;
	}
}
