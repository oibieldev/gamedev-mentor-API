package com.oibieldev.gamedev_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.oibieldev.gamedev_api.dto.mentor.MentorRequest;
import com.oibieldev.gamedev_api.dto.mentor.MentorResponse;
import com.oibieldev.gamedev_api.service.MentorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MentorController {
    
    private final MentorService service;

    @PostMapping("/chat")
    public ResponseEntity<MentorResponse> postChatMethod(
        @RequestPart MentorRequest _request,
        @RequestPart (value = "file", required = false) MultipartFile _file){

        String answer;
        answer = service.getGeminiAnswer(_request.prompt(), _file);

        MentorResponse response = new MentorResponse(answer);
        return ResponseEntity.ok(response);
    }
    

}
