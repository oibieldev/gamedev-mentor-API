package com.oibieldev.gamedev_api.service.interpreters.construct;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.oibieldev.gamedev_api.service.interpreters.ProjectInterpreter;

@Service
public class ConstructInterpreterService implements ProjectInterpreter {

    @Override
    public boolean supports(String _fileExtension) {
        return "c3p".equalsIgnoreCase(_fileExtension);
    }

    @Override
    public String interpret(MultipartFile _file) {
        String objectTypes = "";
        String layouts = "";
        String eventSheets = "";
        String project = "{}";
        
        try (ZipInputStream zipInputStream = new ZipInputStream(_file.getInputStream())) {
            ZipEntry entry = zipInputStream.getNextEntry();
            
            while (entry != null) {
                String entryName = entry.getName();
                if (entry.isDirectory()) {
                    entry = zipInputStream.getNextEntry();
                    continue;
                }

                if ("project.c3proj".equals(entryName)) {
                    project = this.convertJsonToString(zipInputStream);
                }

                if (entryName.startsWith("objectTypes/")
                    && entryName.endsWith(".json")) {

                    String currentObjectTypeJson = this.convertJsonToString(zipInputStream);
                    objectTypes = this.appendJson(
                        objectTypes, 
                        currentObjectTypeJson
                    );
                }
                if (entryName.startsWith("layouts/")
                    && entryName.endsWith(".json")
                     && !entryName.endsWith(".uistate.json")) {

                    String currentLayoutJson = this.convertJsonToString(zipInputStream);
                    layouts = this.appendJson(
                        layouts, 
                        currentLayoutJson
                    );
                }
                if (entryName.startsWith("eventSheets/")
                    && entryName.endsWith(".json")
                     && !entryName.endsWith(".uistate.json")) {

                    String currentEventSheetJson = this.convertJsonToString(zipInputStream);
                    eventSheets = this.appendJson(
                        eventSheets, 
                        currentEventSheetJson
                    ); 
                }

                entry = zipInputStream.getNextEntry();
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Não foi possível ler o projeto Construct 3. Exception: "
                 + exception.getMessage(), 
                 exception
                );    
        }

        return """
                {
                "project": %s,
                "objectTypes": [%s],
                "layouts": [%s],
                "eventSheets": [%s]
                }
                """.formatted(
                    project,
                    objectTypes,
                    layouts,
                    eventSheets
                ); 
    }

    private String convertJsonToString(ZipInputStream _zipInputStream) {
        byte[] jsonBytes;
        try{
            jsonBytes = _zipInputStream.readAllBytes();
            return new String(jsonBytes, StandardCharsets.UTF_8);
            
        }catch( IOException exception ){
            throw new IllegalStateException("Não foi possível ler o projeto Construct 3. Exception: " + exception.getMessage(), exception); 
        }
    }

    private String appendJson(String _json1, String _json2) {
        if (_json1.isEmpty()) {
            return _json2;
        }
        if (_json2.isEmpty()) {
            return _json1;
        }
        return _json1 + "," + _json2;
    }

}
