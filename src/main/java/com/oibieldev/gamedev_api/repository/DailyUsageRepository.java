package com.oibieldev.gamedev_api.repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DailyUsageRepository {
    private final JdbcTemplate jdbcTemplate;
    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");

    public DailyUsageRepository(JdbcTemplate _JdbcTemplate){
        this.jdbcTemplate = _JdbcTemplate;
    }

    public List<Integer> getUsage(String _studentId){
        
        LocalDate today = LocalDate.now(BRAZIL_ZONE);
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
                    today
            );
    }

    public boolean refundUsage(String _studentId){

        LocalDate today = LocalDate.now(BRAZIL_ZONE);
        int rows = jdbcTemplate.update(
            """
                UPDATE daily_usage
                SET used_count = used_count - 1
                WHERE student_external_id = ? 
                    AND usage_date = ? 
                    AND used_count > 0        

            """,
            _studentId,
            today
        );

        return rows > 0;
    }

    public boolean tryUsage(String _studentId) {
        return !this.getUsage(_studentId).isEmpty();
    }

}
