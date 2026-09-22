package com.oibieldev.gamedev_api.dto.persistance;

public record UsageResponse(
    int used,
    int limit,
    int remaining
) {

}
