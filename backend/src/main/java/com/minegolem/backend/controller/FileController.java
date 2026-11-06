package com.minegolem.backend.controller;

import com.minegolem.backend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLConnection;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileStorageService fileStorageService;

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(@RequestParam String path) {
        try {
            InputStream is = fileStorageService.get(path);
            if (is == null) {
                return ResponseEntity.notFound().build();
            }

            String contentType = URLConnection.guessContentTypeFromName(path);
            if (contentType == null) {
                String lower = path.toLowerCase();
                if (lower.endsWith(".pdf")) {
                    contentType = "application/pdf";
                } else if (lower.endsWith(".png")) {
                    contentType = "image/png";
                } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else if (lower.endsWith(".webp")) {
                    contentType = "image/webp";
                } else {
                    contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
                }
            }

            String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            if (filename.startsWith("local:")) {
                filename = filename.substring(6);
                if (filename.contains("/")) {
                    filename = filename.substring(filename.lastIndexOf('/') + 1);
                }
            }

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(is));
        } catch (Exception e) {
            log.error("Errore durante il download del file: {}", path, e);
            return ResponseEntity.notFound().build();
        }
    }
}
