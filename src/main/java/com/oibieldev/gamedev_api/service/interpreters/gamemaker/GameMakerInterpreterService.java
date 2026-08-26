package com.oibieldev.gamedev_api.service.interpreters.gamemaker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.oibieldev.gamedev_api.service.interpreters.ProjectInterpreter;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;


@Service
public class GameMakerInterpreterService implements ProjectInterpreter {
    private final ObjectMapper objectMapper = JsonMapper.builder()
    .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
    .build();


    @Override
    public boolean supports(String _fileExtension) {
        return "yyz".equalsIgnoreCase(_fileExtension);
    }

    @Override
    public String interpret(MultipartFile _file) {
        String project = "{}";

        String objects = "";
        String rooms = "";
        String sprites = "";
        String code = "";

        try (ZipInputStream zipInputStream = new ZipInputStream(_file.getInputStream())){

            ZipEntry entry = zipInputStream.getNextEntry();
            while(entry != null){
                if(entry.isDirectory()){
                    entry = zipInputStream.getNextEntry();
                    continue;
                }

                String entryName = entry.getName();

                
                if(entryName.endsWith(".yyp")){
                    project = this.convertJsonToString(zipInputStream);
                }
                
                
                if(entryName.startsWith("objects/") && entryName.endsWith(".yy")){
                    String currentObjectJson = this.convertJsonToString(zipInputStream);
                    objects = this.appendJson(objects, currentObjectJson);
                }

                if(entryName.startsWith("rooms/") && entryName.endsWith(".yy")){
                    String currentRoomJson = this.convertJsonToString(zipInputStream);
                    rooms = this.appendJson(rooms, currentRoomJson);
                }
                
                if(entryName.startsWith("sprites/") && entryName.endsWith(".yy")){
                    String currentSpriteJson = this.convertJsonToString(zipInputStream);
                    sprites = this.appendJson(sprites, currentSpriteJson);
                }

                if(entryName.endsWith(".gml")){
                    String currentScriptJson = this.readCode(zipInputStream, entryName);
                    code = this.appendJson(code, currentScriptJson);
                }

                entry = zipInputStream.getNextEntry();
            }
            
            if ("{}".equals(project)) {
                throw new IllegalStateException(
                    "O projeto GameMaker não possui um arquivo .yyp."
                );
            }
            
            
        } catch (IOException _exception) {
            throw new IllegalStateException(
                "Não foi possível ler o projeto GameMaker. Exception: "
                 + _exception.getMessage(), 
                 _exception
                );    
        }
            
        return """
            {
                "project": %s,
                "objects": [%s],
                "rooms": [%s],
                "sprites": [%s],
                "code": [%s]
                }
            """.formatted(
                project,
                objects,
                rooms,
                sprites,
                code
            );
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

    private String convertJsonToString(ZipInputStream _zipInputStream) {
        try {

            String json = new String(
                _zipInputStream.readAllBytes(),
                StandardCharsets.UTF_8
            );

            JsonNode jsonNode =
                objectMapper.readTree(json);

            return objectMapper.writeValueAsString(
                jsonNode
            );

        } catch (IOException _exception) {

            throw new IllegalStateException(
                "Não foi possível interpretar um arquivo JSON do GameMaker. Exception: "
                    + _exception.getMessage(),
                _exception
            );
        }
    }

    private String readCode(ZipInputStream _zipInputStream, String _fileName){

        try {

            String content = new String(_zipInputStream.readAllBytes(), StandardCharsets.UTF_8 );
            ObjectNode codeNode = objectMapper.createObjectNode();

            codeNode.put( "file", _fileName );
            codeNode.put( "content", content );

            return objectMapper.writeValueAsString(codeNode);

            } catch (IOException _exception) {

                throw new IllegalStateException(
                "Não foi possível ler um arquivo GML do GameMaker. Exception: "
                    + _exception.getMessage(),
                _exception
                );
            }
    }
}
