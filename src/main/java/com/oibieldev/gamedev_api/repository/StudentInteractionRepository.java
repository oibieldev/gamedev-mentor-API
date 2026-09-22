package com.oibieldev.gamedev_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository 
public class StudentInteractionRepository {
    private final JdbcTemplate jdbcTemplate;

    public StudentInteractionRepository(JdbcTemplate _jdbcTemplate){
        this.jdbcTemplate = _jdbcTemplate;
    }

    public boolean saveInteracion(
        String _studentId,
        String _interactionType,
        String _prompt,
        String _response) {

            int rows = jdbcTemplate.update(
                """
                INSERT INTO student_interaction (
                    student_external_id,
                    interaction_type,
                    prompt,
                    response
                )
                VALUES (?, ?, ?, ?)
                """,
                _studentId,
                _interactionType,
                _prompt,
                _response
            );

            return rows > 0;
        }
    
}
