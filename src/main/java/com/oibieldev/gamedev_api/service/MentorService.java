package com.oibieldev.gamedev_api.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.oibieldev.gamedev_api.client.TextGenerationClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MentorService {
    private final TextGenerationClient textClient;
    private final ProjectInterpreterService projectInterpreterService;

    public String getMentorResponse(String _prompt, MultipartFile _file){
        String tempPrompt = 
                """
                Atue como um mentor, analisando a pergunta do aluno e o projeto dele.
                Estimule a criatividade do aluno e ajude-o a descobrir a resposta.
                Não entregue a solução pronta. Gere perguntas pedagógicas que o
                conduzam até a solução.

                PERGUNTA DO ALUNO:

                %s
                """.formatted(_prompt);

        if(_file == null || _file.isEmpty()) return textClient.generateResponse(tempPrompt) ;
        
        String projectJson = projectInterpreterService.extractProjectJson(_file);
        String fullPrompt =  tempPrompt + "\n\n PROJETO "+ _file.getOriginalFilename() +" EM JSON DO ALUNO: \n\n" + projectJson;

        return textClient.generateResponse(fullPrompt);
        

    }

}
