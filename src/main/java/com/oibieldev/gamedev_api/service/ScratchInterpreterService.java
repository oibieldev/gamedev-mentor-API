package com.oibieldev.gamedev_api.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScratchInterpreterService {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;


    public String extractProjectJson(MultipartFile _file){
        this.validateFile(_file);

        String json = "";

        try{
            ZipInputStream zipInputStream = new ZipInputStream(_file.getInputStream());

            ZipEntry entry = zipInputStream.getNextEntry();
            while(entry != null){

                if("project.json".equals(entry.getName())){
                    json = this.convertJsonToString(zipInputStream);
                    break;
                }
                
                entry = zipInputStream.getNextEntry();
            }

        }catch(Exception e){
            e.printStackTrace();
        }

        return json;
    }

    private String convertJsonToString(ZipInputStream _json){
            String out = "";

            byte[] jsonBytes = null;
            try{
                jsonBytes = _json.readAllBytes();
                out = new String(jsonBytes, StandardCharsets.UTF_8);
                
            }catch( IOException exception ){
                throw new IllegalStateException("Não foi possível ler o projeto Scratch. ", exception); 
            }

            return out;
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
