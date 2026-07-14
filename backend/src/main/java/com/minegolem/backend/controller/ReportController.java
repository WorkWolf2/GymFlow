package com.minegolem.backend.controller;

import com.lowagie.text.DocumentException;
import com.minegolem.backend.domain.enums.ReportType;
import com.minegolem.backend.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/pdf")
    @PreAuthorize("hasAnyAuthority('USER_READ', 'SUBSCRIPTION_READ', 'ACCESS_READ')")
    public void download(
        @RequestParam ReportType type,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        HttpServletResponse response
    ) throws IOException, DocumentException {
        reportService.generate(type, from, to, response);
    }
}
