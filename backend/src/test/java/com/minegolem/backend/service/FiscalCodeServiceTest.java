package com.minegolem.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiscalCodeServiceTest {

    @Mock
    private MunicipalityRegistry municipalityRegistry;

    private FiscalCodeService service;

    @BeforeEach
    void setUp() {
        service = new FiscalCodeService(municipalityRegistry);
    }

    @Test
    void generatesFromCadastralCodeInBirthPlace() {
        when(municipalityRegistry.resolveCadastralCode("Roma (H501)", null))
            .thenReturn(Optional.of("H501"));

        var result = service.generate(
            "Mario",
            "Rossi",
            LocalDate.of(1990, 5, 15),
            "M",
            "Roma (H501)",
            null
        );

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(16);
        assertThat(result.get()).startsWith("RSSMRA");
    }

    @Test
    void generatesFromCityNameAndProvince() {
        when(municipalityRegistry.resolveCadastralCode(eq("Cosenza"), eq("CS")))
            .thenReturn(Optional.of("D086"));

        var result = service.generate(
            "Mario",
            "Rossi",
            LocalDate.of(1990, 5, 15),
            "M",
            "Cosenza",
            "CS"
        );

        assertThat(result).isPresent();
        assertThat(result.get()).contains("D086");
    }

    @Test
    void emptyWhenPlaceNotResolved() {
        when(municipalityRegistry.resolveCadastralCode(eq("CittaInesistente"), eq(null)))
            .thenReturn(Optional.empty());

        var result = service.generate(
            "Mario",
            "Rossi",
            LocalDate.of(1990, 5, 15),
            "M",
            "CittaInesistente",
            null
        );

        assertThat(result).isEmpty();
    }
}
