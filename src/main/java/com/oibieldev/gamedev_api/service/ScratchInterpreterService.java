package com.oibieldev.gamedev_api.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScratchInterpreterService {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;


    public String extractProjectJson(MultipartFile _file){
        this.validateFile(_file);

        


        return "";
    }


    private void validateFile(MultipartFile _file) {

        if (_file == null || _file.isEmpty()) {
            throw new IllegalArgumentException("O arquivo está vazio.");
        }

        if (_file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "O arquivo deve ter no máximo 10 MB."
            );
        }

        String fileName = _file.getOriginalFilename();

        if (fileName == null
                || !fileName.toLowerCase().endsWith(".sb3")) {
            throw new IllegalArgumentException(
                    "O arquivo deve possuir a extensão .sb3."
            );
        }
    }

}
