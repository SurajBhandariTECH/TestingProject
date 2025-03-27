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
import com.mypurecloud.sdk.v2.api.ObjectsApi;
import com.mypurecloud.sdk.v2.api.RoutingApi;
import com.mypurecloud.sdk.v2.model.AuthzDivision;
import com.mypurecloud.sdk.v2.model.AuthzDivisionEntityListing;
import com.mypurecloud.sdk.v2.model.WrapupCode;
import com.mypurecloud.sdk.v2.model.WrapupCodeRequest;
import com.mypurecloud.sdk.v2.model.WritableStarrableDivision;

@Service
public class WrapUpCodeServices {

	@Autowired
	private OrgConfigService orgConfigService;

	public List<String> createWrapUpCodeFromCSV(MultipartFile file, String organizationName, String environment,
			String token) throws IOException {

		List<String> results = new ArrayList<>();

		// retrieve clientID and redirectUri for the specified organization
		Map<String, String> credentials = orgConfigService.getCredentials(organizationName);
		String clientId = credentials.get("clientId");
		String redirectUri = credentials.get("redirectUri");

		if (clientId == null || redirectUri == null) {
			results.add("Error: Client credentials not found for the organization " + organizationName);
			return results;
		}

		try {
			// initialize the Genesys API client
			ApiClient apiClient = ApiClient.Builder.standard().withAccessToken(token)
					.withBasePath("https://api." + environment).build();
			Configuration.setDefaultApiClient(apiClient);

			RoutingApi routingApi = new RoutingApi(apiClient);

			ObjectsApi apiInstance = new ObjectsApi(apiClient);

			// Parse the csv to extract wrap up code and division Names

			List<String[]> csvData = parseCSV(file, results);

			for (String[] row : csvData) {

				String wrapUpCodeName = row[0];
				String divisionName = row[1];

				System.out.println(divisionName);

				try {
					// fetch division Id by division name
					AuthzDivision divisionId = fetchDivisionByName(divisionName, apiInstance);

					System.out.println(divisionId);
					if (divisionId == null) {
						results.add("Division not found: " + divisionName);
						continue;
					}

					// create the wrapup code
					WrapupCodeRequest wrapUpCode = new WrapupCodeRequest();

					wrapUpCode.setName(wrapUpCodeName);

					WritableStarrableDivision division = new WritableStarrableDivision();
					division.setName(divisionName);
					division.setId(divisionId.getId());
					wrapUpCode.setDivision(division);

					routingApi.postRoutingWrapupcodes(wrapUpCode);

					results.add("Successfully Created wrap-up Code: " + wrapUpCodeName + " in division: '"
							+ divisionName + "'");
				} catch (ApiException e) {
					results.add("Failed to create wrap-up code: " + wrapUpCodeName + " (already exist or other "
							+ e.getMessage() + " )");

				}
			}
		} catch (Exception e) {
			results.add("Error: initializing genesys api client: " + e.getMessage());
		}
		return results;
	}

	public List<String> deleteWrapUpCodeFromCSV(MultipartFile file, String organizationName, String environment,
			String token) throws IOException {
		List<String> results = new ArrayList<>();

		// retrieving the credentials for the organization

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

			ObjectsApi apiInstance = new ObjectsApi(apiClient);

			List<String[]> csvData = deleteParseCSV(file, results);

			for (String[] row : csvData) {
				String wrapUpCodeName = row[0];
				String divisionName = row[1];

				try {
					AuthzDivision division = fetchDivisionByName(divisionName, apiInstance);
					if (division == null) {
						results.add("Division not found: " + divisionName);
						continue;
					}

					List<String> divisionId = new ArrayList<>();
					divisionId.add(division.getId());

					List<WrapupCode> wrapUpCodes = routingApi
							.getRoutingWrapupcodes(100, 1, null, null, wrapUpCodeName, null, divisionId).getEntities();

					System.out.println(wrapUpCodes);
					if (wrapUpCodes.isEmpty()) {
						results.add("Wrap up code not found:" + wrapUpCodeName + "in Division:" + divisionName);
						continue;
					}

					System.out.println("Division" + division.getId());
					System.out.println(wrapUpCodes.get(0).getDivision().getId());

					// deleteing the wrapup code
					if (wrapUpCodes.get(0).getDivision().getId() != division.getId()) {
						results.add("Error: division not matched");
						continue;

					}

					for (WrapupCode wrapUpCode : wrapUpCodes) {

						routingApi.deleteRoutingWrapupcode(wrapUpCode.getId());
						results.add("Successfully deleted wrap-up code: " + wrapUpCodeName + " from Division "
								+ divisionName);
					}

				} catch (ApiException e) {
					results.add(
							"Failed to delete the wrap Up code " + wrapUpCodeName + " (Error: " + e.getMessage() + ")");
				}
			}

		} catch (Exception e) {
			results.add("Error initializing Genesys API client: " + e.getMessage());
		}

		return results;

	}

	private AuthzDivision fetchDivisionByName(String divisionName, ObjectsApi apiInstance)
			throws IOException, ApiException {

		AuthzDivisionEntityListing divisions = apiInstance.getAuthorizationDivisions(100, 1, null, null, null, null,
				null, null, divisionName);
		// System.out.println(divisions);
		if (divisions.getEntities() != null && !divisions.getEntities().isEmpty()) {
			return divisions.getEntities().get(0);
		}
		return null;
	}

	private List<String[]> parseCSV(MultipartFile file, List<String> results) throws IOException {

		List<String[]> csvData = new ArrayList<>();
		String wrapUpCodeNamePattern = "^[a-zA-Z0-9_ ]+$";

		try (InputStream inputStream = file.getInputStream(); Scanner scanner = new Scanner(inputStream, "UTF-8")) {

			if (scanner.hasNextLine()) {
				String headerLine = scanner.nextLine();
				headerLine = headerLine.replace("\uFEFF", "");
				String[] headers = headerLine.split(",");

				// validate the header
				if (headers.length < 2 || !headers[0].equalsIgnoreCase("WrapUpCode Name")
						|| !headers[1].equalsIgnoreCase("Division Name")) {
					throw new IllegalArgumentException(
							"Invalid File Format: The first cell of first row must be 'WrapUpCode Name'and first cell of second column must be 'Division Name'");

				}

			}

			// read the validate each subsequent row
			int rowNumber = 1;
			while (scanner.hasNextLine()) {
				rowNumber++;
				String line = scanner.nextLine();
				String[] columns = line.split(",");

				if (columns.length >= 2 && !columns[0].trim().isEmpty() && !columns[1].trim().isEmpty()) {
					String wrapUpCodeName = columns[0].trim();
					String divisionName = columns[1].trim();

					if (wrapUpCodeName.isEmpty() || divisionName.isEmpty()) {
						continue;
					}

					if (!wrapUpCodeName.matches(wrapUpCodeNamePattern)
							&& !divisionName.matches(wrapUpCodeNamePattern)) {
						results.add("Error: Invalid wrapup code or division name '" + wrapUpCodeName + "' or ' "
								+ divisionName + "'at row " + rowNumber
								+ ". Only letters, digits, underscore, and space are allowed.");
						continue;

					}
					csvData.add(new String[] { wrapUpCodeName, divisionName });// add valid skill name

				}
			}

			return csvData;
		}

	}

	private List<String[]> deleteParseCSV(MultipartFile file, List<String> results) throws IOException {
		List<String[]> csvData = new ArrayList<>();
		String wrapUpCodeNamePattern = "^[a-zA-Z0-9_ ]+$";

		try (InputStream inputStream = file.getInputStream(); Scanner scanner = new Scanner(inputStream, "UTF-8")) {

			if (scanner.hasNextLine()) {
				String headerLine = scanner.nextLine();
				headerLine = headerLine.replace("\uFEFF", "");
				String[] headers = headerLine.split(",");

				// validate the header
				if (headers.length < 2 || !headers[0].equalsIgnoreCase("DeleteWrapUpCode Name")
						|| !headers[1].equalsIgnoreCase("Division Name")) {
					throw new IllegalArgumentException(
							"Invalid File Format: The first cell of first row must be 'DeleteWrapUpCode Name'and first cell of second column must be 'Division Name'");

				}

			}

			int rowNumber = 1;
			while (scanner.hasNextLine()) {
				rowNumber++;
				String line = scanner.nextLine();
				String[] columns = line.split(",");

				if (columns.length >= 2 && !columns[0].trim().isEmpty() && !columns[1].trim().isEmpty()) {
					String wrapUpCodeName = columns[0].trim();
					String divisionName = columns[1].trim();

					if (wrapUpCodeName.isEmpty() || divisionName.isEmpty()) {
						continue;
					}

					if (!wrapUpCodeName.matches(wrapUpCodeNamePattern)
							&& !divisionName.matches(wrapUpCodeNamePattern)) {
						results.add("Error: Invalid wrapup code or division name '" + wrapUpCodeName + "' or ' "
								+ divisionName + "'at row " + rowNumber
								+ ". Only letters, digits, underscore, and space are allowed.");
						continue;

					}
					csvData.add(new String[] { wrapUpCodeName, divisionName });// add valid skill name

				}
			}

			return csvData;

		}
	}

}
