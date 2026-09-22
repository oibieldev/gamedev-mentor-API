package com.oibieldev.gamedev_api.service.database;

import org.springframework.stereotype.Service;

import com.oibieldev.gamedev_api.repository.StudentInteractionRepository;

@Service 
public class StudentInteractionService {
    private final StudentInteractionRepository studentInteractionRepository;

    public StudentInteractionService(StudentInteractionRepository _studentInteractionRepository){
        this.studentInteractionRepository = _studentInteractionRepository;
    }

    public boolean saveInteraction(
        String _studentId,
        String _interactionType,
        String _prompt,
        String _response) {

            return studentInteractionRepository.saveInteracion(
                _studentId,
                _interactionType,
                _prompt,
                _response
            );
        }
    
}
