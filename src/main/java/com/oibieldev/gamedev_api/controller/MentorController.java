package com.oibieldev.gamedev_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import com.oibieldev.gamedev_api.dto.mentor.MentorRequest;
import com.oibieldev.gamedev_api.dto.mentor.MentorResponse;
import com.oibieldev.gamedev_api.service.MentorService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MentorController {
    
    private final MentorService service;

    @PostMapping("/chat")
    public ResponseEntity<MentorResponse> postChatMethod(
        @RequestBody MentorRequest _request){
            String answer = service.getGeminiAnswer(_request.prompt());

            MentorResponse response = new MentorResponse(answer);
        return ResponseEntity.ok(response);
    }
    

}
