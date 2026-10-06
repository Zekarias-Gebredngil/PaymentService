package com.payment.nadi.application.port.in;

import com.payment.nadi.application.dto.request.DeviceTokenRequest;
import com.payment.nadi.application.dto.request.SendNotificationRequest;
import com.payment.nadi.application.dto.response.NotificationDto;
import com.payment.nadi.application.dto.response.PageResponse;

import java.util.UUID;

public interface NotificationUseCase {

    PageResponse<NotificationDto> getUserNotifications(String identifier, int page, int size);

    long getUnreadCount(String identifier);

    boolean markAsRead(UUID notificationId);

    int markAllAsRead(String identifier);

    boolean registerDevice(DeviceTokenRequest request);

    NotificationDto sendNotification(SendNotificationRequest request);
}
