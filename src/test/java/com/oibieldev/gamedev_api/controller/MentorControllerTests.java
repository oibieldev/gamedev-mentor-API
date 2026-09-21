package com.oibieldev.gamedev_api.controller;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.oibieldev.gamedev_api.client.TextGenerationClient;
import com.oibieldev.gamedev_api.exception.ImageGenerationExceptionHandler;
import com.oibieldev.gamedev_api.service.MentorService;
import com.oibieldev.gamedev_api.service.database.DailyUsageService;
import com.oibieldev.gamedev_api.service.generation.ImageGenerationService;
import com.oibieldev.gamedev_api.service.interpreters.ProjectInterpreterService;

import jakarta.servlet.ServletException;

class MentorControllerTests {

    private static final String STUDENT_ID = "student-test";

    private TextGenerationClient textClient;
    private ProjectInterpreterService interpreter;
    private ImageGenerationService imageService;
    private DailyUsageService dailyUsageService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        textClient = mock(TextGenerationClient.class);
        interpreter = mock(ProjectInterpreterService.class);
        imageService = mock(ImageGenerationService.class);
        dailyUsageService = mock(DailyUsageService.class);
        when(dailyUsageService.tryConsumeUsage(STUDENT_ID)).thenReturn(true);
        mvc = MockMvcBuilders.standaloneSetup(new MentorController(
                        new MentorService(textClient, interpreter), imageService, dailyUsageService))
                .setControllerAdvice(new ImageGenerationExceptionHandler())
                .build();
    }

    @AfterEach
    void doesNotCallImageService() {
        verifyNoInteractions(imageService);
    }

    @Test
    void preservesChatWithoutProjectFile() throws Exception {
        when(textClient.generateResponse(anyString())).thenReturn("Como seu personagem detecta o chão?");

        mvc.perform(multipart("/api/chat").header("X-Student-Id", STUDENT_ID).file(promptPart()))
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
        verify(dailyUsageService).tryConsumeUsage(STUDENT_ID);
        verifyNoMoreInteractions(dailyUsageService);
    }

    @Test
    void preservesChatWithInterpretedProjectFile() throws Exception {
        MockMultipartFile project = new MockMultipartFile("file", "plataforma.sb3",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[] {1, 2, 3});
        when(interpreter.extractProjectJson(project)).thenReturn("{\"targets\":[\"player\"]}");
        when(textClient.generateResponse(anyString())).thenReturn("Qual bloco controla a velocidade vertical?");

        mvc.perform(multipart("/api/chat").header("X-Student-Id", STUDENT_ID).file(promptPart()).file(project))
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

        mvc.perform(multipart("/api/chat").header("X-Student-Id", STUDENT_ID).file(promptPart()).file(emptyProject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("O que você já tentou?"));

        verify(textClient).generateResponse(anyString());
        verifyNoInteractions(interpreter);
        verifyNoMoreInteractions(textClient);
    }

    @Test
    void missingPromptStillReturnsBadRequest() throws Exception {
        mvc.perform(multipart("/api/chat").header("X-Student-Id", STUDENT_ID))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(textClient, interpreter, dailyUsageService);
    }

    @Test
    void missingStudentIdReturnsBadRequestWithoutConsumingQuota() throws Exception {
        mvc.perform(multipart("/api/chat").file(promptPart()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(textClient, interpreter, dailyUsageService);
    }

    @Test
    void exhaustedQuotaRejectsChatWithoutCallingProvider() throws Exception {
        when(dailyUsageService.tryConsumeUsage(STUDENT_ID)).thenReturn(false);

        mvc.perform(multipart("/api/chat").header("X-Student-Id", STUDENT_ID).file(promptPart()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.images").doesNotExist());

        verify(dailyUsageService).tryConsumeUsage(STUDENT_ID);
        verifyNoMoreInteractions(dailyUsageService);
        verifyNoInteractions(textClient, interpreter);
    }

    @Test
    void doesNotConvertInvalidChatProjectIntoImageRequestError() {
        MockMultipartFile project = new MockMultipartFile("file", "projeto.txt",
                MediaType.TEXT_PLAIN_VALUE, new byte[] {1});
        IllegalArgumentException failure = new IllegalArgumentException("Formato de projeto não suportado.");
        when(interpreter.extractProjectJson(project)).thenThrow(failure);

        ServletException exception = assertThrows(ServletException.class,
                () -> mvc.perform(multipart("/api/chat").header("X-Student-Id", STUDENT_ID)
                        .file(promptPart()).file(project)));

        assertSame(failure, exception.getCause());
        verify(interpreter).extractProjectJson(project);
        verifyNoMoreInteractions(interpreter);
        verifyNoInteractions(textClient);
    }

    @Test
    void preservesDefaultHandlingForUnreadableChatMessage() throws Exception {
        HttpMessageNotReadableException failure = new HttpMessageNotReadableException(
                "Mensagem de chat inválida.", new MockHttpInputMessage(new byte[0]));
        when(textClient.generateResponse(anyString())).thenThrow(failure);

        mvc.perform(multipart("/api/chat").header("X-Student-Id", STUDENT_ID).file(promptPart()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("INVALID_IMAGE_REQUEST"))))
                .andExpect(result -> assertSame(failure, result.getResolvedException()));

        verify(textClient).generateResponse(anyString());
        verifyNoMoreInteractions(textClient);
        verifyNoInteractions(interpreter);
    }

    private static MockMultipartFile promptPart() {
        return new MockMultipartFile("prompt", "", MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8",
                "Como faço meu personagem pular?".getBytes(StandardCharsets.UTF_8));
    }
}
