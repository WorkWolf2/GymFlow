package com.minegolem.backend.controller;

import com.minegolem.backend.service.DatabaseBackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
@Slf4j
public class BackupController {

    private final DatabaseBackupService databaseBackupService;

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> createAndDownloadBackup() {
        try {
            Path backupPath = databaseBackupService.createBackup();
            Resource resource = new FileSystemResource(backupPath.toFile());

            if (!resource.exists()) {
                return ResponseEntity.internalServerError().build();
            }

            String filename = backupPath.getFileName().toString();

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (IOException | InterruptedException e) {
            log.error("Manual backup failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getBackupStatus() {
        return ResponseEntity.ok(databaseBackupService.getBackupStatus());
    }
}
