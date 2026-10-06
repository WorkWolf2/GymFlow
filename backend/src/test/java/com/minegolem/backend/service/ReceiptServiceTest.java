package com.minegolem.backend.service;

import com.minegolem.backend.domain.entity.*;
import com.minegolem.backend.domain.enums.PaymentMethod;
import com.minegolem.backend.domain.enums.SubscriptionTypeEnum;
import com.minegolem.backend.repository.GymRepository;
import com.minegolem.backend.repository.ReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private GymRepository gymRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ReceiptPdfService receiptPdfService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ReceiptService receiptService;

    private Gym gym;
    private User user;
    private SubscriptionType subTypeAbbonamento;
    private SubscriptionType subTypeAssicurazione;

    @BeforeEach
    void setUp() {
        gym = Gym.builder()
            .id(UUID.randomUUID())
            .name("ASD Fitness Legion")
            .address("Via Garibaldi 10, 00100 Roma")
            .settings(Map.of(
                "asdName", "ASD Fitness Legion Club",
                "asdFiscalCode", "98765432101",
                "asdRasdNumber", "998877"
            ))
            .build();

        user = User.builder()
            .id(UUID.randomUUID())
            .clientCode(101L)
            .firstName("Mario")
            .lastName("Rossi")
            .birthDate(LocalDate.of(2010, 5, 20)) // Minor
            .fiscalCode("RSSMRA10E20H501Z")
            .parentName("Giuseppe Rossi")
            .parentFiscalCode("RSSGPP75A01H501Y")
            .gym(gym)
            .build();

        subTypeAbbonamento = SubscriptionType.builder()
            .id(UUID.randomUUID())
            .name("Sala Fitness Trimestrale")
            .type(SubscriptionTypeEnum.ABBONAMENTO)
            .basePrice(new BigDecimal("120.00"))
            .gym(gym)
            .build();

        subTypeAssicurazione = SubscriptionType.builder()
            .id(UUID.randomUUID())
            .name("Tesseramento e Quota Associativa Annuale")
            .type(SubscriptionTypeEnum.ASSICURAZIONE)
            .basePrice(new BigDecimal("30.00"))
            .gym(gym)
            .build();
    }

    @Test
    void testBuildCausaleAbbonamento() {
        Subscription subscription = Subscription.builder()
            .user(user)
            .subscriptionType(subTypeAbbonamento)
            .startDate(LocalDate.of(2026, 10, 1))
            .endDate(LocalDate.of(2026, 12, 31))
            .price(new BigDecimal("120.00"))
            .build();

        String causale = receiptService.buildCausale(subscription);
        assertTrue(causale.contains("Quota di partecipazione alle attività sportive istituzionali"));
        assertTrue(causale.contains("Sala Fitness Trimestrale"));
        assertTrue(causale.contains("01/10/2026"));
        assertTrue(causale.contains("31/12/2026"));
    }

    @Test
    void testBuildCausaleQuotaAssociativa() {
        Subscription subscription = Subscription.builder()
            .user(user)
            .subscriptionType(subTypeAssicurazione)
            .startDate(LocalDate.of(2026, 10, 1))
            .endDate(LocalDate.of(2027, 9, 30))
            .price(new BigDecimal("30.00"))
            .build();

        String causale = receiptService.buildCausale(subscription);
        assertTrue(causale.contains("Quota associativa annuale ASD"));
        assertTrue(causale.contains("2026/2027"));
    }

    @Test
    void testCreateReceiptForSubscriptionWithStampDuty() throws Exception {
        Subscription subscription = Subscription.builder()
            .id(UUID.randomUUID())
            .user(user)
            .subscriptionType(subTypeAbbonamento)
            .startDate(LocalDate.of(2026, 10, 1))
            .endDate(LocalDate.of(2026, 12, 31))
            .price(new BigDecimal("120.00"))
            .build();

        Payment payment = Payment.builder()
            .id(UUID.randomUUID())
            .gym(gym)
            .user(user)
            .subscription(subscription)
            .amount(new BigDecimal("120.00"))
            .method(PaymentMethod.CARD)
            .paymentDate(LocalDate.of(2026, 10, 1))
            .build();

        when(receiptRepository.findMaxReceiptNumberByGymIdAndYear(eq(gym.getId()), eq(2026)))
            .thenReturn(5);

        when(receiptRepository.save(any(Receipt.class))).thenAnswer(invocation -> {
            Receipt r = invocation.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });

        when(receiptPdfService.generateReceiptPdf(any(Receipt.class)))
            .thenReturn(new byte[]{1, 2, 3});

        when(fileStorageService.storeBytes(any(byte[].class), anyString(), anyString()))
            .thenReturn("receipts/gym/2026/ricevuta_6_2026.pdf");

        Receipt receipt = receiptService.createReceiptForSubscription(subscription, payment);

        assertNotNull(receipt);
        assertEquals(6, receipt.getReceiptNumber());
        assertEquals(2026, receipt.getReceiptYear());
        assertEquals("6/2026", receipt.getReceiptFormattedNumber());
        assertTrue(receipt.isStampDutyApplied()); // > 77.47 EUR
        assertEquals(new BigDecimal("2.00"), receipt.getStampDutyAmount());
        assertEquals("receipts/gym/2026/ricevuta_6_2026.pdf", receipt.getPdfStoragePath());
        verify(receiptPdfService).generateReceiptPdf(any(Receipt.class));
        verify(fileStorageService).storeBytes(any(byte[].class), contains("ricevuta_6_2026.pdf"), eq("application/pdf"));
    }

    @Test
    void testCreateReceiptUnderThresholdNoStampDuty() throws Exception {
        Subscription subscription = Subscription.builder()
            .id(UUID.randomUUID())
            .user(user)
            .subscriptionType(subTypeAssicurazione)
            .startDate(LocalDate.of(2026, 10, 1))
            .endDate(LocalDate.of(2027, 9, 30))
            .price(new BigDecimal("30.00"))
            .build();

        Payment payment = Payment.builder()
            .id(UUID.randomUUID())
            .gym(gym)
            .user(user)
            .subscription(subscription)
            .amount(new BigDecimal("30.00"))
            .method(PaymentMethod.CASH)
            .paymentDate(LocalDate.of(2026, 10, 1))
            .build();

        when(receiptRepository.findMaxReceiptNumberByGymIdAndYear(eq(gym.getId()), eq(2026)))
            .thenReturn(0);

        when(receiptRepository.save(any(Receipt.class))).thenAnswer(invocation -> {
            Receipt r = invocation.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });

        when(receiptPdfService.generateReceiptPdf(any(Receipt.class)))
            .thenReturn(new byte[]{1, 2, 3});

        when(fileStorageService.storeBytes(any(byte[].class), anyString(), anyString()))
            .thenReturn("receipts/gym/2026/ricevuta_1_2026.pdf");

        Receipt receipt = receiptService.createReceiptForSubscription(subscription, payment);

        assertNotNull(receipt);
        assertEquals(1, receipt.getReceiptNumber());
        assertEquals("1/2026", receipt.getReceiptFormattedNumber());
        assertFalse(receipt.isStampDutyApplied()); // <= 77.47 EUR
        assertEquals(BigDecimal.ZERO, receipt.getStampDutyAmount());
    }
}
