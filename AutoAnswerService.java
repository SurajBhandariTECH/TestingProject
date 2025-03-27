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
import com.mypurecloud.sdk.v2.model.PatchUser;
import com.mypurecloud.sdk.v2.model.User;
import com.mypurecloud.sdk.v2.model.UserEntityListing;

@Service
public class AutoAnswerService {
	
	@Autowired
	private OrgConfigService orgConfigService;
	
	private int userUpdateCount = 0;
	
	public List<String> enableAutoAnswer(MultipartFile file, String organizationName, String environment, String token)throws IOException {
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
			
			//initialize the Genesys API Client
			ApiClient apiClient = ApiClient.Builder.standard().withAccessToken(token).withBasePath("https://api."+environment).build();
			Configuration.setDefaultApiClient(apiClient);
			
			UsersApi users = new UsersApi(apiClient);
			
			Map<String, User> userList = fetchAllUser(users);
			
			// for email duplication
			int rowIndex = 1;
			int count = 0;
			Set<String> processedEmails = new HashSet<>();
			
			List<String[]> csvData = parseCSV(file,results);
			
			for(String[] row:csvData) {
				String agentEmailId = row[0].trim().toLowerCase();
				String enableAutoAnswer = row[1];
				
				if(processedEmails.contains(agentEmailId)) {
					results.add("duplicate email found :"+agentEmailId); 
					rowIndex++;
					continue; //skip duplicate email
				}
				
				processedEmails.add(agentEmailId);
				
				User matchUser = userList.get(agentEmailId);
				
				if(matchUser !=null) {
					count = updateAutoAnswer(matchUser, agentEmailId, enableAutoAnswer,results, rowIndex);
				}
				else {
					results.add("- Failed to update User: Email: " + agentEmailId + " (User not found)");
					rowIndex++;
					continue;
				}
				rowIndex++;
			}
			results.add("totalNumber of User AutoAnswer Updated " + count);
		}catch (Exception e) {
			results.add("Error: initializing genesys api client: " + e.getMessage());
		}
		
		return results;

	}
	
	public Map<String, User> fetchAllUser(UsersApi usersApi) throws IOException, ApiException{
		Map<String, User> usersList = new HashMap<>();
		
		int pageSize = 100;
		int pageNumber = 1;
		List<String> expand = Arrays.asList(null);
		
		while(true) {
			UserEntityListing userListing = usersApi.getUsers(pageSize, pageNumber, null, null, null, expand,
					null, null);
			
			if(userListing.getEntities()!=null) {
				for(User user :userListing.getEntities()) {
					if(user.getEmail()!=null) {
						usersList.put(user.getEmail().trim().toLowerCase(), user);
					}
				}
			}
			if(userListing.getEntities()==null || userListing.getEntities().isEmpty()) {
				break;
			}
			pageNumber++;
		}
		
		return usersList;
	}
	
	private int updateAutoAnswer(User matchUser, String emailId, String autoAnswer,List<String> results, int rowIndex) {
		
		UsersApi usersApi = new UsersApi();
		try {
			String id = matchUser.getId();
			String name = matchUser.getName();
			boolean acdAutoAnswer = Boolean.parseBoolean(autoAnswer.toLowerCase());
			
			PatchUser user = new PatchUser();
			user.setId(id);
			user.setPreferredName(name);
			user.setAcdAutoAnswer(acdAutoAnswer);
			
			List<PatchUser> body = Arrays.asList(user);
			
			UserEntityListing update = usersApi.patchUsersBulk(body);
			
			results.add("- Successfully enable AutoAnswer the User:"+matchUser.getName());
			userUpdateCount++;
		}catch (ApiException | IOException e) {
			results.add("- Failed to update autoAnswer for User: " + matchUser.getName() + " (Error: "
					+ e.getMessage());
		}
		return userUpdateCount;
	}
	
	private List<String[]> parseCSV(MultipartFile file, List<String>result) throws IOException{
		List<String[]> csvData = new ArrayList<>();
		try(InputStream inputStream = file.getInputStream();
				Scanner sc = new Scanner(inputStream,"UTF-8")){
			if(sc.hasNextLine()) {
				String headerLine = sc.nextLine();
				headerLine = headerLine.replace("\uFEFF","");
				
				if(headerLine==null || !validateHeaders(headerLine)) {
				throw new IllegalArgumentException("Invalid File Format: Headers must be 'emailId', ACDAutoAnswer(true/false)");
			}
		}
		String line;
		int rowCount = 1;
		while(sc.hasNextLine()) {
			rowCount++;
			line = sc.nextLine();
			String[] columns = line.split(",");
			if(columns.length>=2 && !columns[0].trim().isEmpty() && !columns[1].trim().isEmpty()) {
				String email = columns[0].trim();
				String acdAutoAnswer = columns[1].trim();
				
				if(acdAutoAnswer !="true" || acdAutoAnswer !="True") {
					result.add(" AcdAutoAnswer should have only true value");
					break;
				}
				
				if (email.isEmpty() || acdAutoAnswer.isEmpty()) {

					result.add(": Skipped due to missing values or invalid format.\n");
					continue;
				}
				
				csvData.add(new String[] {columns[0].trim(),columns[1].trim()});
			}
		}
		
		}return csvData;
	}
	
	private boolean validateHeaders(String headerLine) {
		String[] headers = headerLine.split(",");
		return headers.length>=2 && headers[0].equalsIgnoreCase("EmailId") && headers[1].equalsIgnoreCase("ACDAutoAnswer(true/false)");
	}
}