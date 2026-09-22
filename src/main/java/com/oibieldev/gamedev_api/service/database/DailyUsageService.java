package com.oibieldev.gamedev_api.service.database;

import org.springframework.stereotype.Service;

import com.oibieldev.gamedev_api.dto.persistance.UsageResponse;
import com.oibieldev.gamedev_api.repository.DailyUsageRepository;

@Service
public class DailyUsageService {
    private static final int DAILY_LIMIT  = 15;

    private final DailyUsageRepository usageRepository;

    public DailyUsageService(DailyUsageRepository _usageRepository){ this.usageRepository = _usageRepository; }

    public boolean tryConsumeUsage(String _studentId) { return usageRepository.tryUsage(_studentId, DAILY_LIMIT); }
    public boolean refundUsage(String _studentId) { return usageRepository.refundUsage(_studentId); }
    public UsageResponse getUsage(String _studentId) {
        int used = usageRepository.getUsageCount(_studentId);
        int remaining = DAILY_LIMIT - used;
        return new UsageResponse(used, DAILY_LIMIT, remaining);
    }
}