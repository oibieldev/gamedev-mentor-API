package com.oibieldev.gamedev_api.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.oibieldev.gamedev_api.service.interpreters.construct.ConstructInterpreterService;
import com.oibieldev.gamedev_api.service.interpreters.scratch.ScratchInterpreterService;

@Configuration
public class InterpretersConfiguration {

    @Bean
    public ScratchInterpreterService scratchInterpreterService() {
        return new ScratchInterpreterService();
    }

    @Bean
    public ConstructInterpreterService constructInterpreterService() {
        return new ConstructInterpreterService();
    }
}
