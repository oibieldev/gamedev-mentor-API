package com.oibieldev.gamedev_api.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DailyUsageRepository {
    private final JdbcTemplate jdbcTemplate;

    public DailyUsageRepository(JdbcTemplate _JdbcTemplate){
        this.jdbcTemplate = _JdbcTemplate;
    }

    public List<Integer> getUsage(String _studentId){
        
        return jdbcTemplate.query(
                    """
                    INSERT INTO daily_usage (
                        student_external_id,
                        usage_date,
                        used_count
                    )
                    VALUES (?, ?, 1)
    
                    ON CONFLICT (student_external_id, usage_date)
                    DO UPDATE SET
                        used_count = daily_usage.used_count + 1
                    WHERE daily_usage.used_count < 15
    
                    RETURNING used_count
                    """,
                    (_responsesSet, _rowNum) -> _responsesSet.getInt("used_count"),
                    _studentId,
                    LocalDate.now()
            );
    }

    public boolean tryUsage(String _studentId) {
        return !this.getUsage(_studentId).isEmpty();
    }

}
