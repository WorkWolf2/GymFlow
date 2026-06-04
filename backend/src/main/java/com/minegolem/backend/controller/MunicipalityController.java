package com.minegolem.backend.controller;

import com.minegolem.backend.dto.response.MunicipalityLookupResponse;
import com.minegolem.backend.dto.response.ProvinceResponse;
import com.minegolem.backend.service.MunicipalityRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/municipalities")
@RequiredArgsConstructor
public class MunicipalityController {

    private final MunicipalityRegistry municipalityRegistry;

    @GetMapping("/provinces")
    @PreAuthorize("isAuthenticated()")
    public List<ProvinceResponse> provinces() {
        return municipalityRegistry.listProvinces().stream()
            .map(p -> new ProvinceResponse(p.sigla(), p.name()))
            .toList();
    }

    @GetMapping("/lookup")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MunicipalityLookupResponse> lookup(
        @RequestParam String place,
        @RequestParam(required = false) String province
    ) {
        return municipalityRegistry.resolve(place, province)
            .map(m -> new MunicipalityLookupResponse(m.cadastralCode(), m.name(), m.provinceSigla()))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
