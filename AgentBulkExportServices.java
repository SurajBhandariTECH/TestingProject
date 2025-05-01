package com.example.Bulk_Skill_Creation.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mypurecloud.sdk.v2.ApiClient;
import com.mypurecloud.sdk.v2.ApiException;
import com.mypurecloud.sdk.v2.Configuration;
import com.mypurecloud.sdk.v2.api.UsersApi;
import com.mypurecloud.sdk.v2.model.DomainRole;
import com.mypurecloud.sdk.v2.model.UserEntityListing;
import com.mypurecloud.sdk.v2.model.UserQueue;
import com.mypurecloud.sdk.v2.model.UserQueueEntityListing;
import com.mypurecloud.sdk.v2.model.UserRoutingLanguage;
import com.mypurecloud.sdk.v2.model.UserRoutingSkill;
import com.opencsv.CSVWriter;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Service
public class AgentBulkExportServices {
	@Autowired 
	private HttpSession session;
	
	@Autowired
	private OrgConfigService orgConfigService;
	
	public List<String> exportAgents(HttpServletResponse response,String organizationName,String environment) throws IOException, ApiException {
		
		 List<String> results = new ArrayList<>();
		 
			Map<String, String> credentials = orgConfigService.getCredentials(organizationName);
			String clientId = credentials.get("clientId");
			String redirectUri = credentials.get("redirectUri");

			if (clientId == null || redirectUri == null || credentials.isEmpty()) {
				results.add("Error: Client credentials not found for organization" + organizationName);
				return results;
			}
		
		try {
			 String accessToken = (String) session.getAttribute("accessToken");
	            if (accessToken == null || accessToken.isEmpty()) {
	                results.add("❌ ERROR: Access token is missing. Please log in again.");
	                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No access token found. Please log in.");
	                return results;
	            }
	            
	          //Initialize Genesys API Client
	    		ApiClient apiClient = ApiClient.Builder.standard().withAccessToken(accessToken).withBasePath("https://api." + environment).build();
	    		Configuration.setDefaultApiClient(apiClient);
	    		
	    		//fetch agent list from genesys cloud
	    		int pageSize = 100; // Max allowed page size
	    		int pageNumber = 1;
	    		List<String> employerInfo = Arrays.asList("locations","skills","languages","employerInfo","team","authorization");
	    		
	    		
	    		 UsersApi usersApi = new UsersApi(apiClient);
	    		 UserEntityListing agents = usersApi.getUsers(pageSize, pageNumber, null, null, null, employerInfo,
	    					null, null);
	    		
	    		 
	    		 
	    		 writeAgentsToCSV(response, agents);
	             results.add("✅ Agent export successfully.");
		}catch (Exception e) {
            results.add("❌ ERROR: Failed to export agents. " + e.getMessage());
            e.printStackTrace();
        }
        return results;
    }
	
	
		
	// method to convert a list of any object to string

 //*---------------------future use
	public static String listToString(List<UserRoutingSkill> list) {
			
		
			if(list==null) {
				return "null";
			}
			
			StringBuilder sb = new StringBuilder();
			sb.append("[");
			
			for(int i=0;i<list.size();i++) {
				String skillName = list.get(i).getName();
				double proficiency = list.get(i).getProficiency();
				sb.append(skillName).append("-(proficiency ").append(proficiency).append(")");
				
				if(i<list.size()-1) {
					sb.append(",");
				}
			}
			sb.append("]");
			return sb.toString();
	}
	
	
	//to convert language list to one string row
	public static String LanguagelistToString(List<UserRoutingLanguage> languageList) {
		if(languageList==null) {
			return "null";
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		
		for(int i=0;i<languageList.size();i++) {
			
			String languageName = languageList.get(i).getName();
			double languageProficiency = languageList.get(i).getProficiency();
			
			sb.append(languageName).append("- (Proficiency ").append(languageProficiency).append(")");
			
			if(i<languageList.size()-1) {
				sb.append(",");
			}
		}
		sb.append("]");
		return sb.toString();
	}
	
	
	//method to convert the role list to string
	public static String RoleListToString(List<DomainRole> roleList) {
		if(roleList==null) {
			return "null";
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append("[ ");
		for(int i=0;i<roleList.size();i++) {
			String roleName = roleList.get(i).getName();
			sb.append(roleName);
			
			if(i<roleList.size()-1) {
				sb.append(", ");
			}
			
		}
		sb.append("]");
		return sb.toString();
	}
		
	public static String QueueListToString(List<UserQueue> userList) {
		if(userList==null) {
			return "null";
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append("[ ");
		for(int i=0;i<userList.size();i++) {
			String roleName = userList.get(i).getName();
			sb.append(roleName);
			
			if(i<userList.size()-1) {
				sb.append(", ");
			}
			
		}
		sb.append("]");
		return sb.toString();
	}
			
	private void writeAgentsToCSV(HttpServletResponse response, UserEntityListing agents) {
		
		try {
			
			//set response header for csv file
			response.setContentType("text/csv");
			response.setHeader("content-Disposition", "attachment; filename = agent_export.csv");
	        
			 //write data to CSV file
			 try(CSVWriter writer = new CSVWriter(new PrintWriter(response.getWriter()))){
				 
			 String[] header = { "Name", "Email", "Title", "Department","Skill"
	                 ,"Language", "Division","Roles","Queue","Manager","EmployerInfo-Official Name","EmployerInfo-EmployeeId"};
			 
			 writer.writeNext(header);
			 
			 UsersApi apiInstance = new UsersApi();
			 
			 //write each agent details
			 for(var user:agents.getEntities()) { 
				 
				
				//get the list of Skills
				List<UserRoutingSkill> skill = user.getSkills();
				//get the list of Languages
				List<UserRoutingLanguage> language = user.getLanguages();
				//get the list of Roles of User
				List<DomainRole> roles = user.getAuthorization().getRoles();
				
				//get the list of Queues assign to user
				
				Integer pageSize = 25; // Integer | Page size
				Integer pageNumber = 1; // Integer | Page number
				Boolean joined = true;
				 List<String> divisionId = Arrays.asList("");
				 UserQueueEntityListing userQueues = apiInstance.getUserQueues(user.getId(),pageSize,pageNumber,joined,divisionId);
				 List<UserQueue> queues = userQueues.getEntities();
				
				
				 // to get the manager Name
				 String managerId = (user.getManager()!=null)?user.getManager().getId():"";
				 String managerName= apiInstance.getUser(managerId, null, null, null).getName();
				 
				
				 
				 String[] row = {
						 user.getName(),
						 user.getEmail(),
						 user.getTitle(),
						 user.getDepartment(),
						 listToString(skill),
						 LanguagelistToString(language),
						 (user.getDivision() !=null)? user.getDivision().getName():"",
						 RoleListToString(roles),
						 QueueListToString(queues),
						 managerName, //manager name
						 (user.getEmployerInfo()!=null)?user.getEmployerInfo().getOfficialName():"",
						 (user.getEmployerInfo()!=null)?user.getEmployerInfo().getEmployeeId():""	
				 };

					writer.writeNext(row);
 
				 }
			 }
			 
		}catch(Exception e) {
			 e.printStackTrace();
			
		}
	}
}
