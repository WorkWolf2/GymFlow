package com.minegolem.backend.controller;

import com.minegolem.backend.dto.request.AccessBridgeRequest;
import com.minegolem.backend.dto.response.AccessBridgeResponse;
import com.minegolem.backend.service.AccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/access-bridge")
@RequiredArgsConstructor
public class AccessBridgeController {

    private final AccessService accessService;

    @PostMapping("/validate")
    public ResponseEntity<AccessBridgeResponse> validate(
        @RequestHeader(value = "X-Access-Bridge-Key", required = false) String apiKey,
        @Valid @RequestBody AccessBridgeRequest request
    ) {
        if (!accessService.isValidBridgeApiKey(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bridge key");
        }

        return ResponseEntity.ok(accessService.validateBridge(request));
    }
}
