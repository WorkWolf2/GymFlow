package com.minegolem.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MunicipalityRegistryTest {

    private MunicipalityRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MunicipalityRegistry(new ObjectMapper());
        registry.load();
    }

    @Test
    void resolvesCityNameOnly() {
        assertThat(registry.resolveCadastralCode("Cosenza")).contains("D086");
        assertThat(registry.resolveCadastralCode("Rende")).contains("H235");
        assertThat(registry.resolveCadastralCode("rende")).contains("H235");
    }

    @Test
    void resolvesExplicitCadastralCode() {
        assertThat(registry.resolveCadastralCode("Roma (H501)")).contains("H501");
    }

    @Test
    void resolvesAmbiguousNameWithProvince() {
        assertThat(registry.resolveCadastralCode("Castro")).isEmpty();
        assertThat(registry.resolveCadastralCode("Castro (CS)")).isEmpty();
        assertThat(registry.resolveCadastralCode("Castro (LE)")).contains("M261");
        assertThat(registry.resolveCadastralCode("Castro (BG)")).contains("C337");
    }

    @Test
    void resolvesAmbiguousNameWithProvinceField() {
        assertThat(registry.resolveCadastralCode("Castro", "LE")).contains("M261");
        assertThat(registry.resolveCadastralCode("Castro", "BG")).contains("C337");
    }

    @Test
    void listsProvinces() {
        assertThat(registry.listProvinces())
            .isNotEmpty()
            .anyMatch(p -> "CS".equals(p.sigla()) && p.name().toLowerCase().contains("cosenza"));
    }
}
