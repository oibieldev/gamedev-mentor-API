package com.oibieldev.gamedev_api.client.gemini;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.oibieldev.gamedev_api.client.TextGenerationClient;
import com.oibieldev.gamedev_api.dto.gemini.GeminiCandidate;
import com.oibieldev.gamedev_api.dto.gemini.GeminiContent;
import com.oibieldev.gamedev_api.dto.gemini.GeminiPart;
import com.oibieldev.gamedev_api.dto.gemini.GeminiRequest;
import com.oibieldev.gamedev_api.dto.gemini.GeminiResponse;

import lombok.RequiredArgsConstructor;


@Component
@RequiredArgsConstructor
public class GeminiTextClient implements TextGenerationClient{

    private final RestClient geminiRestClient;

    @Value("${gemini.api.model}")
    private String apiModel;

    @Override
    public String generateResponse(String _prompt){

        GeminiRequest geminiRequest = this.buildGeminiRequest(_prompt);
        GeminiResponse geminiResponse = null;      

        try{
            geminiResponse = geminiRestClient.post()
                    .uri("/v1beta/models/{model}:generateContent", apiModel)
                    .body(geminiRequest)
                    .retrieve()
                    .body(GeminiResponse.class);

        }catch(HttpClientErrorException.TooManyRequests exception){
            throw new IllegalStateException(
                    "O limite de requisições do Gemini foi atingido.",
                    exception
            );
        }

        this.validateResponse(geminiResponse);
        return this.extractText(geminiResponse);
    }

    private GeminiRequest buildGeminiRequest(String _prompt){
        return  new GeminiRequest(
                    List.of(new GeminiContent(
                            List.of(new GeminiPart(_prompt))
                    ))
        );
    }

    private void validateResponse(GeminiResponse _response){
        if (_response == null
                || _response.candidates() == null
                || _response.candidates().isEmpty()) {

            throw new IllegalStateException(
                    "O Gemini não retornou candidatos."
            );
        }

        GeminiCandidate candidate = _response.candidates().get(0);

        if (candidate == null
                || candidate.content() == null
                || candidate.content().parts() == null
                || candidate.content().parts().isEmpty()) {

            throw new IllegalStateException(
                    "O Gemini não retornou conteúdo válido."
            );
        }

        GeminiPart part = candidate.content().parts().get(0);

        if (part == null
                || part.text() == null
                || part.text().isBlank()) {

            throw new IllegalStateException(
                    "O Gemini retornou uma resposta sem texto."
            );
        }
    }

    private String extractText(GeminiResponse response) {
        return response.candidates().get(0).content().parts().get(0).text();
    }

}
