package com.example.Genesys_Functionalities.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.mypurecloud.sdk.v2.ApiClient;
import com.mypurecloud.sdk.v2.ApiException;
import com.mypurecloud.sdk.v2.Configuration;
import com.mypurecloud.sdk.v2.api.UsersApi;
import com.mypurecloud.sdk.v2.model.EmployerInfo;
import com.mypurecloud.sdk.v2.model.UpdateUser;
import com.mypurecloud.sdk.v2.model.User;
import com.mypurecloud.sdk.v2.model.UserEntityListing;

@Service
public class UserService {

	@Autowired
	private OrgConfigService orgConfigService;
	private int userUpdateCount = 0;

	public List<String> updateUsersFromCsv(MultipartFile file, String organizationName, String environment,
			String token) throws Exception {

		List<String> results = new ArrayList<>();

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


			UsersApi usersApi = new UsersApi(apiClient);

			// Step 1: Fetch all users from Genesys Cloud
			Map<String, User> usersMap = fetchAllUsers(usersApi);

			// Step 2: Parse the CSV file
			List<String[]> csvData = parseCsv(file, results);

			int rowIndex = 1;

			int count = 0;
			// for email duplication
			Set<String> processedEmails = new HashSet<>();
			// Step 3: Process updates sequentially
			for (String[] row : csvData) {

				String email = row[0].trim().toLowerCase();
				String officialName = row[1];
				String employeeId = row[2];

				if (processedEmails.contains(email)) {
					results.add("Duplicate email skipped: " + email);
					rowIndex++;
					continue; // Skip duplicate emails
				}
				processedEmails.add(email);
				// Check if the user exists in the map

				User matchUser = usersMap.get(email);

				if (matchUser != null) {
					count = updateUser(matchUser, officialName, email, employeeId, results, rowIndex);

				} else {
					results.add("- Failed to update User: Email: " + email + " (User not found)");
					rowIndex++;
					continue;
				}
				rowIndex++;

			}
			results.add("totalNumber of User Updated " + count);
		} catch (Exception e) {
			results.add("Error: initializing genesys api client: " + e.getMessage());
		}

		return results;
	}

	private int updateUser(User matchUser, String officialName, String email, String employeeId, List<String> results,
			int rowIndex) {
		UsersApi usersApi = new UsersApi();

		try {
			// Retrieve the user version
			Integer version = matchUser.getVersion();

			// Prepare the user update object
			UpdateUser updateUser = new UpdateUser();
			updateUser.version(version);
			if (matchUser.getEmployerInfo() == null) {
				EmployerInfo employerInfo = new EmployerInfo();
				employerInfo.setOfficialName("");
				employerInfo.setEmployeeId("");
				matchUser.setEmployerInfo(employerInfo);
			}
			updateUser.setEmployerInfo(matchUser.getEmployerInfo());
			updateUser.getEmployerInfo().setOfficialName(officialName);
			updateUser.getEmployerInfo().setEmployeeId(employeeId);

			usersApi.patchUser(matchUser.getId(), updateUser);

			results.add("- Successfully Update the User: " + matchUser.getName() + " Email: " + email);
			userUpdateCount++;

		} catch (ApiException | IOException e) {
			results.add("- Failed to update User: " + matchUser.getName() + " Email: " + email + " (Error: "
					+ e.getMessage());
		}
		return userUpdateCount;
	}

	private Map<String, User> fetchAllUsers(UsersApi usersApi) throws ApiException, IOException {
		Map<String, User> usersMap = new HashMap<>();
		int pageSize = 100; // Max allowed page size
		int pageNumber = 1;
		List<String> employerInfo = Arrays.asList("employerInfo");

		while (true) {
			UserEntityListing userListing = usersApi.getUsers(pageSize, pageNumber, null, null, null, employerInfo,
					null, null);
			if (userListing.getEntities() != null) {
				for (User user : userListing.getEntities()) {
					if (user.getEmail() != null) {
						usersMap.put(user.getEmail().trim().toLowerCase(), user); // Use email as key
					}
				}
			}
			if (userListing.getEntities() == null || userListing.getEntities().isEmpty()) {
				break; // No more users to fetch
			}
			pageNumber++;

		}
		return usersMap;
	}

	private List<String[]> parseCsv(MultipartFile file, List<String> result) throws Exception {
		List<String[]> csvData = new ArrayList<>();
		try (InputStream inputStream = file.getInputStream(); Scanner scanner = new Scanner(inputStream, "UTF-8")) {

			if (scanner.hasNextLine()) {
				String headerLine = scanner.nextLine();
				headerLine = headerLine.replace("\uFEFF", "");

				if (headerLine == null || !validateHeaders(headerLine)) {
					System.out.println(headerLine);
					throw new IllegalArgumentException(
							"Invalid File Format: Headers must be 'emailId', 'officialName', 'employeeId'.");
				}
			}

			String line;
			int rowCount = 1;
			while (scanner.hasNextLine()) {
				rowCount++;
				line = scanner.nextLine();
				String[] columns = line.split(",");
				if (columns.length >= 3 && !columns[0].trim().isEmpty() && !columns[1].trim().isEmpty()
						&& !columns[2].trim().isEmpty()) {
					String email = columns[0].trim();
					String officialName = columns[1].trim();
					String employeeId = columns[2].trim();

					if (email.isEmpty() || officialName.isEmpty() || employeeId.isEmpty()) {

						result.add(": Skipped due to missing values or invalid format.\n");
						continue;
					}
					csvData.add(new String[] { columns[0].trim(), columns[1].trim(), columns[2].trim() });
				}
			}
		}
		return csvData;
	}

	private boolean validateHeaders(String headerLine) {

		String[] headers = headerLine.split(",");

		System.out.println(headers[0].trim().compareToIgnoreCase("EmailId"));
		System.out.println(headers[1].equalsIgnoreCase("officialName"));
		System.out.println(headers[2].equalsIgnoreCase("employeeId"));
		return headers.length >= 3 && headers[0].equalsIgnoreCase("EmailId")
				&& headers[1].equalsIgnoreCase("officialName") && headers[2].equalsIgnoreCase("employeeId");
	}

}