// main.js - Global jQuery functions and AJAX configurations

$(document).ready(function() {
    // 1. AJAX Setup: Attach JWT token to every request automatically
    $.ajaxSetup({
        beforeSend: function(xhr) {
            const token = localStorage.getItem('jwt_token');
            if (token) {
                xhr.setRequestHeader('Authorization', 'Bearer ' + token);
            }
        },
        error: function(jqXHR, textStatus, errorThrown) {
            if (jqXHR.status === 401 || jqXHR.status === 403) {
                // Token expired or invalid, redirect to login
                console.warn("Unauthorized access. Redirecting to login...");
                localStorage.removeItem('jwt_token');
                if (window.location.pathname !== '/login') {
                    window.location.href = '/login';
                }
            } else {
                console.error("AJAX Error:", textStatus, errorThrown);
                // Optionally show a global error toast here
            }
        }
    });

    // 2. Add an utility for formatting dates
    window.formatDate = function(dateString) {
        if (!dateString) return "-";
        const date = new Date(dateString);
        return date.toLocaleDateString('it-IT', { day: '2-digit', month: '2-digit', year: 'numeric' });
    };

    prepareNfcAudio();
    setupNfcRealtime();
});


$(document).ajaxSend(function(event, xhr) {
    const token = localStorage.getItem('jwt_token');

    if (token) {
        xhr.setRequestHeader('Authorization', 'Bearer ' + token);
    }
});

function setupNfcRealtime() {
    if (window.location.pathname === '/login' || !localStorage.getItem('jwt_token')) {
        return;
    }

    ensureNfcUi();

    if (typeof SockJS === 'undefined' || typeof StompJs === 'undefined') {
        console.warn('SockJS/STOMP non disponibili');
        return;
    }

    const client = new StompJs.Client({
        webSocketFactory: () => new SockJS('/ws'),
        reconnectDelay: 5000,
        debug: () => {}
    });

    client.onConnect = function() {
        client.subscribe('/topic/realtime', message => {
            const event = JSON.parse(message.body);
            handleRealtimeEvent(event);
        });

        client.subscribe('/topic/accesses', message => {
            const event = JSON.parse(message.body);
            showNfcToast(event);
            if (!event.userId || event.denialReason === 'NO_USER') {
                openNfcAssignModal(event.tagUid);
            }
        });

        client.subscribe('/topic/nfc/unknown-tag', message => {
            const event = JSON.parse(message.body);
            showNfcToast({
                tagUid: event.tagUid,
                deviceId: event.deviceId,
                granted: false,
                denialReason: 'TAG_UNKNOWN'
            });
            openNfcAssignModal(event.tagUid);
        });
    };

    client.activate();
    window.LegionAsdRealtimeClient = client;
}

function handleRealtimeEvent(event) {
    if (!event || !isCurrentGymEvent(event)) {
        return;
    }

    window.dispatchEvent(new CustomEvent('legionasd:realtime', { detail: event }));

    const refreshers = realtimeRefreshersFor(event);
    refreshers.forEach(fn => {
        try {
            if (typeof fn === 'function') fn(event);
        } catch (e) {
            console.debug('Refresh realtime non riuscito', e);
        }
    });
}

function realtimeRefreshersFor(event) {
    const type = event.type;
    const refreshers = new Set();

    if (type === 'ACCESS' || type === 'DASHBOARD') {
        refreshers.add(window.LegionAsdRefreshDashboard);
        refreshers.add(window.LegionAsdRefreshAccesses);
    }

    if (type === 'USER' || type === 'NFC' || type === 'SUBSCRIPTION') {
        refreshers.add(window.LegionAsdRefreshClients);
        refreshers.add(window.LegionAsdRefreshClientDetail);
        refreshers.add(window.LegionAsdRefreshDashboard);
        refreshers.add(window.LegionAsdRefreshPaymentClients);
    }

    if (type === 'PAYMENT') {
        refreshers.add(window.LegionAsdRefreshPayments);
        refreshers.add(window.LegionAsdRefreshClientDetail);
        refreshers.add(window.LegionAsdRefreshDashboard);
    }

    if (type === 'SUBSCRIPTION_TYPE') {
        refreshers.add(window.LegionAsdRefreshSubscriptionTypes);
        refreshers.add(window.LegionAsdRefreshClientDetail);
    }

    if (type === 'SETTINGS' || type === 'EMAIL_TEMPLATE' || type === 'EXPIRATION_TEMPLATE') {
        refreshers.add(window.LegionAsdRefreshSettings);
        refreshers.add(window.LegionAsdRefreshEmailTemplates);
        refreshers.add(window.LegionAsdRefreshExpirationTemplates);
    }

    return refreshers;
}

function isCurrentGymEvent(event) {
    const gymId = currentGymId();
    return !event.gymId || !gymId || event.gymId === gymId;
}

function currentGymId() {
    const token = localStorage.getItem('jwt_token');
    if (!token) return null;

    try {
        const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
        const payload = JSON.parse(decodeURIComponent(escape(atob(base64))));
        return payload.gymId || null;
    } catch (e) {
        return null;
    }
}

function ensureNfcUi() {
    if ($('#nfc-toast-container').length === 0) {
        $('body').append('<div id="nfc-toast-container" class="space-y-3" style="position:fixed;top:72px;left:50%;transform:translateX(-50%);z-index:10000;width:min(560px,calc(100vw - 32px));pointer-events:none"></div>');
    }

    if ($('#global-nfc-assign-modal').length === 0) {
        $('body').append(`
            <div id="global-nfc-assign-modal" class="modal-overlay hidden" style="z-index: 9998">
                <div class="card w-full max-w-md overflow-hidden animate-slide-up">
                    <div class="flex items-center justify-between px-6 py-4 border-b border-surface-border">
                        <h2 class="text-lg font-semibold text-foreground">Assegna Tag NFC</h2>
                        <button id="global-nfc-close" class="icon-button">
                            <i data-lucide="x" class="w-5 h-5"></i>
                        </button>
                    </div>
                    <form id="global-nfc-form" class="p-6 space-y-4">
                        <div>
                            <label class="block text-xs font-medium text-foreground-muted mb-1.5">Tag</label>
                            <input id="global-nfc-tag" readonly class="input font-mono" />
                        </div>
                        <div>
                            <label class="block text-xs font-medium text-foreground-muted mb-1.5">Cliente</label>
                            <select id="global-nfc-user" required class="input"></select>
                        </div>
                        <div class="pt-4 flex items-center justify-end gap-3">
                            <button type="button" id="global-nfc-cancel" class="btn-ghost">Annulla</button>
                            <button type="submit" id="global-nfc-submit" class="btn-primary">Assegna</button>
                        </div>
                    </form>
                </div>
            </div>
        `);

        $('#global-nfc-close, #global-nfc-cancel').on('click', function() {
            $('#global-nfc-assign-modal').addClass('hidden');
        });

        $('#global-nfc-form').on('submit', function(e) {
            e.preventDefault();
            const tagUid = $('#global-nfc-tag').val();
            const userId = $('#global-nfc-user').val();
            if (!tagUid || !userId) return;

            $('#global-nfc-submit').prop('disabled', true).text('Assegnazione...');
            $.ajax({
                url: '/api/nfc/assign',
                type: 'POST',
                data: { tagUid: tagUid, userId: userId },
                success: function(tag) {
                    $('#global-nfc-assign-modal').addClass('hidden');
                    showNfcToast({ tagUid: tag.tagUid, userName: tag.userFullName, clientCode: tag.clientCode, granted: true, denialReason: null });
                },
                complete: function() {
                    $('#global-nfc-submit').prop('disabled', false).text('Assegna');
                }
            });
        });
    }
}

function showNfcToast(event) {
    const reason = denialText(event.denialReason);
    const title = event.granted ? 'Accesso consentito' : 'Accesso negato';
    const colorClass = event.granted ? 'border-success' : 'border-danger';
    const statusClass = event.granted ? 'text-success' : 'text-danger';
    const icon = event.granted ? 'circle-check-big' : 'circle-x';
    const accent = event.granted ? 'rgba(34,197,94,.14)' : 'rgba(239,68,68,.14)';
    const tagValue = event.clientCode && String(event.tagUid) === String(event.clientCode) ? 'ID #' + event.clientCode : (event.tagUid ? 'NFC ' + event.tagUid : '-');
    const nameValue = event.clientCode && event.userName ? '#' + event.clientCode + ' - ' + event.userName : (event.userName || 'Tag non assegnato');
    const tag = escapeNfcText(tagValue);
    const name = escapeNfcText(nameValue);
    const reasonLabel = escapeNfcText(reason);

    const toast = $(`
        <div class="nfc-access-popup bg-surface border-l-4 ${colorClass} rounded-xl px-5 py-5"
             style="pointer-events:auto;opacity:0;transform:translateY(-18px) scale(.97);transition:opacity .18s cubic-bezier(0.2,0,0,1),transform .18s cubic-bezier(0.2,0,0,1);box-shadow:0 0 0 1px rgba(255,255,255,.1),0 18px 50px rgba(0,0,0,.35)">
            <div class="flex items-center gap-4">
                <div class="${statusClass} shrink-0 flex items-center justify-center rounded-full" style="width:56px;height:56px;background:${accent}">
                    <i data-lucide="${icon}" style="width:32px;height:32px"></i>
                </div>
                <div class="min-w-0 flex-1">
                    <p class="${statusClass} font-bold uppercase tracking-wide text-balance" style="font-size:20px;line-height:1.2">${title}</p>
                    <p class="text-foreground font-semibold truncate mt-1" style="font-size:16px">${name}</p>
                    <p class="text-foreground-muted font-mono mt-1" style="font-size:13px">${tag}${event.granted ? '' : ' - ' + reasonLabel}</p>
                </div>
                <button type="button" class="nfc-popup-close icon-button" aria-label="Chiudi avviso">
                    <i data-lucide="x" class="w-5 h-5"></i>
                </button>
            </div>
        </div>
    `);

    const container = $('#nfc-toast-container');
    container.prepend(toast);
    container.children().slice(3).remove();
    if (window.lucide) lucide.createIcons();

    toast.find('.nfc-popup-close').on('click', () => dismissNfcToast(toast));

    // Popup e suono partono nello stesso frame visivo.
    window.requestAnimationFrame(() => {
        toast.css({ opacity: 1, transform: 'translateY(0) scale(1)' });
        playNfcSound(event.granted);
    });

    const timeout = window.setTimeout(() => dismissNfcToast(toast), 6000);
    toast.data('dismiss-timeout', timeout);
}

function dismissNfcToast(toast) {
    if (!toast || !toast.length || !toast.closest('body').length) return;
    window.clearTimeout(toast.data('dismiss-timeout'));
    toast.css({ opacity: 0, transform: 'translateY(-12px) scale(.98)' });
    window.setTimeout(() => toast.remove(), 160);
}

function escapeNfcText(value) {
    return $('<div>').text(value == null ? '' : String(value)).html();
}

function openNfcAssignModal(tagUid) {
    if (!tagUid) return;
    $('#global-nfc-tag').val(tagUid);
    $('#global-nfc-user').html('<option value="">Caricamento...</option>');
    $('#global-nfc-assign-modal').removeClass('hidden');

    $.ajax({
        url: '/api/users?size=200',
        type: 'GET',
        success: function(data) {
            const users = data.content || [];
            const select = $('#global-nfc-user');
            select.empty();
            if (users.length === 0) {
                select.append('<option value="">Nessun cliente disponibile</option>');
                return;
            }
            users.forEach(user => {
                select.append(`<option value="${user.id}">#${user.clientCode || '-'} - ${user.fullName} ${user.email ? '- ' + user.email : ''}</option>`);
            });
        }
    });
}

function denialText(reason) {
    const labels = {
        TAG_UNKNOWN: 'Tag sconosciuto',
        NO_USER: 'Tag non assegnato',
        USER_INACTIVE: 'Utente inattivo',
        NO_ACTIVE_SUBSCRIPTION: 'Abbonamento assente',
        NO_ACTIVE_INSURANCE: 'Assicurazione assente',
        CERT_EXPIRED: 'Certificato scaduto',
        CERT_MISSING: 'Certificato assente'
    };
    return labels[reason] || reason || 'Negato';
}

function prepareNfcAudio() {
    const unlock = function() {
        const ctx = getNfcAudioContext();
        if (ctx && ctx.state === 'suspended') ctx.resume().catch(() => {});
    };
    document.addEventListener('pointerdown', unlock, { once: true, passive: true });
    document.addEventListener('keydown', unlock, { once: true });
}

function getNfcAudioContext() {
    const AudioContext = window.AudioContext || window.webkitAudioContext;
    if (!AudioContext) return null;
    if (!window.LegionAsdNfcAudioContext || window.LegionAsdNfcAudioContext.state === 'closed') {
        window.LegionAsdNfcAudioContext = new AudioContext();
    }
    return window.LegionAsdNfcAudioContext;
}

function playNfcSound(granted) {
    try {
        const ctx = getNfcAudioContext();
        if (!ctx) return;

        const schedule = function() {
            const start = ctx.currentTime + 0.01;
            const master = ctx.createGain();
            const compressor = ctx.createDynamicsCompressor();
            master.gain.setValueAtTime(granted ? 0.75 : 0.62, start);
            compressor.threshold.value = -12;
            compressor.knee.value = 16;
            compressor.ratio.value = 8;
            master.connect(compressor);
            compressor.connect(ctx.destination);

            if (granted) {
                // Due note chiare e ravvicinate: conferma positiva ben riconoscibile.
                [[880, 0], [1320, 0.16]].forEach(([frequency, offset]) => {
                    const osc = ctx.createOscillator();
                    const gain = ctx.createGain();
                    const noteStart = start + offset;
                    osc.type = 'sine';
                    osc.frequency.setValueAtTime(frequency, noteStart);
                    gain.gain.setValueAtTime(0.001, noteStart);
                    gain.gain.exponentialRampToValueAtTime(0.9, noteStart + 0.018);
                    gain.gain.exponentialRampToValueAtTime(0.001, noteStart + 0.34);
                    osc.connect(gain);
                    gain.connect(master);
                    osc.start(noteStart);
                    osc.stop(noteStart + 0.36);
                });
            } else {
                // Tre impulsi bassi: accesso negato più urgente e distinto.
                [0, 0.22, 0.44].forEach(offset => {
                    const osc = ctx.createOscillator();
                    const gain = ctx.createGain();
                    const pulseStart = start + offset;
                    osc.type = 'square';
                    osc.frequency.setValueAtTime(260, pulseStart);
                    osc.frequency.linearRampToValueAtTime(190, pulseStart + 0.16);
                    gain.gain.setValueAtTime(0.001, pulseStart);
                    gain.gain.linearRampToValueAtTime(0.72, pulseStart + 0.012);
                    gain.gain.exponentialRampToValueAtTime(0.001, pulseStart + 0.18);
                    osc.connect(gain);
                    gain.connect(master);
                    osc.start(pulseStart);
                    osc.stop(pulseStart + 0.19);
                });
            }
        };

        if (ctx.state === 'suspended') {
            ctx.resume().then(schedule).catch(() => {});
        } else {
            schedule();
        }
    } catch (e) {
        console.debug('Audio NFC non disponibile', e);
    }
}



