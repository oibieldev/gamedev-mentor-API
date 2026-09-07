package com.oibieldev.gamedev_api.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.oibieldev.gamedev_api.client.TextGenerationClient;
import com.oibieldev.gamedev_api.service.MentorService;
import com.oibieldev.gamedev_api.service.ProjectInterpreterService;

class MentorControllerTests {

    private TextGenerationClient textClient;
    private ProjectInterpreterService interpreter;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        textClient = mock(TextGenerationClient.class);
        interpreter = mock(ProjectInterpreterService.class);
        mvc = MockMvcBuilders.standaloneSetup(new MentorController(new MentorService(textClient, interpreter)))
                .setControllerAdvice(new ImageGenerationExceptionHandler())
                .build();
    }

    @Test
    void preservesChatWithoutProjectFile() throws Exception {
        when(textClient.generateResponse(anyString())).thenReturn("Como seu personagem detecta o chão?");

        mvc.perform(multipart("/api/chat").file(promptPart()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Como seu personagem detecta o chão?"))
                .andExpect(jsonPath("$.images").doesNotExist());

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(textClient).generateResponse(prompt.capture());
        assertTrue(prompt.getValue().contains("Atue como um mentor"));
        assertTrue(prompt.getValue().contains("Como faço meu personagem pular?"));
        assertFalse(prompt.getValue().contains("EM JSON DO ALUNO"));
        verifyNoInteractions(interpreter);
        verifyNoMoreInteractions(textClient);
    }

    @Test
    void preservesChatWithInterpretedProjectFile() throws Exception {
        MockMultipartFile project = new MockMultipartFile("file", "plataforma.sb3",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[] {1, 2, 3});
        when(interpreter.extractProjectJson(project)).thenReturn("{\"targets\":[\"player\"]}");
        when(textClient.generateResponse(anyString())).thenReturn("Qual bloco controla a velocidade vertical?");

        mvc.perform(multipart("/api/chat").file(promptPart()).file(project))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Qual bloco controla a velocidade vertical?"));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(textClient).generateResponse(prompt.capture());
        assertTrue(prompt.getValue().contains("Como faço meu personagem pular?"));
        assertTrue(prompt.getValue().contains("PROJETO plataforma.sb3 EM JSON DO ALUNO"));
        assertTrue(prompt.getValue().contains("{\"targets\":[\"player\"]}"));
        verify(interpreter).extractProjectJson(project);
        verifyNoMoreInteractions(textClient, interpreter);
    }

    @Test
    void emptyProjectFileStillUsesTextOnlyChat() throws Exception {
        when(textClient.generateResponse(anyString())).thenReturn("O que você já tentou?");
        MockMultipartFile emptyProject = new MockMultipartFile("file", "vazio.sb3",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[0]);

        mvc.perform(multipart("/api/chat").file(promptPart()).file(emptyProject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("O que você já tentou?"));

        verify(textClient).generateResponse(anyString());
        verifyNoInteractions(interpreter);
        verifyNoMoreInteractions(textClient);
    }

    @Test
    void missingPromptStillReturnsBadRequest() throws Exception {
        mvc.perform(multipart("/api/chat")).andExpect(status().isBadRequest());

        verifyNoInteractions(textClient, interpreter);
    }

    private static MockMultipartFile promptPart() {
        return new MockMultipartFile("prompt", "", MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8",
                "Como faço meu personagem pular?".getBytes(StandardCharsets.UTF_8));
    }
}
