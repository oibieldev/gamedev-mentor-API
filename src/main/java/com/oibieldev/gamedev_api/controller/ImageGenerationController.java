package com.oibieldev.gamedev_api.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.oibieldev.gamedev_api.dto.image.ImageGenerationRequest;
import com.oibieldev.gamedev_api.dto.image.ImageGenerationResponse;
import com.oibieldev.gamedev_api.service.ImageGenerationService;

import lombok.RequiredArgsConstructor;

@CrossOrigin(origins = "*")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/images")
public class ImageGenerationController {

    private final ImageGenerationService service;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ImageGenerationResponse> generate(@RequestBody ImageGenerationRequest _request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.generateImages(_request));
    }
}
