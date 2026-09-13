package com.minegolem.backend.dto.response;

import com.minegolem.backend.domain.entity.Gym;
import java.util.UUID;

public record PublicGymInfoResponse(
    UUID id,
    String name,
    String address,
    String phone,
    String email
) {
    public static PublicGymInfoResponse from(Gym g) {
        if (g == null) return new PublicGymInfoResponse(null, "Palestra", null, null, null);
        return new PublicGymInfoResponse(
            g.getId(),
            g.getName(),
            g.getAddress(),
            g.getPhone(),
            g.getEmail()
        );
    }
}
