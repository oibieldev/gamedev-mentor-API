package com.oibieldev.gamedev_api.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.oibieldev.gamedev_api.dto.image.ImageGenerationRequest;
import com.oibieldev.gamedev_api.dto.image.ImageGenerationResponse;
import com.oibieldev.gamedev_api.dto.mentor.MentorResponse;
import com.oibieldev.gamedev_api.service.ImageGenerationService;
import com.oibieldev.gamedev_api.service.MentorService;

import lombok.RequiredArgsConstructor;

@CrossOrigin(origins = "*")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MentorController {
    
    private final MentorService service;
    private final ImageGenerationService imageService;

    @PostMapping("/chat")
    public ResponseEntity<MentorResponse> chat(
        @RequestPart ("prompt") String _prompt,
        @RequestPart (value = "file", required = false) MultipartFile _file){

        String answer;
        answer = service.getMentorResponse(_prompt, _file);

        MentorResponse response = new MentorResponse(answer);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/images", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ImageGenerationResponse> generateImages(@RequestBody ImageGenerationRequest _request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(imageService.generateImages(_request));
    }
}
