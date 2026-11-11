package com.minegolem.backend.nfc;

import com.minegolem.backend.domain.entity.*;
import com.minegolem.backend.domain.enums.DenialReason;
import com.minegolem.backend.domain.enums.SubscriptionTypeEnum;
import com.minegolem.backend.repository.MedicalCertificateRepository;
import com.minegolem.backend.repository.NfcTagRepository;
import com.minegolem.backend.repository.SubscriptionRepository;
import com.minegolem.backend.repository.UserRepository;
import com.minegolem.backend.service.RealtimeEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NfcAccessValidatorPackageTest {

    @Mock
    private NfcTagRepository nfcTagRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private MedicalCertificateRepository medicalCertificateRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RealtimeEventService realtimeEventService;

    @InjectMocks
    private NfcAccessValidator validator;

    private Gym gym;
    private User user;
    private NfcTag nfcTag;
    private SubscriptionType packageType;
    private SubscriptionType insuranceType;
    private SubscriptionType subType;
    private MedicalCertificate validCert;

    @BeforeEach
    void setUp() {
        gym = Gym.builder().id(UUID.randomUUID()).name("GymFlow Gym").build();
        user = User.builder()
            .id(UUID.randomUUID())
            .gym(gym)
            .firstName("Mario")
            .lastName("Rossi")
            .active(true)
            .clientCode(1001L)
            .build();

        nfcTag = NfcTag.builder()
            .tagUid("A1B2C3D4")
            .user(user)
            .gym(gym)
            .active(true)
            .build();

        packageType = SubscriptionType.builder()
            .id(UUID.randomUUID())
            .name("Pacchetto 10 Ingressi")
            .type(SubscriptionTypeEnum.PACCHETTO)
            .totalEntries(10)
            .build();

        insuranceType = SubscriptionType.builder()
            .id(UUID.randomUUID())
            .name("Assicurazione Annuale")
            .type(SubscriptionTypeEnum.ASSICURAZIONE)
            .build();

        subType = SubscriptionType.builder()
            .id(UUID.randomUUID())
            .name("Abbonamento Mensile")
            .type(SubscriptionTypeEnum.ABBONAMENTO)
            .build();

        validCert = MedicalCertificate.builder()
            .id(UUID.randomUUID())
            .user(user)
            .issuedDate(LocalDate.now().minusDays(10))
            .expiryDate(LocalDate.now().plusMonths(6))
            .build();
    }

    @Test
    @DisplayName("Should grant access and decrement package entries when client has valid package")
    void testPackageAccessGrantedAndDecremented() {
        Subscription packageSub = Subscription.builder()
            .id(UUID.randomUUID())
            .user(user)
            .subscriptionType(packageType)
            .startDate(LocalDate.now().minusDays(5))
            .endDate(LocalDate.now().plusMonths(2))
            .totalEntries(10)
            .remainingEntries(10)
            .build();

        Subscription insuranceSub = Subscription.builder()
            .id(UUID.randomUUID())
            .user(user)
            .subscriptionType(insuranceType)
            .startDate(LocalDate.now().minusDays(10))
            .endDate(LocalDate.now().plusMonths(10))
            .build();

        when(nfcTagRepository.findByTagUidAndActiveTrue("A1B2C3D4")).thenReturn(Optional.of(nfcTag));
        // No ABBONAMENTO
        when(subscriptionRepository.findActiveByUserAndType(eq(user.getId()), eq(SubscriptionTypeEnum.ABBONAMENTO), any()))
            .thenReturn(Collections.emptyList());
        // Has PACCHETTO
        when(subscriptionRepository.findActiveByUserAndType(eq(user.getId()), eq(SubscriptionTypeEnum.PACCHETTO), any()))
            .thenReturn(List.of(packageSub));
        // Has ASSICURAZIONE
        when(subscriptionRepository.findActiveByUserAndType(eq(user.getId()), eq(SubscriptionTypeEnum.ASSICURAZIONE), any()))
            .thenReturn(List.of(insuranceSub));
        // Has valid cert
        when(medicalCertificateRepository.findFirstByUser_IdAndDeletedAtIsNullOrderByExpiryDateDesc(user.getId()))
            .thenReturn(Optional.of(validCert));

        NfcAccessValidator.ValidationResult result = validator.validate("A1B2C3D4");

        assertThat(result.granted()).isTrue();
        assertThat(result.remainingEntries()).isEqualTo(9);
        assertThat(packageSub.getRemainingEntries()).isEqualTo(9);
        verify(subscriptionRepository).save(packageSub);
        verify(realtimeEventService).publish(gym.getId(), "SUBSCRIPTION", "UPDATED", packageSub.getId());
    }

    @Test
    @DisplayName("Should deny access when package entries reach 0")
    void testPackageAccessDeniedWhenZeroEntries() {
        Subscription exhaustedSub = Subscription.builder()
            .id(UUID.randomUUID())
            .user(user)
            .subscriptionType(packageType)
            .startDate(LocalDate.now().minusDays(5))
            .endDate(LocalDate.now().plusMonths(2))
            .totalEntries(10)
            .remainingEntries(0)
            .build();

        when(nfcTagRepository.findByTagUidAndActiveTrue("A1B2C3D4")).thenReturn(Optional.of(nfcTag));
        // No ABBONAMENTO
        when(subscriptionRepository.findActiveByUserAndType(eq(user.getId()), eq(SubscriptionTypeEnum.ABBONAMENTO), any()))
            .thenReturn(Collections.emptyList());
        // Exhausted PACCHETTO
        when(subscriptionRepository.findActiveByUserAndType(eq(user.getId()), eq(SubscriptionTypeEnum.PACCHETTO), any()))
            .thenReturn(List.of(exhaustedSub));

        NfcAccessValidator.ValidationResult result = validator.validate("A1B2C3D4");

        assertThat(result.granted()).isFalse();
        assertThat(result.denialReason()).isEqualTo(DenialReason.PACKAGE_ENTRIES_EXHAUSTED);
        assertThat(exhaustedSub.getRemainingEntries()).isEqualTo(0);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should NOT decrement entries if access is denied due to expired medical certificate")
    void testPackageNotDecrementedWhenCertExpired() {
        Subscription packageSub = Subscription.builder()
            .id(UUID.randomUUID())
            .user(user)
            .subscriptionType(packageType)
            .startDate(LocalDate.now().minusDays(5))
            .endDate(LocalDate.now().plusMonths(2))
            .totalEntries(10)
            .remainingEntries(5)
            .build();

        Subscription insuranceSub = Subscription.builder()
            .id(UUID.randomUUID())
            .user(user)
            .subscriptionType(insuranceType)
            .startDate(LocalDate.now().minusDays(10))
            .endDate(LocalDate.now().plusMonths(10))
            .build();

        MedicalCertificate expiredCert = MedicalCertificate.builder()
            .id(UUID.randomUUID())
            .user(user)
            .issuedDate(LocalDate.now().minusYears(2))
            .expiryDate(LocalDate.now().minusDays(1)) // Expired
            .build();

        when(nfcTagRepository.findByTagUidAndActiveTrue("A1B2C3D4")).thenReturn(Optional.of(nfcTag));
        when(subscriptionRepository.findActiveByUserAndType(eq(user.getId()), eq(SubscriptionTypeEnum.ABBONAMENTO), any()))
            .thenReturn(Collections.emptyList());
        when(subscriptionRepository.findActiveByUserAndType(eq(user.getId()), eq(SubscriptionTypeEnum.PACCHETTO), any()))
            .thenReturn(List.of(packageSub));
        when(subscriptionRepository.findActiveByUserAndType(eq(user.getId()), eq(SubscriptionTypeEnum.ASSICURAZIONE), any()))
            .thenReturn(List.of(insuranceSub));
        when(medicalCertificateRepository.findFirstByUser_IdAndDeletedAtIsNullOrderByExpiryDateDesc(user.getId()))
            .thenReturn(Optional.of(expiredCert));

        NfcAccessValidator.ValidationResult result = validator.validate("A1B2C3D4");

        assertThat(result.granted()).isFalse();
        assertThat(result.denialReason()).isEqualTo(DenialReason.CERT_EXPIRED);
        // Crucial: entries must NOT be decremented!
        assertThat(packageSub.getRemainingEntries()).isEqualTo(5);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should NOT decrement entries if client has an active unlimited ABBONAMENTO")
    void testNoDecrementWhenActiveAbbonamentoExists() {
        Subscription activeAbbonamento = Subscription.builder()
            .id(UUID.randomUUID())
            .user(user)
            .subscriptionType(subType)
            .startDate(LocalDate.now().minusDays(5))
            .endDate(LocalDate.now().plusMonths(1))
            .build();

        Subscription insuranceSub = Subscription.builder()
            .id(UUID.randomUUID())
            .user(user)
            .subscriptionType(insuranceType)
            .startDate(LocalDate.now().minusDays(10))
            .endDate(LocalDate.now().plusMonths(10))
            .build();

        when(nfcTagRepository.findByTagUidAndActiveTrue("A1B2C3D4")).thenReturn(Optional.of(nfcTag));
        when(subscriptionRepository.findActiveByUserAndType(eq(user.getId()), eq(SubscriptionTypeEnum.ABBONAMENTO), any()))
            .thenReturn(List.of(activeAbbonamento));
        when(subscriptionRepository.findActiveByUserAndType(eq(user.getId()), eq(SubscriptionTypeEnum.ASSICURAZIONE), any()))
            .thenReturn(List.of(insuranceSub));
        when(medicalCertificateRepository.findFirstByUser_IdAndDeletedAtIsNullOrderByExpiryDateDesc(user.getId()))
            .thenReturn(Optional.of(validCert));

        NfcAccessValidator.ValidationResult result = validator.validate("A1B2C3D4");

        assertThat(result.granted()).isTrue();
        assertThat(result.packageUsed()).isNull();
        verify(subscriptionRepository, never()).save(any());
    }
}
