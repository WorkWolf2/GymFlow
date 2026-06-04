package com.minegolem.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FiscalCodeService {

    private final MunicipalityRegistry municipalityRegistry;

    private static final String VOWELS = "AEIOU";
    private static final char[] MONTH_CODES = {'A', 'B', 'C', 'D', 'E', 'H', 'L', 'M', 'P', 'R', 'S', 'T'};
    private static final Map<Character, Integer> ODD_VALUES = Map.ofEntries(
        Map.entry('0', 1), Map.entry('1', 0), Map.entry('2', 5), Map.entry('3', 7), Map.entry('4', 9),
        Map.entry('5', 13), Map.entry('6', 15), Map.entry('7', 17), Map.entry('8', 19), Map.entry('9', 21),
        Map.entry('A', 1), Map.entry('B', 0), Map.entry('C', 5), Map.entry('D', 7), Map.entry('E', 9),
        Map.entry('F', 13), Map.entry('G', 15), Map.entry('H', 17), Map.entry('I', 19), Map.entry('J', 21),
        Map.entry('K', 2), Map.entry('L', 4), Map.entry('M', 18), Map.entry('N', 20), Map.entry('O', 11),
        Map.entry('P', 3), Map.entry('Q', 6), Map.entry('R', 8), Map.entry('S', 12), Map.entry('T', 14),
        Map.entry('U', 16), Map.entry('V', 10), Map.entry('W', 22), Map.entry('X', 25), Map.entry('Y', 24),
        Map.entry('Z', 23)
    );

    public Optional<String> generate(
        String firstName,
        String lastName,
        LocalDate birthDate,
        String sex,
        String birthPlace,
        String birthProvince
    ) {
        Optional<String> birthPlaceCode = municipalityRegistry.resolveCadastralCode(birthPlace, birthProvince);
        String normalizedSex = normalizeSex(sex);

        if (isBlank(firstName) || isBlank(lastName) || birthDate == null
            || normalizedSex == null || birthPlaceCode.isEmpty()) {
            return Optional.empty();
        }

        String partial = surnameCode(lastName)
            + nameCode(firstName)
            + String.format("%02d", birthDate.getYear() % 100)
            + MONTH_CODES[birthDate.getMonthValue() - 1]
            + String.format("%02d", birthDate.getDayOfMonth() + ("F".equals(normalizedSex) ? 40 : 0))
            + birthPlaceCode.get();

        return Optional.of(partial + controlCharacter(partial));
    }

    public String normalizeFiscalCode(String fiscalCode) {
        return isBlank(fiscalCode) ? null : fiscalCode.trim().toUpperCase(Locale.ROOT);
    }

    public String normalizeSex(String sex) {
        if (isBlank(sex)) {
            return null;
        }
        return sex.trim().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String surnameCode(String value) {
        return codeFromLetters(value, false);
    }

    private String nameCode(String value) {
        return codeFromLetters(value, true);
    }

    private String codeFromLetters(String value, boolean firstName) {
        String letters = onlyLetters(value);
        String consonants = letters.chars()
            .filter(ch -> VOWELS.indexOf(ch) < 0)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
        String vowels = letters.chars()
            .filter(ch -> VOWELS.indexOf(ch) >= 0)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();

        String source = firstName && consonants.length() >= 4
            ? "" + consonants.charAt(0) + consonants.charAt(2) + consonants.charAt(3)
            : consonants + vowels + "XXX";

        return source.substring(0, 3);
    }

    private String onlyLetters(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
    }

    private char controlCharacter(String partial) {
        int total = 0;
        for (int i = 0; i < partial.length(); i++) {
            char ch = partial.charAt(i);
            total += i % 2 == 0 ? ODD_VALUES.get(ch) : evenValue(ch);
        }
        return (char) ('A' + (total % 26));
    }

    private int evenValue(char ch) {
        return Character.isDigit(ch) ? ch - '0' : ch - 'A';
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
