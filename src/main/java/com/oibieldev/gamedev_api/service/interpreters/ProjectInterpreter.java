package com.oibieldev.gamedev_api.service.interpreters;

import org.springframework.web.multipart.MultipartFile;

public interface ProjectInterpreter {
    boolean supports(String _fileExtension);
    String interpret(MultipartFile _file);
}
