package com.oibieldev.gamedev_api.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.oibieldev.gamedev_api.client.TextGenerationClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MentorService {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final TextGenerationClient textClient;
    public final ScratchInterpreterService scratchInterpreterService;

    public String getGeminiAnswer(String _prompt, MultipartFile _file){
        String out = "";

        if(_file == null || _file.isEmpty()) out = textClient.generateResponse(_prompt);
        else{
            if(_file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("Arquivo muito grande!");

            String mentorPrompt = "Atue como um mentor, analisando a pergunta do aluno e o projeto dele, "+
                                    "e devolvendo uma resposta que estimule a criatividade dele para descobrir "+
                                     "a resposta. Não dê a resposta pronta, gere perguntas pedagógicas para que "+
                                      "ele encontre o caminho até a solução";
            
            String projectJson = scratchInterpreterService.extractProjectJson(_file);
            String fullPrompt = mentorPrompt + "\n\nPERGUNTA DO ALUNO: " + _prompt + "\n\n PROJETO SCRATCH EM JSON DO ALUNO: \n\n" + projectJson;

            out = textClient.generateResponse(fullPrompt);
        }


        return out;
    }

}
