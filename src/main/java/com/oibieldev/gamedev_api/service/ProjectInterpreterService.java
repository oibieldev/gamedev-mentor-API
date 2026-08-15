package com.oibieldev.gamedev_api.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.oibieldev.gamedev_api.service.interpreters.ProjectInterpreter;
import com.oibieldev.gamedev_api.service.interpreters.construct.ConstructInterpreterService;
import com.oibieldev.gamedev_api.service.interpreters.scratch.ScratchInterpreterService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class ProjectInterpreterService {
        private final long MAX_FILE_SIZE = 10 * 1024 * 1024;

        private ProjectInterpreter interpreter;
        private ScratchInterpreterService scratchInterpreterService;
        private ConstructInterpreterService constructInterpreterService;


        public String extractProjectJson(MultipartFile _file) {
            this.validateFile(_file);
            String fileName = _file.getOriginalFilename();
            String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1);

            interpreter = switch (fileExtension.toLowerCase()) {
                case "sb3" -> scratchInterpreterService;
                case "c3p" -> constructInterpreterService;
                default -> null;
            };

            if (interpreter == null) {
                throw new IllegalArgumentException(
                        "Não há um interpretador disponível para a extensão de arquivo: " + fileExtension);
            }

            return interpreter.interpret(_file);
        }


        private void validateFile(MultipartFile _file) {

        if (_file == null || _file.isEmpty()) {
            throw new IllegalArgumentException(
                "O arquivo está vazio.");
        }

        if (_file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "O arquivo deve ter no máximo 10 MB."
            );
        }
    }
}
