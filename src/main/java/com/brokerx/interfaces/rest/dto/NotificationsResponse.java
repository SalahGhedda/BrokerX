package com.brokerx.interfaces.rest.dto;

import java.util.List;

public record NotificationsResponse(
        List<NotificationResponse> notifications
) {
}
