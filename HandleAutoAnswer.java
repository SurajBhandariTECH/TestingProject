package com.example.Genesys_Functionalities.handler;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.Genesys_Functionalities.service.AutoAnswerService;
import com.example.Genesys_Functionalities.service.OrgConfigService;

import jakarta.servlet.http.HttpSession;


@Service
public class HandleAutoAnswer {
	
	@Autowired
	private OrgConfigService orgConfigService;
	
	@Autowired
	private AutoAnswerService autoAnswerService;
	
	public String handleAutoAnswer(MultipartFile file,HttpSession session, Model model, RedirectAttributes redirectAttributes ) {
		
		try {
			String organizationName = (String) session.getAttribute("organizationName");
			String environment = (String) session.getAttribute("environment");

			if (organizationName == null || environment == null) {
				model.addAttribute("errorMessage", "Session expired. Please login again.");
				return "login";
			}
			
			String token =(String) session.getAttribute("accesstoken");
			
			if (token == null || !orgConfigService.validateAccessToken(token, environment)) {
				throw new IllegalArgumentException(
						"Client credentials have changed or are invalid. Please log in again:");
			}
			
			//processing the csv file and enabling the autoanswer
			List<String> results = autoAnswerService.enableAutoAnswer(file, organizationName,environment,token);
			// add result to model for display
						// add the confirmation message and results to the redirect attributes so it can
						// be displayed in the success message
		
			model.addAttribute("confirmationMessage", "Connect to Genesys org:" + organizationName);
			model.addAttribute("successMessage", "autoAnswer file uploaded successfully.");
			model.addAttribute("results", results);
			
		}
		catch (IllegalArgumentException e) {
			model.addAttribute("errorMessage", e.getMessage());
			return "login";
		} catch (IOException e) {
			model.addAttribute("errorMessage", e.getMessage());
			return "autoAnswer";
		} catch (Exception e) {
			model.addAttribute("errorMessage", "Error processing the file: " + e.getMessage());
			return "autoAnswer";
		}
		
		return "autoAnswer";
		
	}
	
	

}
