package com.example.Genesys_Functionalities.handler;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.Genesys_Functionalities.service.GenesysServices;
import com.example.Genesys_Functionalities.service.OrgConfigService;

import jakarta.servlet.http.HttpSession;

@Service
public class HandleSkillUpload {
	
	@Autowired
	OrgConfigService orgConfigService;
	@Autowired
	GenesysServices genesysService;
	
	public String handleSkillsFileUpload(MultipartFile file, HttpSession httpSession, Model model) {

		try {

			String organizationName = (String) httpSession.getAttribute("organizationName");
			String environment = (String) httpSession.getAttribute("environment");

			if (organizationName == null || environment == null) {
				model.addAttribute("errorMessage", "Session expired. Please login in again.");
				return "login";
			}

			String token = (String) httpSession.getAttribute("accessToken");
			if (token == null || !orgConfigService.validateAccessToken(token, environment)) {
				throw new IllegalArgumentException(
						"Client credentials have changed or are invalid. Please log in again:");
			}

			// process the CSV file and create Skill
			List<String> results = genesysService.createSkillFromCsv(file, organizationName, environment, token);
			// add the confirmation message and results to the redirect attributes so it can
			// be displayed in the success message
			model.addAttribute("confirmationMessage", "Connect to Genesys org:" + organizationName);
			model.addAttribute("results", results);
			model.addAttribute("successMessage", "Skill Upload successfully");

			// redirectAttributes.addFlashAttribute("results",results);

		} catch (IllegalArgumentException e) {
			model.addAttribute("errorMessage" + e.getLocalizedMessage());
			return "login";
		} catch (IOException e) {
			model.addAttribute("errorMessage", "File process error: " + e.getMessage());
			return "upload";
		} catch (Exception e) {
			model.addAttribute("errorMessage", "Error processing the files:" + e.getMessage());
			return "upload";
		}
		return "upload";
	}
	
	public String handleDeleteSkillFile(MultipartFile file, HttpSession session, Model model,
			RedirectAttributes redirectAttributes) {
		try {

			String organizationName = (String) session.getAttribute("organizationName");
			String environment = (String) session.getAttribute("environment");
			String token = (String) session.getAttribute("accessToken");

			if (organizationName == null || environment == null) {
				model.addAttribute("errorMessage", "Session expired. Please log in again.");
				return "login";
			}

			if (token == null || !orgConfigService.validateAccessToken(token, environment)) {
				throw new IllegalArgumentException(
						"Client credentials have changed or are invalid. Please log in again:");
			}

			// process the CSV file and delete skills
			List<String> results = genesysService.deleteSkillsFromCSV(file, organizationName, environment, token);

			model.addAttribute("confirmationMessage", "Connected to Genesys org: " + organizationName);
			model.addAttribute("results", results);
			model.addAttribute("successMessage", "file upload successfully.");
		}

		catch (IllegalArgumentException e) {
			model.addAttribute("errorMessage", e.getMessage());
			return "login";
		} catch (IOException e) {
			model.addAttribute("errorMessage", "File processing error: " + e.getMessage());
			return "upload";
		} catch (Exception e) {
			model.addAttribute("errorMessage", "Error processing the file: " + e.getMessage());
			return "upload";
		}

		return "upload";
	}
}
