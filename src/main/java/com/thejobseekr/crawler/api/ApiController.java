package com.thejobseekr.crawler.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.concurrent.ExecutionException;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import com.thejobseekr.crawler.task.JSONFilter;
import com.thejobseekr.crawler.task.model.Job;

import java.util.ArrayList;

@Controller
@RequestMapping("/api")
public class ApiController {

	@PostMapping("/query")
	public ResponseEntity<?> queryJena(@RequestBody String query) {
		System.out.println(query);
		query = replaceEqualsAtStringEnd(query);
		List<String> results = JSONFilter.doTextSearchQuery(query);

		List<Job> responseData = pullDataFromGoogleFireStore(results);

		System.out.println(responseData);
		return new ResponseEntity<>(responseData, HttpStatus.OK);
	}

	public String replaceEqualsAtStringEnd(String input) {
		if(input.endsWith("=")) {
			input = input.substring(0, input.length() - 1);
		}
		return input;
	}

	public synchronized List<Job> pullDataFromGoogleFireStore(List<String> IDs) {
		List<Job> docsFromFirestore= new ArrayList<>();
		Firestore db = FirestoreClient.getFirestore();

		for(String id : IDs) {
			DocumentReference docRef = db.collection("jobs").document(id);
			ApiFuture<DocumentSnapshot> future = docRef.get();
			try {
				DocumentSnapshot document = future.get();
				if(document.exists()) {
					docsFromFirestore.add(Job.fromMap(document.getData()));
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}
		System.out.println(docsFromFirestore);
		return docsFromFirestore;
	}

}
