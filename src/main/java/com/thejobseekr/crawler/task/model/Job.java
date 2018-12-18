package com.thejobseekr.crawler.task.model;

import java.util.Map;

import org.json.JSONObject;

public class Job extends Document {
	private int jobID;

	public Job(String reference, Metadata metadata, String content, int jobID) {
		super(reference, metadata, content);
		setJobID(jobID);
	}

	
	@SuppressWarnings("unchecked")
	public static Job fromMap(Map<String, Object> map) {
		String reference = (String)map.get("reference");
		Map<String, Object> metaMap = (Map<String, Object>)map.get("metadata");
		String title = (String)metaMap.get("title");
		String location = (String)metaMap.get("location");
		Metadata metadata = new Metadata(title, location);
		String content = (String)map.get("content");
		int jobID = ((Long)map.get("jobID")).intValue();
		return new Job(reference, metadata, content, jobID);
	}
	
	public static Job fromJSON(JSONObject jobJson) {
		String reference = jobJson.getString("reference");
		JSONObject metadataJson = jobJson.getJSONObject("metadata");
		String title = metadataJson.getString("title");
		String location = metadataJson.getString("location");
		Metadata metadata = new Metadata(title, location);
		String content = jobJson.getString("content");
		int jobID = jobJson.getInt("jobID");
		return new Job(reference, metadata, content, jobID);
	}

	/**
	 * Returns value of jobID
	 * @return the ID of the job
	 */
	public int getJobID() {
		return jobID;
	}

	/**
	 * @param jobID the jobID to set
	 */
	public void setJobID(int jobID) {
		this.jobID = jobID;
	}
}
