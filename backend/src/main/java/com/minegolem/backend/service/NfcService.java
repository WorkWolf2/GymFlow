package com.minegolem.backend.service;


import com.minegolem.backend.domain.entity.Gym;
import com.minegolem.backend.domain.entity.NfcTag;
import com.minegolem.backend.domain.entity.User;
import com.minegolem.backend.exception.BusinessException;
import com.minegolem.backend.exception.ResourceNotFoundException;
import com.minegolem.backend.repository.GymRepository;
import com.minegolem.backend.repository.NfcTagRepository;
import com.minegolem.backend.repository.UserRepository;
import com.minegolem.backend.security.StaffUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NfcService {

    private final NfcTagRepository nfcTagRepository;
    private final UserRepository userRepository;
    private final GymRepository gymRepository;
    private final AuditService auditService;

    @Transactional
    public NfcTag assignTag(String tagUid, UUID userId) {
        UUID gymId = currentGymId();

        // deactivate existing tag on this user
        nfcTagRepository.findByUserIdAndActiveTrue(userId)
            .ifPresent(existing -> {
                existing.setActive(false);
                nfcTagRepository.save(existing);
            });

        User user = userRepository.findByIdAndGymIdAndDeletedAtIsNull(userId, gymId)
            .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        Gym gym = gymRepository.findById(gymId).orElseThrow();

        NfcTag tag = nfcTagRepository.findByTagUidAndActiveTrue(tagUid)
            .orElseGet(() -> NfcTag.builder()
                .gym(gym)
                .tagUid(tagUid.toUpperCase())
                .build());

        if (tag.getUser() != null && !tag.getUser().getId().equals(userId)) {
            throw new BusinessException("Tag already assigned to another user");
        }

        tag.setUser(user);
        tag.setAssignedAt(LocalDateTime.now());
        tag.setActive(true);
        tag.setGym(gym);

        NfcTag saved = nfcTagRepository.save(tag);
        auditService.log("NFC_TAG_ASSIGNED", "NfcTag", tagUid);
        return saved;
    }

    @Transactional
    public void unassignTag(String tagUid) {
        NfcTag tag = nfcTagRepository.findByTagUidAndActiveTrue(tagUid)
            .orElseThrow(() -> ResourceNotFoundException.of("NfcTag", tagUid));
        tag.setUser(null);
        tag.setAssignedAt(null);
        nfcTagRepository.save(tag);
        auditService.log("NFC_TAG_UNASSIGNED", "NfcTag", tagUid);
    }

    @Transactional
    public void deactivateTag(String tagUid) {
        NfcTag tag = nfcTagRepository.findByTagUidAndActiveTrue(tagUid)
            .orElseThrow(() -> ResourceNotFoundException.of("NfcTag", tagUid));
        tag.setActive(false);
        nfcTagRepository.save(tag);
        auditService.log("NFC_TAG_DEACTIVATED", "NfcTag", tagUid);
    }

    @Transactional(readOnly = true)
    public List<NfcTag> listUnassigned() {
        return nfcTagRepository.findByGymIdAndUserIsNullAndActiveTrue(currentGymId());
    }

    private UUID currentGymId() {
        StaffUserDetails d = (StaffUserDetails)
            SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return d.getGymId();
    }
}
