package com.minegolem.backend.service;


import com.minegolem.backend.domain.entity.Gym;
import com.minegolem.backend.domain.entity.MedicalCertificate;
import com.minegolem.backend.domain.entity.NfcTag;
import com.minegolem.backend.domain.entity.User;
import com.minegolem.backend.domain.enums.SubscriptionTypeEnum;
import com.minegolem.backend.dto.request.UserRequest;
import com.minegolem.backend.dto.response.UserResponse;
import com.minegolem.backend.exception.BusinessException;
import com.minegolem.backend.exception.ResourceNotFoundException;
import com.minegolem.backend.repository.*;
import com.minegolem.backend.security.StaffUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final GymRepository gymRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MedicalCertificateRepository medicalCertificateRepository;
    private final NfcTagRepository nfcTagRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final FiscalCodeService fiscalCodeService;

    @Transactional(readOnly = true)
    public Page<UserResponse> list(String search, Pageable pageable) {
        UUID gymId = currentGymId();
        return userRepository.searchByGym(gymId, search, pageable)
            .map(this::toResponse);
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        UUID gymId = currentGymId();
        Gym gym = gymRepository.findById(gymId)
            .orElseThrow(() -> ResourceNotFoundException.of("Gym", gymId));

        User user = User.builder()
            .clientCode(userRepository.nextClientCode())
            .gym(gym)
            .firstName(request.firstName())
            .lastName(request.lastName())
            .email(request.email())
            .phone(request.phone())
            .birthDate(request.birthDate())
            .birthPlace(request.birthPlace())
            .birthProvince(normalizeBirthProvince(request.birthProvince()))
            .sex(fiscalCodeService.normalizeSex(request.sex()))
            .fiscalCode(resolveFiscalCode(request, null))
            .address(request.address())
            .notes(request.notes())
            .build();

        User saved = userRepository.save(user);
        auditService.log("USER_CREATED", "User", saved.getId().toString());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return toResponse(findUser(id));
    }

    @Transactional
    public UserResponse update(UUID id, UserRequest request) {
        User user = findUser(id);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setBirthDate(request.birthDate());
        user.setBirthPlace(request.birthPlace());
        user.setBirthProvince(normalizeBirthProvince(request.birthProvince()));
        user.setSex(fiscalCodeService.normalizeSex(request.sex()));
        user.setFiscalCode(resolveFiscalCode(request, user));
        user.setAddress(request.address());
        user.setNotes(request.notes());
        auditService.log("USER_UPDATED", "User", id.toString());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        User user = findUser(id);
        user.setDeletedAt(LocalDateTime.now());
        user.setActive(false);
        userRepository.save(user);
        auditService.log("USER_DELETED", "User", id.toString());
    }

    @Transactional
    public UserResponse uploadAvatar(UUID id, MultipartFile file) {
        User user = findUser(id);
        String path = fileStorageService.store(file, "avatars/" + id);
        user.setAvatarPath(path);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse saveSignature(UUID id, MultipartFile signaturePng) {
        User user = findUser(id);
        String path = fileStorageService.store(signaturePng, "signatures/" + id);
        user.setSignaturePath(path);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse uploadDocuments(UUID id, MultipartFile front, MultipartFile back) {
        User user = findUser(id);
        if (front != null && !front.isEmpty()) {
            String path = fileStorageService.store(front, "documents/" + id + "/front");
            user.setDocFrontPath(path);
        }
        if (back != null && !back.isEmpty()) {
            String path = fileStorageService.store(back, "documents/" + id + "/back");
            user.setDocBackPath(path);
        }
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse saveCertificate(UUID id, MultipartFile file,
                                        LocalDate issuedDate, LocalDate expiryDate, String notes) {
        User user = findUser(id);
        boolean hasFile = file != null && !file.isEmpty();

        if (!hasFile && expiryDate == null) {
            throw new BusinessException("Indicare la data di scadenza o caricare un file");
        }

        MedicalCertificate cert = medicalCertificateRepository
            .findFirstByUser_IdAndDeletedAtIsNullOrderByExpiryDateDesc(user.getId())
            .orElse(MedicalCertificate.builder().user(user).build());

        if (issuedDate != null) {
            cert.setIssuedDate(issuedDate);
        } else if (cert.getIssuedDate() == null) {
            cert.setIssuedDate(LocalDate.now());
        }

        if (expiryDate != null) {
            cert.setExpiryDate(expiryDate);
        } else if (cert.getExpiryDate() == null) {
            cert.setExpiryDate(LocalDate.now().plusYears(1));
        }

        if (cert.getExpiryDate().isBefore(cert.getIssuedDate())) {
            throw new BusinessException("La data di scadenza deve essere successiva alla data di emissione");
        }

        if (notes != null && !notes.isBlank()) {
            cert.setNotes(notes.trim());
        }

        if (hasFile) {
            cert.setFilePath(fileStorageService.store(file, "certificates/" + id));
        }

        medicalCertificateRepository.save(cert);
        auditService.log("CERTIFICATE_SAVED", "User", id.toString());
        return toResponse(user);
    }

    private User findUser(UUID id) {
        UUID gymId = currentGymId();
        return userRepository.findByIdAndGymIdAndDeletedAtIsNull(id, gymId)
            .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    public UserResponse toResponse(User u) {
        boolean hasActiveSub = !subscriptionRepository
            .findActiveByUserAndType(u.getId(), SubscriptionTypeEnum.ABBONAMENTO, LocalDate.now())
            .isEmpty();

        var certOpt = medicalCertificateRepository
            .findFirstByUser_IdAndDeletedAtIsNullOrderByExpiryDateDesc(u.getId());

        MedicalCertificate.Status certStatus = certOpt
            .map(MedicalCertificate::getStatus)
            .orElse(MedicalCertificate.Status.MISSING);

        boolean hasCertificate = certOpt.isPresent();

        String nfcUid = nfcTagRepository.findByUserIdAndActiveTrue(u.getId())
            .map(NfcTag::getTagUid)
            .orElse(null);

        return new UserResponse(
            u.getId(), u.getClientCode(), u.getFirstName(), u.getLastName(), u.getFullName(),
            u.getEmail(), u.getPhone(), u.getBirthDate(), u.getBirthPlace(), u.getBirthProvince(), u.getSex(), u.getFiscalCode(),
            u.getAddress(), u.getNotes(), fileUrl(u.getAvatarPath()), fileUrl(u.getDocFrontPath()), fileUrl(u.getDocBackPath()), u.isActive(),
            u.getCreatedAt(), hasActiveSub, certStatus, hasCertificate,
            certOpt.map(MedicalCertificate::getIssuedDate).orElse(null),
            certOpt.map(MedicalCertificate::getExpiryDate).orElse(null),
            nfcUid
        );
    }

    private String fileUrl(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        String url = fileStorageService.getPresignedUrl(objectName);
        return url != null ? url : objectName;
    }

    private String resolveFiscalCode(UserRequest request, User existing) {
        String provided = fiscalCodeService.normalizeFiscalCode(request.fiscalCode());
        if (provided != null) {
            return provided;
        }

        String birthProvince = normalizeBirthProvince(request.birthProvince());
        if (birthProvince == null && existing != null) {
            birthProvince = existing.getBirthProvince();
        }

        return fiscalCodeService.generate(
            request.firstName(),
            request.lastName(),
            request.birthDate(),
            request.sex(),
            request.birthPlace(),
            birthProvince
        ).orElse(existing != null ? existing.getFiscalCode() : null);
    }

    private String normalizeBirthProvince(String birthProvince) {
        return MunicipalityRegistry.normalizeProvinceSigla(birthProvince).orElse(null);
    }

    private UUID currentGymId() {
        StaffUserDetails details = (StaffUserDetails)
            SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return details.getGymId();
    }
}
