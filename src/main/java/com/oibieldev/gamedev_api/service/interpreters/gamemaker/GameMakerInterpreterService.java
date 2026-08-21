package com.oibieldev.gamedev_api.service.interpreters.gamemaker;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.oibieldev.gamedev_api.service.interpreters.ProjectInterpreter;


@Service
public class GameMakerInterpreterService implements ProjectInterpreter {
    @Override
    public boolean supports(String _fileExtension) {
        return "yyz".equalsIgnoreCase(_fileExtension);
    }

    @Override
    public String interpret(MultipartFile _file) {
        // Implement the logic to interpret GameMaker project files here
        return "{}"; // Placeholder return value
    }

}
