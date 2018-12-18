
package com.thejobseekr.crawler;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;

import com.thejobseekr.crawler.config.CrawlerConfig;

@SpringBootApplication
public class CrawlerApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(CrawlerApplication.class, args);
		CrawlerConfig.run();
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(CrawlerApplication.class);
	}

	@Bean
	public CorsFilter corsFilter() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowCredentials(true);
		config.addAllowedOrigin(HelperStrings.ASTERISK.toString());
		config.addAllowedHeader(HelperStrings.ASTERISK.toString());
		config.addAllowedMethod(CORSStrings.POST.toString());
		config.addAllowedHeader(CORSStrings.FIREBASE_AUTH.toString());
		source.registerCorsConfiguration(CORSStrings.ALL.toString(), config);
		return new CorsFilter(source);
	}

	@Bean
	public FirebaseAuth firebaseAuth() throws IOException {

		InputStream serviceAccount = this.getClass().getResourceAsStream(FirebaseStrings.ADMIN_SDK_PATH.toString());

		FirestoreOptions fsOptions = FirestoreOptions
		                             .newBuilder()
		                             .setTimestampsInSnapshotsEnabled(true)
		                             .build();

		FirebaseOptions options = new FirebaseOptions
		                          .Builder()
		                          .setFirestoreOptions(fsOptions)
		                          .setCredentials(GoogleCredentials.fromStream(serviceAccount))
		                          .build();

		if(FirebaseApp.getApps().isEmpty()) {
			FirebaseApp
			.initializeApp(options);
		}

		return FirebaseAuth
		       .getInstance();
	}

	private enum CORSStrings {
		ALL("/**"),
		POST("POST"),
		FIREBASE_AUTH("x-firebase-auth");

		private final String text;

		CORSStrings(final String text) {
			this.text = text;
		}

		@Override
		public String toString() {
			return text;
		}
	}

	private enum FirebaseStrings {
		ADMIN_SDK_PATH("/spring-firebase-test-firebase-adminsdk-ql2kg-95106115e4.json");

		private final String text;

		FirebaseStrings(final String text) {
			this.text = text;
		}

		@Override
		public String toString() {
			return text;
		}
	}

	private enum HelperStrings {
		ASTERISK("*");

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
