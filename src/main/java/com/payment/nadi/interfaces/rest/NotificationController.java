package com.payment.nadi.interfaces.rest;

import com.payment.nadi.application.dto.request.DeviceTokenRequest;
import com.payment.nadi.application.dto.request.SendNotificationRequest;
import com.payment.nadi.application.dto.response.NotificationDto;
import com.payment.nadi.application.dto.response.PageResponse;
import com.payment.nadi.application.port.in.NotificationUseCase;
import com.payment.nadi.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "SMS & Push Notifications", description = "Query Notification History, Device Registration, and Alert Delivery")
public class NotificationController {

    private final NotificationUseCase notificationUseCase;

    public NotificationController(NotificationUseCase notificationUseCase) {
        this.notificationUseCase = notificationUseCase;
    }

    @GetMapping
    @Operation(summary = "List User Notifications", description = "Returns a paginated list of SMS and Push notification alerts for a user account.")
    public ResponseEntity<ApiResponse<PageResponse<NotificationDto>>> listNotifications(
            @RequestParam String identifier,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        PageResponse<NotificationDto> result = notificationUseCase.getUserNotifications(identifier, page, size);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get Unread Count", description = "Returns the count of unread notifications for the given user.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnreadCount(@RequestParam String identifier) {
        long count = notificationUseCase.getUnreadCount(identifier);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "identifier", identifier,
                "unreadCount", count
        )));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark Notification as Read", description = "Updates the read status of a notification alert.")
    public ResponseEntity<ApiResponse<String>> markAsRead(@PathVariable UUID id) {
        boolean marked = notificationUseCase.markAsRead(id);
        if (marked) {
            return ResponseEntity.ok(ApiResponse.ok("Notification marked as read", "READ"));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("NOT_FOUND", "Notification not found"));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark All Notifications as Read", description = "Marks all unread notifications for the specified user as read.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAllAsRead(@RequestParam String identifier) {
        int count = notificationUseCase.markAllAsRead(identifier);
        return ResponseEntity.ok(ApiResponse.ok("All notifications marked as read", Map.of(
                "identifier", identifier,
                "markedCount", count
        )));
    }

    @PostMapping("/devices")
    @Operation(summary = "Register Device Push Token", description = "Registers or updates mobile device FCM/APNs push token and notification preferences.")
    public ResponseEntity<ApiResponse<String>> registerDevice(@Valid @RequestBody DeviceTokenRequest request) {
        notificationUseCase.registerDevice(request);
        return ResponseEntity.ok(ApiResponse.ok("Device push token registered successfully", "REGISTERED"));
    }

    @PostMapping("/send")
    @Operation(summary = "Send Manual Alert (Admin)", description = "Dispatches an ad-hoc SMS or Push alert to a user.")
    public ResponseEntity<ApiResponse<NotificationDto>> sendAlert(@Valid @RequestBody SendNotificationRequest request) {
        NotificationDto result = notificationUseCase.sendNotification(request);
        return ResponseEntity.ok(ApiResponse.ok("Notification dispatched successfully", result));
    }
}
