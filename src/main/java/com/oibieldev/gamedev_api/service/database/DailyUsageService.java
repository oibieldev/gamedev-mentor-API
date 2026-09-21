package com.oibieldev.gamedev_api.service.database;

import org.springframework.stereotype.Service;

import com.oibieldev.gamedev_api.repository.DailyUsageRepository;

@Service
public class DailyUsageService {

    private final DailyUsageRepository usageRepository;

    public DailyUsageService(DailyUsageRepository _usageRepository){ this.usageRepository = _usageRepository; }

    public boolean tryConsumeUsage(String _studentId) {

        return usageRepository.tryUsage(_studentId);
    }
}