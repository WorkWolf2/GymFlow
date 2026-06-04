package com.minegolem.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class MunicipalityRegistry {

    private static final Pattern PROVINCE_IN_PARENS = Pattern.compile("\\(([A-Z]{2})\\)");
    private static final Pattern PROVINCE_SUFFIX = Pattern.compile("(?:,|\\s)([A-Z]{2})\\s*$");
    private static final Pattern EMBEDDED_CADASTRAL = Pattern.compile(
        "(?:^|[^A-Z0-9])([A-Z][0-9A-Z]{3})(?:$|[^A-Z0-9])"
    );

    private final ObjectMapper objectMapper;
    private Map<String, List<Municipality>> byNormalizedName = Map.of();
    private List<Province> provinces = List.of();

    public MunicipalityRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        try {
            var resource = new ClassPathResource("data/comuni.json");
            List<ComuneRecord> records = objectMapper.readValue(
                resource.getInputStream(),
                new TypeReference<>() {}
            );
            Map<String, List<Municipality>> index = new HashMap<>();
            Map<String, String> provinceIndex = new TreeMap<>();
            for (ComuneRecord record : records) {
                if (record.nome() == null || record.codiceCatastale() == null) {
                    continue;
                }
                String key = normalizeName(record.nome());
                index.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new Municipality(record.nome(), record.codiceCatastale(), record.sigla()));
                if (record.sigla() != null && !record.sigla().isBlank()) {
                    String provinceName = record.provincia() != null && record.provincia().nome() != null
                        ? record.provincia().nome()
                        : record.sigla();
                    provinceIndex.putIfAbsent(record.sigla(), provinceName);
                }
            }
            byNormalizedName = Map.copyOf(index);
            provinces = provinceIndex.entrySet().stream()
                .map(e -> new Province(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(Province::name))
                .toList();
            log.info("Registro comuni caricato: {} nomi, {} province", byNormalizedName.size(), provinces.size());
        } catch (IOException e) {
            log.error("Impossibile caricare data/comuni.json", e);
            byNormalizedName = Map.of();
        }
    }

    public List<Province> listProvinces() {
        return provinces;
    }

    public Optional<Municipality> resolve(String birthPlace, String provinceSigla) {
        return resolveCadastralCode(birthPlace, provinceSigla).flatMap(this::findByCadastralCode);
    }

    public Optional<String> resolveCadastralCode(String birthPlace) {
        return resolveCadastralCode(birthPlace, null);
    }

    public Optional<String> resolveCadastralCode(String birthPlace, String provinceSigla) {
        if (isBlank(birthPlace)) {
            return Optional.empty();
        }

        Optional<String> explicit = extractExplicitCadastralCode(birthPlace);
        if (explicit.isPresent()) {
            return explicit;
        }

        ParsedPlace parsed = parsePlaceName(birthPlace);
        if (parsed.name().isBlank()) {
            return Optional.empty();
        }

        List<Municipality> matches = byNormalizedName.getOrDefault(parsed.name(), List.of());
        if (matches.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> province = normalizeProvinceSigla(provinceSigla);
        if (province.isEmpty()) {
            province = parsed.provinceSigla();
        }

        if (province.isPresent()) {
            String sigla = province.get();
            matches = matches.stream()
                .filter(m -> sigla.equals(m.provinceSigla()))
                .toList();
        }

        if (matches.size() == 1) {
            return Optional.of(matches.get(0).cadastralCode());
        }

        return Optional.empty();
    }

    public static Optional<String> normalizeProvinceSigla(String provinceSigla) {
        if (isBlank(provinceSigla)) {
            return Optional.empty();
        }
        String normalized = provinceSigla.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z]{2}") ? Optional.of(normalized) : Optional.empty();
    }

    public Optional<Municipality> findByCadastralCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return byNormalizedName.values().stream()
            .flatMap(List::stream)
            .filter(m -> normalized.equals(m.cadastralCode()))
            .findFirst();
    }

    private ParsedPlace parsePlaceName(String birthPlace) {
        String upper = birthPlace.trim().toUpperCase(Locale.ROOT);
        Optional<String> province = Optional.empty();

        Matcher parens = PROVINCE_IN_PARENS.matcher(upper);
        if (parens.find()) {
            province = Optional.of(parens.group(1));
        } else {
            Matcher suffix = PROVINCE_SUFFIX.matcher(upper);
            if (suffix.find()) {
                province = Optional.of(suffix.group(1));
            }
        }

        String withoutProvince = PROVINCE_IN_PARENS.matcher(upper).replaceAll(" ");
        withoutProvince = PROVINCE_SUFFIX.matcher(withoutProvince).replaceAll("");
        withoutProvince = withoutProvince.replaceAll("[^A-Z0-9\\s]", " ");
        withoutProvince = withoutProvince.replaceAll("\\s+", " ").trim();

        StringBuilder name = new StringBuilder();
        for (String token : withoutProvince.split("\\s+")) {
            if (token.isBlank() || isCadastralCode(token)) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(token);
        }

        return new ParsedPlace(normalizeName(name.toString()), province);
    }

    private Optional<String> extractExplicitCadastralCode(String birthPlace) {
        String normalized = birthPlace.trim().toUpperCase(Locale.ROOT);
        if (isCadastralCode(normalized)) {
            return Optional.of(normalized);
        }

        String cleaned = normalized.replaceAll("[^A-Z0-9]", " ");
        for (String token : cleaned.split("\\s+")) {
            if (isCadastralCode(token)) {
                return Optional.of(token);
            }
        }

        Matcher embedded = EMBEDDED_CADASTRAL.matcher(normalized);
        if (embedded.find() && isCadastralCode(embedded.group(1))) {
            return Optional.of(embedded.group(1));
        }

        return Optional.empty();
    }

    static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return normalized.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static boolean isCadastralCode(String token) {
        return token != null
            && token.matches("[A-Z][0-9A-Z]{3}")
            && token.chars().anyMatch(Character::isDigit);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ParsedPlace(String name, Optional<String> provinceSigla) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ComuneRecord(String nome, String codiceCatastale, String sigla, ProvinciaRecord provincia) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProvinciaRecord(String nome) {}

    public record Municipality(String name, String cadastralCode, String provinceSigla) {}

    public record Province(String sigla, String name) {}
}
