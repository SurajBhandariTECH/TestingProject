package com.example.Genesys_Functionalities.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.mypurecloud.sdk.v2.ApiClient;
import com.mypurecloud.sdk.v2.ApiException;
import com.mypurecloud.sdk.v2.Configuration;
import com.mypurecloud.sdk.v2.api.RoutingApi;
import com.mypurecloud.sdk.v2.model.RoutingSkill;

@Service // @Service Annotation to indicate that a class belongs to that layer.
public class GenesysServices {

	@Autowired
	private OrgConfigService orgConfigService;

	public List<String> createSkillFromCsv(MultipartFile csvFile, String organizationName, String environment,
			String token) throws IOException {// An InputStreamReader is a bridge from byte streams to character
												// streams: It reads bytes and decodes them into characters using a
												// specified charset. The charset that it uses may be specified by name
												// or may be given explicitly, or the platform's default charset may be
												// accepted.

		List<String> results = new ArrayList<>();

		// retrieve clientId and clientSecret for the specified organization
		Map<String, String> credentials = orgConfigService.getCredentials(organizationName);
		String clientId = credentials.get("clientId");
		String redirectUri = credentials.get("redirectUri");

		if (clientId == null || redirectUri == null) {
			results.add("Error: Client Credentials not found for organization" + organizationName);
			return results;
		}

		try {
			// initialize the Genesys API Client
			ApiClient apiClient = ApiClient.Builder.standard().withAccessToken(token)
					.withBasePath("https://api." + environment).build();
			Configuration.setDefaultApiClient(apiClient);

			System.out.println("connected");

			RoutingApi routingApi = new RoutingApi(apiClient);

			// parse the csv to extract skill names starting from second row

			List<String> skillNames = parseCSV(csvFile);

			// handle validation error from parsing
			for (String skillName : skillNames) {

				if (skillName.startsWith("Error: ")) {
					results.add(skillName);
					continue;
				}
				try {
					RoutingSkill skill = new RoutingSkill();
					skill.setName(skillName);
					routingApi.postRoutingSkills(skill);
					results.add("Successfully created Skill:" + skillName);
				} catch (ApiException e) {
					results.add("Falied to create skill: " + skillName + " (already exist or other " + e.getMessage()
							+ " )");
				}
			}

		} catch (Exception e) {
			results.add("Error initializing Genesys API client: " + e.getMessage());
		}

		return results;
	}

	public List<String> deleteSkillsFromCSV(MultipartFile file, String organizationName, String environment,
			String token) throws IOException {
		List<String> results = new ArrayList<>();

		// retrieve clientId and CientSecret for a specified org
		Map<String, String> credentials = orgConfigService.getCredentials(organizationName);
		String clientId = credentials.get("clientId");
		String redirectUri = credentials.get("redirectUri");

		if (clientId == null || redirectUri == null) {
			results.add("Error: Client Credentials not found for organization" + organizationName);
			return results;
		}

		try {

			ApiClient apiClient = ApiClient.Builder.standard().withAccessToken(token)
					.withBasePath("https://api." + environment).build();

			Configuration.setDefaultApiClient(apiClient);

			RoutingApi routingApi = new RoutingApi(apiClient);

			// Parse the CSV to extract skill names
			List<String> skillNames = parseCSVDelete(file);

			for (String skillName : skillNames) {
				if (skillName.startsWith("Error: ")) {
					results.add(skillName);
					continue;
				}

				Integer pageSize = 100; // Integer | Page size
				Integer pageNumber = 1;
				try {
					// fetch skill by name and delete it
					List<RoutingSkill> skills = routingApi.getRoutingSkills(pageSize, pageNumber, skillName, null)
							.getEntities();
					if (skills.isEmpty()) {
						results.add("Skill not found: " + skillName);
						continue;
					}

					for (RoutingSkill skill : skills) {
						routingApi.deleteRoutingSkill(skill.getId());
						results.add(" Successfully Delete Skill: " + skillName);
					}
				} catch (ApiException e) {
					results.add("Failed to delete skill: " + skillName + " (Error: " + e.getMessage() + ")");
				}
			}
		} catch (Exception e) {
			results.add("Error initializing Genesys API client: " + e.getMessage());
		}
		return results;

	}

	public List<String> createWrapUpCodeFromCsv(MultipartFile file, String organizationName, String environment) {
		List<String> results = new ArrayList<>();

		try {
			// Add specific validation and processing for Wrap-Up Code CSV
			// This logic may differ from the skills import and may require different API
			// calls

			// Simulate the Wrap-Up Code import processing
			// TODO: Implement actual logic for Wrap-Up Code import

			results.add("Wrap-Up Code Import Success.");
		} catch (Exception e) {
			results.add("Wrap-Up Code Import Failed: " + e.getMessage());
		}

		return results;
	}

	private List<String> parseCSV(MultipartFile file) throws IOException {

		List<String> skillNames = new ArrayList<>();
		// allowed : letters digits, underscore and space
		String skillNamePattern = "^[a-zA-Z0-9_ ]+$";

		try (InputStream inputStream = file.getInputStream(); Scanner scanner = new Scanner(inputStream, "UTF-8")) {

			// skip the header row
			if (scanner.hasNextLine()) {
				String headerLine = scanner.nextLine();
				headerLine = headerLine.replace("\uFEFF", "");
				String[] headers = headerLine.split(",");
				System.out.println(headers[0].trim());

				// validate the header
				if (headers.length == 0 || !headers[0].equalsIgnoreCase("Skill Name")) {
					throw new IllegalArgumentException(
							"Invalid File Format: The first cell of first row must be 'Skill Name'.");
				}

			}

			// read and validate each subsequent row
			int rowNumber = 1;
			while (scanner.hasNextLine()) {
				rowNumber++;
				String line = scanner.nextLine();
				String[] columns = line.split(",");

				if (columns.length > 0 && !columns[0].trim().isEmpty()) {
					String skillName = columns[0].trim();

					if (skillName.isEmpty()) {
						// skillNames.add("Error: Empty skill name at row "+rowNumber+".");
						continue;
					}

					if (!skillName.matches(skillNamePattern)) {
						skillNames.add("Error: Invalid skill name '" + skillName + "' at row " + rowNumber
								+ ". Only letters, digits, underscore, and space are allowed.");
						continue;
					}
					skillNames.add(skillName); // add valid skill name
				}

			}
		}

		return skillNames;
	}

	private List<String> parseCSVDelete(MultipartFile file) throws IOException {
		List<String> skillNames = new ArrayList<>();
		// allowed : letters digits, underscore and space
		// String skillNamePattern = "^[a-zA-Z0-9_ ]+$";

		try (InputStream inputStream = file.getInputStream(); Scanner scanner = new Scanner(inputStream, "UTF-8")) {

			// skip the header row
			if (scanner.hasNextLine()) {
				String headerLine = scanner.nextLine();
				headerLine = headerLine.replace("\uFEFF", "");
				String[] headers = headerLine.split(",");
				System.out.println(headers[0].trim());

				// validate the header
				if (headers.length == 0 || !headers[0].equalsIgnoreCase("DeleteSkill Name")) {
					throw new IllegalArgumentException(
							"Invalid File Format: The first cell of first row must be 'DeleteSkill Name'.");
				}

			}

			// read and validate each subsequent row
			int rowNumber = 1;
			while (scanner.hasNextLine()) {
				rowNumber++;
				String line = scanner.nextLine();
				String[] columns = line.split(",");

				if (columns.length > 0 && !columns[0].trim().isEmpty()) {
					String skillName = columns[0].trim();

					if (skillName.isEmpty()) {
						// skillNames.add("Error: Empty skill name at row "+rowNumber+".");
						continue;
					}

				
					skillNames.add(skillName); // add valid skill name
				}

			}
		}

		return skillNames;
	}
}