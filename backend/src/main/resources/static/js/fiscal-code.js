/**
 * Calcolo automatico del codice fiscale italiano (16 caratteri).
 * Luogo di nascita + provincia (sigla, es. CS) oppure codice catastale nel testo.
 */
(function (global) {
    const VOWELS = 'AEIOU';
    const MONTH_CODES = ['A', 'B', 'C', 'D', 'E', 'H', 'L', 'M', 'P', 'R', 'S', 'T'];
    const ODD_VALUES = {
        0: 1, 1: 0, 2: 5, 3: 7, 4: 9, 5: 13, 6: 15, 7: 17, 8: 19, 9: 21,
        A: 1, B: 0, C: 5, D: 7, E: 9, F: 13, G: 15, H: 17, I: 19, J: 21,
        K: 2, L: 4, M: 18, N: 20, O: 11, P: 3, Q: 6, R: 8, S: 12, T: 14,
        U: 16, V: 10, W: 22, X: 25, Y: 24, Z: 23
    };

    const placeCache = {};

    function fiscalPart(value, isName) {
        const letters = (value || '').toUpperCase().replace(/[^A-Z]/g, '');
        const consonants = [...letters].filter(function (ch) { return VOWELS.indexOf(ch) < 0; }).join('');
        const vowelPart = [...letters].filter(function (ch) { return VOWELS.indexOf(ch) >= 0; }).join('');
        const source = isName && consonants.length >= 4
            ? consonants[0] + consonants[2] + consonants[3]
            : consonants + vowelPart + 'XXX';
        return source.substring(0, 3);
    }

    function isCadastralCode(token) {
        return /^[A-Z][0-9A-Z]{3}$/.test(token) && /\d/.test(token);
    }

    function extractLocalCadastralCode(value) {
        const text = (value || '').toUpperCase();
        const trimmed = text.trim();
        if (isCadastralCode(trimmed)) {
            return trimmed;
        }
        const cleaned = text.replace(/[^A-Z0-9]/g, ' ');
        const tokens = cleaned.split(/\s+/).filter(Boolean);
        for (let i = 0; i < tokens.length; i++) {
            if (isCadastralCode(tokens[i])) {
                return tokens[i];
            }
        }
        const embedded = text.match(/(?:^|[^A-Z0-9])([A-Z][0-9A-Z]{3})(?:$|[^A-Z0-9])/);
        return embedded && isCadastralCode(embedded[1]) ? embedded[1] : null;
    }

    function resolveBirthPlaceCode(value, province) {
        const $ = global.jQuery;
        const local = extractLocalCadastralCode(value);
        if (local) {
            return Promise.resolve(local);
        }
        if (!value || value.trim().length < 2) {
            return Promise.resolve(null);
        }
        const provinceParam = (province || '').trim().toUpperCase();
        const key = value.trim().toLowerCase() + '|' + provinceParam;
        if (Object.prototype.hasOwnProperty.call(placeCache, key)) {
            return Promise.resolve(placeCache[key]);
        }
        if (!$) {
            return Promise.resolve(null);
        }
        const params = { place: value.trim() };
        if (provinceParam) {
            params.province = provinceParam;
        }
        const request = $.getJSON('/api/municipalities/lookup', params)
            .then(function (data) {
                placeCache[key] = data.cadastralCode;
                return data.cadastralCode;
            })
            .catch(function () {
                placeCache[key] = null;
                return null;
            });

        const timeout = new Promise(function (resolve) {
            setTimeout(function () { resolve(null); }, 5000);
        });
        return Promise.race([request, timeout]);
    }

    function loadProvinceSelect(selector, selectedValue) {
        const $ = global.jQuery;
        if (!$) {
            return Promise.resolve();
        }
        const $select = $(selector);
        return $.getJSON('/api/municipalities/provinces')
            .then(function (provinces) {
                provinces.forEach(function (p) {
                    $select.append(
                        $('<option></option>').val(p.sigla).text(p.sigla + ' – ' + p.name)
                    );
                });
                if (selectedValue) {
                    $select.val(selectedValue);
                }
            });
    }

    function controlChar(partial) {
        let total = 0;
        for (let i = 0; i < partial.length; i++) {
            const ch = partial[i];
            total += i % 2 === 0
                ? ODD_VALUES[ch]
                : (/[0-9]/.test(ch) ? Number(ch) : ch.charCodeAt(0) - 65);
        }
        return String.fromCharCode(65 + (total % 26));
    }

    function normalizeSex(sex) {
        if (!sex || !String(sex).trim()) {
            return null;
        }
        const c = String(sex).trim().charAt(0).toUpperCase();
        return c === 'M' || c === 'F' ? c : null;
    }

    function computeWithPlaceCode(firstName, lastName, birthDate, sex, placeCode) {
        const normalizedSex = normalizeSex(sex);
        if (!firstName || !lastName || !birthDate || !normalizedSex || !placeCode) {
            return null;
        }
        const date = new Date(birthDate + 'T00:00:00');
        if (Number.isNaN(date.getTime())) {
            return null;
        }
        const day = String(date.getDate() + (normalizedSex === 'F' ? 40 : 0)).padStart(2, '0');
        const partial = fiscalPart(lastName, false)
            + fiscalPart(firstName, true)
            + String(date.getFullYear()).slice(-2)
            + MONTH_CODES[date.getMonth()]
            + day
            + placeCode;
        return partial + controlChar(partial);
    }

    function bindAutofill(fiscalSelector, fields) {
        const $ = global.jQuery;
        if (!$) {
            return function () { return Promise.resolve(); };
        }
        const $fiscal = $(fiscalSelector);
        const $first = $(fields.firstName);
        const $last = $(fields.lastName);
        const $birthDate = $(fields.birthDate);
        const $birthPlace = $(fields.birthPlace);
        const $birthProvince = fields.birthProvince ? $(fields.birthProvince) : $();
        const $sex = $(fields.sex);
        let debounceTimer;

        function provinceValue() {
            return $birthProvince.length ? $birthProvince.val() : '';
        }

        function run() {
            if ($fiscal.val().trim()) {
                return Promise.resolve();
            }
            return resolveBirthPlaceCode($birthPlace.val(), provinceValue())
                .then(function (placeCode) {
                    const code = computeWithPlaceCode(
                        $first.val(),
                        $last.val(),
                        $birthDate.val(),
                        $sex.val(),
                        placeCode
                    );
                    if (code) {
                        $fiscal.val(code);
                    }
                })
                .catch(function () { /* lookup fallito: il server può calcolare il CF */ });
        }

        function scheduleRun() {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(run, 350);
        }

        $first.add($last).add($birthDate).add($sex).on('input change', scheduleRun);
        $birthPlace.on('input change', scheduleRun);
        $birthProvince.on('change', scheduleRun);
        return run;
    }

    global.FiscalCode = {
        bindAutofill: bindAutofill,
        loadProvinceSelect: loadProvinceSelect,
        resolveBirthPlaceCode: resolveBirthPlaceCode
    };
})(window);
