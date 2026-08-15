package com.oibieldev.gamedev_api.service.interpreters.construct;

import org.springframework.web.multipart.MultipartFile;

import com.oibieldev.gamedev_api.service.interpreters.ProjectInterpreter;

public class ConstructInterpreterService implements ProjectInterpreter {

    @Override
    public boolean supports(String _fileExtension) {
        return _fileExtension.equalsIgnoreCase("c3p");
    }

    @Override
    public String interpret(MultipartFile _file) {
        // Implement the logic to extract project JSON from Construct 3 project file (.c3p)
        // This is a placeholder implementation and should be replaced with actual logic.
        return "{}"; // Return an empty JSON object as a placeholder.
    }

}
