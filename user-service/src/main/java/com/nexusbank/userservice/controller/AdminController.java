package com.nexusbank.userservice.controller;

import com.nexusbank.userservice.dto.response.AdminStatsResponse;
import com.nexusbank.userservice.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminStatsResponse> getUserStats() {
        return ResponseEntity.ok(adminService.getUserStats());
    }
}
