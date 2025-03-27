 package com.example.Genesys_Functionalities.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.Genesys_Functionalities.handler.HandleAutoAnswer;
import com.example.Genesys_Functionalities.handler.HandleLanguageSkill;
import com.example.Genesys_Functionalities.handler.HandleSkillUpload;
import com.example.Genesys_Functionalities.handler.HandleWrapupCode;
import com.example.Genesys_Functionalities.service.AgentBulkExportServices;
import com.example.Genesys_Functionalities.service.GenesysServices;
import com.example.Genesys_Functionalities.service.LanguageSkillServices;
import com.example.Genesys_Functionalities.service.OrgConfigService;
import com.example.Genesys_Functionalities.service.UserService;
import com.example.Genesys_Functionalities.service.WrapUpCodeServices;
import com.mypurecloud.sdk.v2.ApiClient;
import com.mypurecloud.sdk.v2.ApiException;
import com.mypurecloud.sdk.v2.Configuration;
import com.mypurecloud.sdk.v2.api.AuthorizationApi;
import com.mypurecloud.sdk.v2.api.OrganizationApi;
import com.mypurecloud.sdk.v2.api.UsersApi;
import com.mypurecloud.sdk.v2.model.DomainRole;
import com.mypurecloud.sdk.v2.model.Organization;
import com.mypurecloud.sdk.v2.model.UserAuthorization;
import com.mypurecloud.sdk.v2.model.UserMe;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/genesysContactCenter")
public class SkillController {

	@Autowired
	private OrgConfigService orgConfigService;

	@Autowired
	private GenesysServices genesysService;
	
	@Autowired
	private AgentBulkExportServices agentBulkExportServices;

	@Autowired
	private UserService userService;
	
	@Autowired
	private HandleSkillUpload handleSkillUpload;
	
	@Autowired
	private  HandleAutoAnswer autoAnswer;
	
	@Autowired
	private HandleLanguageSkill handlelanguageSkill;
	
	@Autowired
	private HandleWrapupCode handleWrapUpCode;

	private static final String requriedRole = "custom-Import-Role";

	public SkillController(GenesysServices genesysService) {
		this.genesysService = genesysService;
	}

	// Show login form
	@GetMapping("/login")
	public String showLoginForm(Model model) {
		model.addAttribute("organizationName", orgConfigService.getAvailableOrganizations());
		return "login";
	}

	@GetMapping("/authorize")
	public String handleAuthorization(@RequestParam("organizationName") String organizationName,
			@RequestParam("environment") String environment, HttpSession session, HttpServletRequest request,
			Model model) {

		session.invalidate();

		// Start a new session
		HttpSession newSession = request.getSession(true);
		String clientId = orgConfigService.getClientId(organizationName);
		String redirectUri = orgConfigService.getRedirectUri(organizationName);

		System.out.println(clientId + " " + redirectUri);
		String authUrl = "https://login." + environment + "/oauth/authorize?response_type=token" + "&client_id="
				+ clientId + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
				+ "&force_login=true";

		// save the organizationName and environment in session
		newSession.setAttribute("organizationName", organizationName);
		newSession.setAttribute("environment", environment);

		System.out.println(authUrl);

		return "redirect:" + authUrl;
	}

	// handle the oauth callback

	@GetMapping("/callback")
	public String handleCallback(HttpSession session, RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("errormessage",
				"No access token recieved. Please ensure javascript is extracting the token");
		return "redirect:/genesysContactCenter/login";
	}

	@GetMapping("/callbackServer")
	public String handleServerCallback(@RequestParam(value = "access_token", required = false) String accessToken,
			HttpServletRequest request, HttpSession session, RedirectAttributes redirectAttributes) {

		if (accessToken == null || accessToken.isEmpty()) {
			redirectAttributes.addFlashAttribute("errorMessage", "Authorization failed. please try again.");
			return "redirect:/genesysContactCenter/login";
		}

		String organizationName = (String) session.getAttribute("organizationName");
		String environment = (String) session.getAttribute("environment");

		// save accesstoken in the session
		session.setAttribute("accessToken", accessToken);

		// initialize the API client with the access token

		ApiClient apiClient = ApiClient.Builder.standard().withAccessToken(accessToken)
				.withBasePath("https://api." + environment).build();
		Configuration.setDefaultApiClient(apiClient);

		try {
			// Fetch logged-in user details

			UsersApi usersApi = new UsersApi(apiClient);
			List<String> expand = Arrays.asList("");
			String integrationPresenceSource = "";

			UserMe currentUser = usersApi.getUsersMe(expand, integrationPresenceSource);
			session.setAttribute("currentUserId", currentUser.getId());

			System.out.println(currentUser);

			// fetch organization detail seperately
			OrganizationApi organizationApi = new OrganizationApi(apiClient);
			Organization organization = organizationApi.getOrganizationsMe();
			String actualOrgName = organization.getName();
			actualOrgName = actualOrgName.replace(" ", "");

			System.out.println(actualOrgName);

			// Validae that the Selected organization matches the actual logged -in org

			if (!actualOrgName.equalsIgnoreCase(organizationName)) {
				session.invalidate();
				HttpSession newSession = request.getSession(true);// create new session

				redirectAttributes.addFlashAttribute("errorMessage",
						"Organization Validation failed. Please select the correct environment.");
				return "redirect:/genesysContactCenter/logout";
			}

			System.out.println(currentUser.getId());

			// fetch user roles
			AuthorizationApi authorizationApi = new AuthorizationApi(apiClient);
			UserAuthorization userRoles = authorizationApi.getUserRoles(currentUser.getId());
			// Extract role names
			List<String> rolesName = userRoles.getRoles().stream().map(DomainRole::getName)
					.collect(Collectors.toList());
			System.out.println(rolesName);

			// Extract role id(if needed)
			List<String> rolesId = userRoles.getRoles().stream().map(DomainRole::getId).collect(Collectors.toList());

			// Storing role names in session
			session.setAttribute("userRoles", rolesName);

			// check if user has the required role

			if (rolesName.contains(requriedRole)) {
				redirectAttributes.addFlashAttribute("ConfirmationMessage",
						"Successfully connected to genesys." + organizationName);
				return "redirect:/genesysContactCenter/importOptions";
			} else {
				return "redirect:/genesysContactCenter/accessDenied";
			}

		} catch (Exception e) {

			session.invalidate();// clear session in case of errors
			HttpSession newSession = request.getSession(true);// Ensure fresh session
			redirectAttributes.addFlashAttribute("errorMessage",
					"Error retrieving user roles: " + e.getMessage() + e.fillInStackTrace());
			return "redirect:/genesysContactCenter/login";

		}

	}

	@GetMapping("/logout")
	public String logout(HttpServletRequest request, HttpSession session, Model model) {
		String environment = (String) session.getAttribute("environment");
		session.invalidate(); // clear session completely

		String redirectAfterLogout = "http://localhost:8080/genesysContactCenter/login";
		String genesysLogoutUrl = "https://login." + environment + "/logout?" + "redirect_uri="
				+ URLEncoder.encode(redirectAfterLogout, StandardCharsets.UTF_8);

		model.addAttribute("genesysLogoutUrl", genesysLogoutUrl);
		model.addAttribute("redirectAfterLogout", redirectAfterLogout);

		return "logoutPage";
	}

	@GetMapping("/afterLogout")
	public String forceLogout() {
		System.out.println("genesys logout complete. Redirecting to login page...");
		return "redirect:/genesysContactCenter/login";
	}

	@GetMapping("/accessDenied")
	public String accessDeniedPage(Model model) {
		model.addAttribute("errorMessage", " You do not have permission to access this page");
		return "accessDenied";
	}

	// Show choose import options page
	@GetMapping("/importOptions")
	public String showChooseImportPage(HttpSession session, Model model) {
		String organizationName = (String) session.getAttribute("organizationName");
		String environment = (String) session.getAttribute("environment");

		if (organizationName == null || environment == null) {
			model.addAttribute("errorMessage", "Session expired. Please log in again.");
			return "login";
		}

		String confirmationMessage = "Connected to Genesys org: " + organizationName;
		model.addAttribute("confirmationMessage", confirmationMessage);
		model.addAttribute("organizationName", organizationName);
		model.addAttribute("environment", environment);

		return "importOptions";
	}

	// show upload page for bulk skill
	@GetMapping("/upload")
	public String showUploadPage(HttpSession session, Model model) {

		String organizationName = (String) session.getAttribute("organizationName");
		String environment = (String) session.getAttribute("environment");

		if (organizationName == null || environment == null) {
			model.addAttribute("errorMessage", "Session expired. Please log in again.");
			return "login";
		}

		String confirmationMessage = "Connected to Genesys org: " + organizationName;
		model.addAttribute("confirmationMessage", confirmationMessage);
		model.addAttribute("organizationName", organizationName);
		model.addAttribute("environment", environment);

		return "upload";
	}

	@GetMapping("/deleteSkill")
	public String showUploadSkillPage(HttpSession session, Model model) {
		String organizationName = (String) session.getAttribute("organizationName");
		String environment = (String) session.getAttribute("environment");

		if (organizationName == null || environment == null) {
			model.addAttribute("errorMessage", "Session expired. Please log in again.");
			return "login";
		}

		String confirmationMessage = "Connected to Genesys org: " + organizationName;
		model.addAttribute("confirmationMessage", confirmationMessage);
		model.addAttribute("organizationName", organizationName);
		model.addAttribute("environment", environment);

		return "upload";
	}

	// Show upload page for Bulk Language Skill
	@GetMapping("/uploadLanguageSkill")
	public String showUploadLanguageSkillPage(HttpSession session, Model model) {
		String organizationName = (String) session.getAttribute("organizationName");
		String environment = (String) session.getAttribute("environment");

		if (organizationName == null || environment == null) {
			model.addAttribute("errorMessage", "Session expired. Please log in again.");
			return "login";
		}

		String confirmationMessage = "Connected to Genesys org: " + organizationName;
		model.addAttribute("confirmationMessage", confirmationMessage);
		model.addAttribute("organizationName", organizationName);
		model.addAttribute("environment", environment);

		return "uploadLanguageSkill"; // Create this page for Language Skill upload
	}

	// show the upload language skill page for deleting languageSkill
	@GetMapping("/deleteLanguageSkill")
	public String showDeleteLangaugeSkillPage(HttpSession session, Model model) {
		String organizationName = (String) session.getAttribute("organizationName");
		String environment = (String) session.getAttribute("environment");

		if (organizationName == null || environment == null) {
			model.addAttribute("errorMessage", "Session expired. Please log in again.");
			return "login";
		}

		String confirmationMessage = "Connected to Genesys org: " + organizationName;
		model.addAttribute("confirmationMessage", confirmationMessage);
		model.addAttribute("organizationName", organizationName);
		model.addAttribute("environment", environment);

		return "uploadLanguageSkill";
	}

	@GetMapping("/uploadWrapUpCode")

	public String uploadWrapUpCodePage(HttpSession session, Model model) {
		String organizationName = (String) session.getAttribute("organizationName");
		String environment = (String) session.getAttribute("environment");

		if (organizationName == null || environment == null) {
			model.addAttribute("errorMessage", "Session expired. Please log in again");
			return "login";
		}

		String confirmationMessage = "Connected to genesys org: " + organizationName;
		model.addAttribute("organizationName", organizationName);
		model.addAttribute("environment", environment);
		model.addAttribute("confirmationMessage", confirmationMessage);

		return "uploadWrapUpCode";
	}

	@GetMapping("/deleteWrapUpCode")

	public String deleteWrapUpCodePage(HttpSession session, Model model) {
		String organizationName = (String) session.getAttribute("organizationName");
		String environment = (String) session.getAttribute("environment");

		if (organizationName == null || environment == null) {
			model.addAttribute("errorMessage", "Session expired. Please log in again");
			return "login";
		}

		String confirmationMessage = "Connected to genesys org: " + organizationName;
		model.addAttribute("organizationName", organizationName);
		model.addAttribute("environment", environment);
		model.addAttribute("confirmationMessage", confirmationMessage);

		return "uploadWrapUpCode";
	}

	@GetMapping("/uploadUser")
	public String EmployeerInfoUpdation(HttpSession httpSession, Model model) {
		String organizationName = (String) httpSession.getAttribute("organizationName");
		String environment = (String) httpSession.getAttribute("environment");

		if (organizationName == null || environment == null) {
			model.addAttribute("errorMessage", "Session expired. Please log in again");
			return "login";

		}
		String confirmationMessage = "Connected to genesys org: " + organizationName;
		model.addAttribute("organizationName", organizationName);
		model.addAttribute("environment", environment);
		model.addAttribute("confirmationMessage", confirmationMessage);
		return "uploadUser";

	}
	
	@GetMapping("/autoAnswer")
	public String autoAnswerEnableOrDisable(HttpSession session, Model model) {
		
		String organizationName = (String) session.getAttribute("organizationName");
		String environment = (String) session.getAttribute("environment");
		if (organizationName == null || environment == null) {
			model.addAttribute("errorMessage", "Session expired. Please log in again");
			return "login";

		}
		
		String confirmationMessage ="Connect to genesys org: " + organizationName;
		model.addAttribute("organizationName",organizationName);
		model.addAttribute("environment", environment);
		model.addAttribute("confirmationMessage", confirmationMessage);
		return "autoAnswer";
		
	}
	
	@PostMapping("/autoAnswer")
	public String autoAnswer(@RequestParam("file") MultipartFile file, HttpSession httpSession,
			RedirectAttributes redirectAttributes, Model model) {

		return autoAnswer.handleAutoAnswer(file, httpSession, model, redirectAttributes);
	}

	
	//redirect to Agent Export page
		@GetMapping("/agentExportPage")
		public String showAgentExportPage(HttpSession session, Model model) {
			
			String organizationName = (String) session.getAttribute("organizationName");
			String environment = (String) session.getAttribute("environment");

			if (organizationName == null || environment == null) {
				model.addAttribute("errorMessage", "Session expired. Please log in again");
				return "login";

			}
			String confirmationMessage = "Connected to genesys org: " + organizationName;
			model.addAttribute("organizationName", organizationName);
			model.addAttribute("environment", environment);
			model.addAttribute("confirmationMessage", confirmationMessage);
			return "agentExport";
		}
		
		@GetMapping("/exportAgents")
		public String exportAgents(HttpServletResponse response,RedirectAttributes redirectAttributes,HttpSession session) throws IOException, ApiException {
			String organizationName = (String) session.getAttribute("organizationName");
			String environment = (String) session.getAttribute("environment");
			List<String> results = agentBulkExportServices.exportAgents(response,organizationName,environment);
			
			// Add messages to display on the Agent Export page
	        redirectAttributes.addFlashAttribute("exportMessages", results);
	        
	        return "redirect:/genesysContactCenter/agentExportPage";
		}
		
	
	@PostMapping("/upload")
	public String handleSkillFileUpload(@RequestParam("file") MultipartFile file, HttpSession session, Model model) {
		// model.addAttribute("confirmationMessage", confirmationMessage);
		return handleSkillUpload.handleSkillsFileUpload(file, session, model);

	}

	// handle the deletion of skill
	@PostMapping("/deleteSkill")
	public String handleDeleteSkillFileUpload(@RequestParam("file") MultipartFile file, HttpSession session,
			Model model, RedirectAttributes redirectAttributes) {
		return handleSkillUpload.handleDeleteSkillFile(file, session, model, redirectAttributes);

	}

	// Handle File Upload for Bulk Language Skills
	@PostMapping("/uploadLanguageSkill")
	public String handleLanguageSkillFileUpload(@RequestParam("file") MultipartFile file, HttpSession httpSession,
			RedirectAttributes redirectAttributes, Model model) {

		return handlelanguageSkill.handleLanguageSkillFile(file, httpSession, model, redirectAttributes);
	}

	// handle deletion of language skills
	@PostMapping("/deleteLanguageSkill")
	public String handleDeleteLanguageSkill(@RequestParam("file") MultipartFile file, HttpSession session, Model model,
			RedirectAttributes redirectAttributes) {
		return handlelanguageSkill.handleDeleteLanguageSkillFile(file, session, model, redirectAttributes);

	}

	// Handle File Upload for Bulk Wrap-Up Code (with different validation and
	// functionality)
	@PostMapping("/uploadWrapUpCode")
	public String handleWrapUpCodeFileUpload(@RequestParam("file") MultipartFile file, HttpSession httpSession,
			RedirectAttributes redirectAttributes, Model model) {
		return handleWrapUpCode.handleWrapUpCodeFile(file, httpSession, redirectAttributes, model);
	}

	@PostMapping("/deleteWrapUpCode")
	public String handleDeletewrapUpCode(@RequestParam("file") MultipartFile file, HttpSession session, Model model,
			RedirectAttributes redirectAttributes) {
		return handleWrapUpCode.handleDeleteWrapUpCodeFile(file, session, model, redirectAttributes);

	}

	@PostMapping("/uploadUser")
	public String handleUserUpdate(@RequestParam("file") MultipartFile file, HttpSession session,
			RedirectAttributes redirectAttributes, Model model) {
		return handleUserEmployerInfoFile(file, session, model, redirectAttributes);
	}	

	// handle file upload
	

	public String handleUserEmployerInfoFile(MultipartFile file, HttpSession session, Model model,
			RedirectAttributes redirectAttributes) {
		try {
			String organizationName = (String) session.getAttribute("organizationName");
			String environment = (String) session.getAttribute("environment");

			if (organizationName == null || environment == null) {
				model.addAttribute("errorMessage", "Session expired. Please log in again.");
				return "login";
			}
			String token = (String) session.getAttribute("accessToken");
			if (token == null || !orgConfigService.validateAccessToken(token, environment)) {
				throw new IllegalArgumentException(
						"Client credentials have changed or are invalid. Please log in again:");
			}

			List<String> results = userService.updateUsersFromCsv(file, organizationName, environment, token);
			model.addAttribute("confirmationMessage", "Connected to Genesys org: " + organizationName);
			model.addAttribute("results", results);
			model.addAttribute("successMessage", "file upload successfully.");

		} catch (IllegalArgumentException e) {
			model.addAttribute("errorMessage", e.getMessage());
			return "login";
		} catch (IOException e) {
			model.addAttribute("errorMessage", "File processing error: " + e.getMessage());
			return "uploadUser";
		} catch (Exception e) {
			model.addAttribute("errorMessage", "Error processing the file: " + e.getMessage());
			return "uploadUser";
		}

		return "uploadUser";
	}
	

}