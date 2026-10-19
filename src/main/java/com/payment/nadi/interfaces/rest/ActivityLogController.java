package com.payment.nadi.interfaces.rest;

import com.payment.nadi.application.dto.response.ActivityLogDto;
import com.payment.nadi.application.dto.response.PageResponse;
import com.payment.nadi.application.service.ActivityLogService;
import com.payment.nadi.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/activities")
@Tag(name = "System Activity & Audit Logs", description = "Query real-time paginated activities, governance events, and system audit trail")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping
    @Operation(summary = "List All System Activities (Paginated)", description = "Returns a paginated list of all activities across registered accounts, transactions, and security alerts.")
    public ResponseEntity<ApiResponse<PageResponse<ActivityLogDto>>> listActivities(
            @Parameter(description = "Zero-indexed page number")
            @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Items per page (supports any size: 5, 10, 20, 50, etc.)")
            @RequestParam(required = false, defaultValue = "10") int size,
            @Parameter(description = "Category filter: ALL, GOVERNANCE, FINANCIAL, SECURITY, USER_MANAGEMENT, SYSTEM")
            @RequestParam(required = false, defaultValue = "ALL") String category,
            @Parameter(description = "Status filter: ALL, SUCCESS, WARNING, FAILED, INFO")
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @Parameter(description = "Optional search query matching actor, action, target entity, or details")
            @RequestParam(required = false) String query
    ) {
        PageResponse<ActivityLogDto> response = activityLogService.getActivities(page, size, category, status, query);
        return ResponseEntity.ok(ApiResponse.ok("Activities retrieved successfully", response));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get Activity Metrics Summary", description = "Returns summary counts across activity categories.")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getActivityStats() {
        Map<String, Long> stats = activityLogService.getActivityStats();
        return ResponseEntity.ok(ApiResponse.ok("Activity metrics retrieved", stats));
    }
}
