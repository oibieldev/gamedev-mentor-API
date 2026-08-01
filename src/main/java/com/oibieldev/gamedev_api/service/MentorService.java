package com.oibieldev.gamedev_api.service;

import org.springframework.stereotype.Service;

import com.oibieldev.gamedev_api.client.TextGenerationClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MentorService {

    private final TextGenerationClient textClient;

    public String getGeminiAnswer(String _request){
        return textClient.generateResponse(_request);
    }

}
