package com.oibieldev.gamedev_api.service.interpreters;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class ProjectInterpreterService {
        private final long MAX_FILE_SIZE = 10 * 1024 * 1024;
        private final List<ProjectInterpreter> interpreters;



        public String extractProjectJson(MultipartFile _file) {
            this.validateFile(_file);

            String fileName = _file.getOriginalFilename();
            if (fileName == null || !fileName.contains(".")) {
                throw new IllegalArgumentException(
                "Não foi possível identificar a extensão do arquivo."
                );

            }
            String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1);
            ProjectInterpreter interpreter = interpreters.stream()
                .filter(currentInterpreter ->
                    currentInterpreter.supports(fileExtension)
                )
                .findFirst()
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "Não há um interpretador disponível para a extensão: "
                        + fileExtension
                    )
                );

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
