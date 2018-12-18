package com.thejobseekr.crawler.task;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.thejobseekr.crawler.task.model.Document;
import com.thejobseekr.crawler.task.model.Metadata;
import com.thejobseekr.crawler.task.model.Job;
import com.thejobseekr.crawler.task.model.Department;

import com.squareup.moshi.JsonReader;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;

import org.apache.commons.io.FileUtils;
import org.apache.jena.atlas.lib.StrUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.text.EntityDefinition;
import org.apache.jena.query.text.TextDatasetFactory;
import org.apache.jena.query.text.TextIndexConfig;
import org.apache.jena.query.text.TextQuery;

import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.api.core.ApiFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JSONFilter {

	private static Logger logger = LoggerFactory.getLogger(JSONFilter.class);
	
	private static Dataset jenaDataset = null;

	public synchronized static void run() {
		//cleanJena();
		//readInJSONDoc();   //reads in JSON Doc and writes turtle(RDF) to file(s)
		
		doTextSearchQuery(JSONFilterJena.EX_SEARCH_QEURY.toString()); //Queries it all
	}

	

	private static void cleanJena() {
		File dbFolder = new File(JSONFilterJena.JENA_DIR.toString());
		try {
			FileUtils.forceDelete(dbFolder);
		} catch (IOException e) {
			logger.error(JSONFilterJena.FOLDER_NOT_EXISTS.toString());
		}
	}

	private static void readInJSONDoc() {
		File airbnbJSONFolder = new File(JSONFilterCrawler.AIR_BNB_OUTPUT_DIR.toString());
		File[] arr = airbnbJSONFolder.listFiles();
		for(File f : arr) {
			try {
				List<Document> documents = readJsonStream(Okio.buffer(Okio.source(f)));
				postDocsToFirebase(documents);
				List<Job> filteredJobs = filterJobs(documents);
				List<Department> filteredDepartments = filterDepartments(documents);
				createTTLIndexFile(filteredJobs, filteredDepartments);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		logger.info(JSONFilterDocs.FINISHED_FILTERING.toString());
	}

	private static List<Document> readJsonStream(BufferedSource source) throws IOException {
		JsonReader reader = JsonReader.of(source);
		try {
			return readDocAddArray(reader);
		} finally {
			reader.close();
		}
	}

	private static List<Document> readDocAddArray(JsonReader reader) throws IOException {
		List<Document> documents = new ArrayList<Document>();

		reader.beginArray();
		while(reader.hasNext()) {
			Document doc = readDocAdd(reader);
			if(!(doc instanceof Department && ((Department)doc).getName().equals(""))) {
				documents.add(doc);
			}
		}
		reader.endArray();
		return documents;
	}

	private static Document readDocAdd(JsonReader reader) throws IOException {

		Document document = null;

		reader.beginObject();
		while(reader.hasNext()) {
			String name = reader.nextName();
			if(name.equals(JSONFilterDocs.JSON_DOC_ADD.toString())) {
				document = readDocument(reader);
			} else {
				reader.skipValue();
			}
		}
		reader.endObject();
		return document;
	}

	private static Document readDocument(JsonReader reader) throws IOException {
		String reference = null;
		Metadata metadata = null;
		String content = null;

		reader.beginObject();
		while(reader.hasNext()) {
			String name = reader.nextName();
			if(name.equals(JSONFilterDocs.JSON_REFERENCE.toString())) {
				reference = reader.nextString();
			} else if(name.equals(JSONFilterDocs.JSON_CONTENT.toString())) {
				content = reader.nextString();
				content = content.replaceAll("\\uFFFD", "\'");
				content = new String(content.getBytes(), "UTF-8");
			} else if(name.equals(JSONFilterDocs.JSON_METADATA.toString())) {
				String metadataString = reader.nextString();
				String titleDel = JSONFilterDocs.TITLE_DELIMETER.toString();
				metadataString = metadataString.substring(metadataString.indexOf(titleDel) + titleDel.length() + 1, metadataString.indexOf("]"));
				String title = readTitle(metadataString);
				metadata = new Metadata(title);
			} else {
				reader.skipValue();
			}
		}
		reader.endObject();

		metadata.setLocation(extractLocationFromDoc(reference, content));
		content = extractContentFromDoc(reference, content);

		int lastTrailingSepIndex = reference.lastIndexOf(HelperStrings.SEPARATOR.toString()) + 1;
		String docIDOrName = reference.substring(lastTrailingSepIndex);
		int docID = 0;
		try {
			docID = Integer.parseInt(docIDOrName);
			logger.info(docIDOrName);
		} catch(Exception e) {
			logger.error(JSONFilterDocs.FOUND_A_WORD.toString());
		}

		if(docID > 0) {
			return new Job(reference, metadata, content, Integer.parseInt(docIDOrName));
		} else {
			return new Department(reference, metadata, content, docIDOrName);
		}
	}

	private static String readTitle(String reader) throws IOException {
		if(reader.contains(JSONFilterDocs.CONTRACTOR_CONTAINS.toString())) {
			reader = reader.replace(JSONFilterDocs.CONTRACTOR_REPLACE_BEG.toString(), HelperStrings.EMPTY.toString());
			reader = reader.replace(JSONFilterDocs.CONTRACTOR_REPLACE_END.toString(), HelperStrings.EMPTY.toString());
		} else if(reader.contains(JSONFilterDocs.CAREER_CONTAINS.toString())) {
			reader = reader.replace(JSONFilterDocs.CAREER_REPLACE.toString(), HelperStrings.EMPTY.toString());
		}
		return reader;
	}

	private static String extractContentFromDoc(String ref, String content) {
		if (ref.matches(JSONFilterDocs.CONTRACTOR_FILTER.toString())) { //it's a contract job
			int firstIndex = content.indexOf(JSONFilterDocs.CONTRACTOR_BEFORE_CONTENT.toString());
			int secondIndex = content.indexOf(JSONFilterDocs.CONTRACTOR_AFTER_CONTENT.toString());
			content = content.substring(firstIndex + JSONFilterDocs.CONTRACTOR_BEFORE_CONTENT.length(), secondIndex).trim();
		} else if (ref.matches(JSONFilterDocs.CAREER_FILTER.toString())) { //it's a career job
			content = content.substring(content.indexOf(JSONFilterDocs.CAREER_BEFORE_CONTENT.toString()) + JSONFilterDocs.CAREER_BEFORE_CONTENT.length(), content.indexOf(JSONFilterDocs.CAREER_AFTER_CONTENT.toString())).trim();
		} else if (ref.matches(JSONFilterDocs.DEPARTMENT_FILTER.toString())) { //it's a department
			if(content.contains(JSONFilterDocs.NO_OPEN_POSITIONS.toString())) {
				content = JSONFilterDocs.NONE.toString();
			} else {
				content = content.substring(content.indexOf(JSONFilterDocs.DEPARTMENT_BEFORE_CONTENT.toString()) + JSONFilterDocs.DEPARTMENT_BEFORE_CONTENT.length(), content.indexOf(JSONFilterDocs.DEPARTMENT_AFTER_CONTENT.toString())).trim();
			}
		} else { //it's the contract job posting page
			//content = content.substring(content.indexOf(AIR_BNB_JSON_CPO_BEFORE_CON) + AIR_BNB_JSON_CPO_BEFORE_CON_LEN, content.indexOf(AIR_BNB_JSON_CPO_AFTER_CON)).trim();
		}

		return content;
	}

	private static String extractLocationFromDoc(String ref, String content) {
		String location = HelperStrings.EMPTY.toString();
		if (ref.matches(JSONFilterDocs.CONTRACTOR_FILTER.toString())) {
			location = content.substring(content.indexOf(JSONFilterDocs.CONTRACTOR_BEFORE_LOCATION.toString()) + JSONFilterDocs.CONTRACTOR_BEFORE_LOCATION.length(),content.indexOf(JSONFilterDocs.CONTRACTOR_AFTER_LOCATION.toString())).trim();
		} else if (ref.matches(JSONFilterDocs.CAREER_FILTER.toString())) {
			location = content.substring(content.indexOf(JSONFilterDocs.CAREER_BEFORE_LOCATION.toString()) + JSONFilterDocs.CAREER_BEFORE_LOCATION.length(),content.indexOf(JSONFilterDocs.CAREER_AFTER_LOCATION.toString())).trim();
		} else {
			location = "NONE";
		}
		if (location.equals("CITY, COUNTRY")) {
			location = "Remote";
		}
		return location;
	}

	private static List<Document> postDocsToFirebase(List<Document> documents) {
		for(int i = 0; i < documents.size(); i++) {
			Document doc = documents.get(i);
			String ref = doc.getReference();
			String docID = ref.substring(ref.lastIndexOf(HelperStrings.SEPARATOR.toString()));
			if (docID.equals(HelperStrings.SEPARATOR.toString())) {
				documents.remove(i);
				i--;
			}
			docID = docID.substring(1);

			if(documents.contains(doc)) {
				Firestore db = FirestoreClient.getFirestore();
				DocumentReference docRef = db.collection("jobs").document(docID);
				ApiFuture<WriteResult> res = docRef.set(doc);
				try {
					System.out.println("added doc at: " + res.get().getUpdateTime());
				} catch(Exception e) {

				}
			}
		}

		return documents;
	}

	private static List<Job> filterJobs(List<Document> docs) {
		List<Job> filteredJobs = new ArrayList<>();
		for(Document doc : docs) {
			if(isJob(doc)) {
				filteredJobs.add((Job)doc);
			}
		}
		return filteredJobs;
	}

	private static boolean isJob(Document doc) {
		return doc instanceof Job;
	}
	
	private static List<Department> filterDepartments(List<Document> docs) {
		List<Department> filteredDepartments = new ArrayList<>(); 
		for(Document doc : docs) {
			if(isDepartment(doc)) {
				filteredDepartments.add((Department)doc);
			}
		}
		return filteredDepartments;
	}
	
	private static boolean isDepartment(Document doc) {
		return doc instanceof Department;
	}

	private static void createTTLIndexFile(List<Job> filteredJobs, List<Department> filteredDepartments) {
		File ttlIndexFolder = new File(JSONFilterJena.TTL_DIR.toString());
		File ttlIndexFile = new File(JSONFilterJena.TTL_FILE.toString());
		if(!ttlIndexFile.exists()) {
			try {
				ttlIndexFolder.mkdirs();
				ttlIndexFile.createNewFile();
			} catch (IOException e) {
				logger.error(e.getMessage());
			}
		}
		try (Sink fileSink = Okio.sink(ttlIndexFile);
			 BufferedSink bufferedSink = Okio.buffer(fileSink)) {
			bufferedSink.writeUtf8(JSONFilterJena.PREFIX_TEXT_SEARCH.toString());
			bufferedSink.writeUtf8(HelperStrings.NEW_LINE.toString());
			bufferedSink.writeUtf8(HelperStrings.NEW_LINE.toString());
			for(Job job : filteredJobs) {
				writeRefEntry(bufferedSink, job);
				writeTitleEntry(bufferedSink, job);
				writeLocationEntry(bufferedSink, job);
				writeContentEntry(bufferedSink, job);
			}
		} catch(IOException e) {
			logger.error(e.getMessage());
		}
	}
	
	private static void writeJobID(BufferedSink bufferedSink, Job job) throws IOException {
		bufferedSink.writeUtf8(JSONFilterJena.TEXT_AIR_BNB_INDEX_TAG.toString() + job.getJobID());
		bufferedSink.writeUtf8(HelperStrings.SPACE.toString());
	}
	
	private static void writeRefEntry(BufferedSink bufferedSink, Job job) throws IOException {
		writeJobID(bufferedSink, job);
		bufferedSink.writeUtf8(JSONFilterJena.TEXT_AIR_BNB_INDEX_TAG.toString());
		bufferedSink.writeUtf8(JSONFilterJena.HAS_REF.toString());
		bufferedSink.writeUtf8(HelperStrings.SPACE.toString());
		bufferedSink.writeUtf8(HelperStrings.QUOTE.toString());
		bufferedSink.writeUtf8(job.getReference());
		bufferedSink.writeUtf8(HelperStrings.QUOTE.toString());
		bufferedSink.writeUtf8(HelperStrings.SPACE.toString());
		bufferedSink.writeUtf8(HelperStrings.SEMI_COLON.toString());
		bufferedSink.writeUtf8(HelperStrings.NEW_LINE.toString());
	}
	
	private static void writeTitleEntry(BufferedSink bufferedSink, Job job) throws IOException {
		bufferedSink.writeUtf8(HelperStrings.TAB.toString());
		bufferedSink.writeUtf8(HelperStrings.TAB.toString());
		bufferedSink.writeUtf8(HelperStrings.TAB.toString());
		bufferedSink.writeUtf8(JSONFilterJena.TEXT_AIR_BNB_INDEX_TAG.toString());
		bufferedSink.writeUtf8(JSONFilterJena.HAS_TITLE.toString());
		bufferedSink.writeUtf8(HelperStrings.SPACE.toString());
		bufferedSink.writeUtf8(HelperStrings.QUOTE.toString());
		bufferedSink.writeUtf8(job.getMetadata().getTitle());
		bufferedSink.writeUtf8(HelperStrings.QUOTE.toString());
		bufferedSink.writeUtf8(HelperStrings.SPACE.toString());
		bufferedSink.writeUtf8(HelperStrings.SEMI_COLON.toString());
		bufferedSink.writeUtf8(HelperStrings.NEW_LINE.toString());
	}
	
	private static void writeLocationEntry(BufferedSink bufferedSink, Job job) throws IOException {
		bufferedSink.writeUtf8(HelperStrings.TAB.toString());
		bufferedSink.writeUtf8(HelperStrings.TAB.toString());
		bufferedSink.writeUtf8(HelperStrings.TAB.toString());
		bufferedSink.writeUtf8(JSONFilterJena.TEXT_AIR_BNB_INDEX_TAG.toString());
		bufferedSink.writeUtf8(JSONFilterJena.LOCATED_IN.toString());
		bufferedSink.writeUtf8(HelperStrings.SPACE.toString());
		bufferedSink.writeUtf8(HelperStrings.QUOTE.toString());
		bufferedSink.writeUtf8(job.getMetadata().getLocation());
		bufferedSink.writeUtf8(HelperStrings.QUOTE.toString());
		bufferedSink.writeUtf8(HelperStrings.SPACE.toString());
		bufferedSink.writeUtf8(HelperStrings.SEMI_COLON.toString());
		bufferedSink.writeUtf8(HelperStrings.NEW_LINE.toString());
	}
	
	private static void writeContentEntry(BufferedSink bufferedSink, Job job) throws IOException {
		String contentForJena = job.getContent().replaceAll("[\n|\\\\.|\"]", "");
		bufferedSink.writeUtf8(HelperStrings.TAB.toString());
		bufferedSink.writeUtf8(HelperStrings.TAB.toString());
		bufferedSink.writeUtf8(HelperStrings.TAB.toString());
		bufferedSink.writeUtf8(JSONFilterJena.TEXT_AIR_BNB_INDEX_TAG.toString());
		bufferedSink.writeUtf8(JSONFilterJena.HAS_CONTENT.toString());
		bufferedSink.writeUtf8(HelperStrings.SPACE.toString());
		bufferedSink.writeUtf8(HelperStrings.QUOTE.toString());
		bufferedSink.writeUtf8(contentForJena); 
		bufferedSink.writeUtf8(HelperStrings.QUOTE.toString());
		bufferedSink.writeUtf8(HelperStrings.SPACE.toString());
		bufferedSink.writeUtf8(HelperStrings.DOT.toString());
		bufferedSink.writeUtf8(HelperStrings.NEW_LINE.toString());
	}
	
	private static Dataset createJenaTDBIndexedByLucene() {
		Dataset jenaDataSet = connectToJenaDS();
		
		EntityDefinition refEntityDefinition = new EntityDefinition("uri", "text", ResourceFactory.createProperty(JSONFilterJena.THE_JOB_SEEKR_JENA_URI.toString(), JSONFilterJena.HAS_REF.toString()));
		EntityDefinition titleEntityDefinition = new EntityDefinition("uri", "text", ResourceFactory.createProperty(JSONFilterJena.THE_JOB_SEEKR_JENA_URI.toString(), JSONFilterJena.HAS_TITLE.toString()));
		EntityDefinition locationEntityDefinition = new EntityDefinition("uri", "text", ResourceFactory.createProperty(JSONFilterJena.THE_JOB_SEEKR_JENA_URI.toString(), JSONFilterJena.LOCATED_IN.toString()));
		EntityDefinition contentEntityDefinition = new EntityDefinition("uri", "text", ResourceFactory.createProperty(JSONFilterJena.THE_JOB_SEEKR_JENA_URI.toString(), JSONFilterJena.HAS_CONTENT.toString()));
		Directory refLuceneDir = null;
		Directory titleLuceneDir = null;
		Directory locLuceneDir = null;
		Directory conLuceneDir = null;
		try {
			refLuceneDir = new SimpleFSDirectory((new File(JSONFilterJena.LUCENE_REF_DIR.toString()).toPath()));
			titleLuceneDir = new SimpleFSDirectory((new File(JSONFilterJena.LUCENE_TITLE_DIR.toString()).toPath()));
			locLuceneDir = new SimpleFSDirectory((new File(JSONFilterJena.LUCENE_LOCATION_DIR.toString()).toPath()));
			conLuceneDir = new SimpleFSDirectory((new File(JSONFilterJena.LUCENE_CONTENT_DIR.toString()).toPath()));
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
		TextIndexConfig refIndexConfig = new TextIndexConfig(refEntityDefinition);
		TextIndexConfig titleIndexConfig = new TextIndexConfig(titleEntityDefinition);
		TextIndexConfig locationIndexConfig = new TextIndexConfig(locationEntityDefinition);
		TextIndexConfig contentIndexConfig = new TextIndexConfig(contentEntityDefinition);
		
		
		Dataset refDataSet = TextDatasetFactory.createLucene(jenaDataSet, refLuceneDir, refIndexConfig);
		Dataset titleDataSet = TextDatasetFactory.createLucene(refDataSet, titleLuceneDir, titleIndexConfig);
		Dataset locDataSet = TextDatasetFactory.createLucene(titleDataSet, locLuceneDir, locationIndexConfig);
		Dataset contentDataSet = TextDatasetFactory.createLucene(locDataSet, conLuceneDir, contentIndexConfig);
		return contentDataSet;
	}
	
	private static Dataset loadDataset(Dataset dataset) {
		
		dataset = createJenaTDBIndexedByLucene();
		logger.info("loading data");
		long startTime = System.currentTimeMillis();
		dataset.begin(ReadWrite.WRITE);
		try {
			Model m = dataset.getDefaultModel();
			RDFDataMgr.read(m, JSONFilterJena.TTL_FILE.toString());
			dataset.commit();
		} finally {
			dataset.end();
		}
		
		long finishTime = System.currentTimeMillis();
		long loadingTime = finishTime - startTime;
		logger.info("Loading the model finished after " + loadingTime + "ms");
		return dataset;
	}

	public static List<String> doTextSearchQuery(String exp) {
		TextQuery.init();  //initialized Jena-text
		jenaDataset = loadDataset(jenaDataset);
		String prefix = StrUtils.strjoinNL(
				JSONFilterJena.PREFIX_TEXT.toString(),
				JSONFilterJena.PREFIX_AIR_BNB_URI.toString());
		
		
		jenaDataset.begin(ReadWrite.READ);
		String[] expArray = exp.split("%7C");
		String contentSearch = "";
		contentSearch += "*" + expArray[0] + "*~";
		for(int i = 1; i < expArray.length - 1; i++) {
			contentSearch += " || *" + expArray[i] + "*~";
		}
		logger.info(expArray.length+ "");
		if(expArray.length > 1) {
			contentSearch += " || *" + expArray[expArray.length - 1] + "*~"; 
		}
		String queryString = StrUtils.strjoinNL(
				JSONFilterJena.SELECT.toString(),
				HelperStrings.OPENING_CURLY.toString(),
				"optional{?s text:query (\'" + contentSearch + "\' 20)}",
				"}");
		System.out.println(queryString);
		QueryExecution queryExec = QueryExecutionFactory.create(prefix + HelperStrings.NEW_LINE.toString() + queryString, jenaDataset);
		
		List<String> queryResults = new ArrayList<>();
		
		for ( ResultSet results = queryExec.execSelect(); results.hasNext(); ) {
			QuerySolution querySolution = results.next();
			System.out.println(querySolution.toString());
			
			String reference = querySolution.get("?s").toString();
			String jobID = reference.substring(reference.indexOf("#") + 1);
			queryResults.add(jobID);
		}
		jenaDataset.end();
		jenaDataset.close();
		logger.info(queryResults.toString());
		return queryResults;
	}

	private static Dataset connectToJenaDS() {
		Dataset thejobseekrDataSet = TDB2Factory.connectDataset(JSONFilterJena.DATASET_DIR.toString());
		return thejobseekrDataSet;
	}

	private enum JSONFilterJena {
		JENA_DIR("jena"),
		DATASET_DIR("jena/dataset"),
		TTL_DIR("jena/ttl"),
		TTL_FILE("jena/ttl/airbnb.ttl"),
		LUCENE_REF_DIR("jena/lucene/ref"),
		LUCENE_TITLE_DIR("jena/lucene/title"),
		LUCENE_LOCATION_DIR("jena/lucene/loc"),
		LUCENE_CONTENT_DIR("jena/lucene/content"),
		FOLDER_NOT_EXISTS("Folder didn't exist!"),
		TEXT_AIR_BNB_INDEX_TAG("airbnb:"),
		THE_JOB_SEEKR_JENA_URI("http://thejobseekr.info/jenatext#"),
		PREFIX_TEXT_SEARCH("@prefix " + TEXT_AIR_BNB_INDEX_TAG + " <" + THE_JOB_SEEKR_JENA_URI + ">"),
		PREFIX_TEXT("PREFIX text: <http://jena.apache.org/text#>"),
		PREFIX_AIR_BNB_URI("PREFIX " + TEXT_AIR_BNB_INDEX_TAG + " <" + THE_JOB_SEEKR_JENA_URI+ ">"),
		HAS_REF("hasRef"),
		HAS_TITLE("hasTitle"),
		HAS_CONTENT("hasContent"),
		LOCATED_IN("locatedIn"),
		IN_DEPARTMENT("inDepartment"),
		DEFAULT_ADDRESS("http://thejobseekr/job/"),
		SPLIT_REGEX("[\\.\\n\"]"),
		EX_SEARCH_QEURY("Software"),
		SELECT("SELECT * "),
		LIMIT_20("Limit 20"),
		REF_VAR("?ref");


		private final String text;

		JSONFilterJena(final String text) {
			this.text = text;
		}

		@Override
		public String toString() {
			return text;
		}
	}

	private enum JSONFilterCrawler {
		AIR_BNB_OUTPUT_DIR("./output/airbnbCrawledJSONFiles");

		private final String text;

		JSONFilterCrawler(final String text) {
			this.text = text;
		}

		@Override
		public String toString() {
			return text;
		}
	}

	private enum JSONFilterDocs {
		FINISHED_FILTERING("Successfully finished filtering, reading and writing the files."),
		TITLE_DELIMETER("{title="),
		JSON_DOC_ADD("doc-add"),
		JSON_REFERENCE("reference"),
		JSON_CONTENT("content"),
		JSON_METADATA("metadata"),
		JSON_TITLE("title"),
		CONTRACTOR_CONTAINS("at Contractor"),
		CONTRACTOR_REPLACE_BEG("Job Application for "),
		CONTRACTOR_REPLACE_END("at Contractor Jobs at Airbnb"),
		CAREER_CONTAINS("| Careers"),
		CAREER_REPLACE("| Careers at Airbnb"),
		CONTRACTOR_FILTER("https://boards.greenhouse.io/contractorjobs/[a-z,/,0-9]+"),
		CAREER_FILTER("https://www.airbnb.ca/careers/departments/position/\\d+"),
		MAIN_FILTER(CONTRACTOR_FILTER + "|" + CAREER_FILTER),
		DEPARTMENT_FILTER("https://www.airbnb.ca/careers/departments/[a-z,-]+"),
		CONTRACTOR_BEFORE_LOCATION("\n  \n\n    (View all jobs)\n\n    \n   "),
		CONTRACTOR_AFTER_LOCATION("\n    \n\n\n\n  \n"),
		CONTRACTOR_BEFORE_CONTENT("\n    \n\n\n\n  \n"),
		CONTRACTOR_AFTER_CONTENT("Apply for this Job"),
		CAREER_BEFORE_LOCATION("\n        \n\n        \n          "),
		CAREER_AFTER_LOCATION("\n        \n\n      \n\n    \n\n  \n\n\n  \n    \n      \n        \n          "),
		CAREER_BEFORE_CONTENT("\n        \n\n      \n\n    \n\n  \n\n\n  \n    \n      \n        \n          "),
		CAREER_AFTER_CONTENT("Apply Now\n          \n        \n\n      \n\n      \n        \n          \t\n                "),
		DEPARTMENT_BEFORE_CONTENT("\n\n  \n\n\n\n\n\n  \n\n"),
		DEPARTMENT_AFTER_CONTENT("Open Positions"),
		CPO_BEFORE_LOCATION("Remote\n\n\n\n  \n\n\n\n\n\n\n\n  \n\n\n  Contractors \n\n\n  \n  "),
		CPO_AFTER_LOCATION(" \n\n\n  \n\n\n\n\n    \n\n  \n\n\n      \n    Powered by"),
		NO_OPEN_POSITIONS("No open positions at this time"),
		NONE("NONE"),
		FOUND_A_WORD("found a word");

		private final String text;

		JSONFilterDocs(final String text) {
			this.text = text;
		}

		public int length() {
			return text.length();
		}

		@Override
		public String toString() {
			return text;
		}
	}

	private enum HelperStrings {
		NULL(null),
		EQUALS("="),
		SPACE(" "),
		DBL_SPACE("  "),
		PERCENT_40("%40"),
		DOT("."),
		SEMI_COLON(";"),
		AT("@"),
		QUOTE("\""),
		EMPTY(""),
		ASTERISK("*"),
		OPENING_TAG("<"),
		CLOSING_TAG(">"),
		NEW_LINE("\n"),
		OPENING_CURLY("{"),
		CLOSING_CURLY("}"),
		TAB("\t"),
		SEPARATOR("/");

		private final String text;

		HelperStrings(final String text) {
			this.text = text;
		}

		@Override
		public String toString() {
			return text;
		}
	}

}
