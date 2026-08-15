package com.oibieldev.gamedev_api.service.interpreters.scratch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.oibieldev.gamedev_api.service.interpreters.ProjectInterpreter;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScratchInterpreterService implements ProjectInterpreter{
    @Override
    public boolean supports(String _fileExtension) {
        return _fileExtension.equalsIgnoreCase("sb3");
    }

    @Override
    public String interpret(MultipartFile _file){
        String json = "";

        try(ZipInputStream zipInputStream =
         new ZipInputStream(_file.getInputStream())){

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
        byte[] jsonBytes;
        try{
            jsonBytes = _json.readAllBytes();
            return new String(jsonBytes, StandardCharsets.UTF_8);
            
        }catch( IOException exception ){
            throw new IllegalStateException("Não foi possível ler o projeto Scratch. ", exception); 
        }
    }


}
