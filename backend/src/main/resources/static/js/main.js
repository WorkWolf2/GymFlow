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
        $('body').append('<div id="nfc-toast-container" class="fixed bottom-4 right-4 z-[9999] space-y-2 w-80"></div>');
    }

    if ($('#global-nfc-assign-modal').length === 0) {
        $('body').append(`
            <div id="global-nfc-assign-modal" class="modal-overlay hidden" style="z-index: 9998">
                <div class="bg-surface border border-surface-border rounded shadow-card w-full max-w-md overflow-hidden animate-slide-up">
                    <div class="flex items-center justify-between px-6 py-4 border-b border-surface-border">
                        <h2 class="text-lg font-semibold text-foreground">Assegna Tag NFC</h2>
                        <button id="global-nfc-close" class="text-foreground-muted hover:text-foreground transition-colors">
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
    playNfcSound(event.granted);

    const reason = denialText(event.denialReason);
    const title = event.granted ? 'Accesso consentito' : 'Accesso negato';
    const colorClass = event.granted ? 'border-success/40' : 'border-danger/40';
    const tag = event.clientCode && String(event.tagUid) === String(event.clientCode) ? 'ID #' + event.clientCode : (event.tagUid ? 'NFC ' + event.tagUid : '-');
    const name = event.clientCode && event.userName ? '#' + event.clientCode + ' - ' + event.userName : (event.userName || 'Tag non assegnato');

    const toast = $(`
        <div class="bg-surface border ${colorClass} shadow-card rounded-lg px-4 py-3 animate-slide-up">
            <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                    <p class="text-sm font-semibold text-foreground">${title}</p>
                    <p class="text-xs text-foreground-muted truncate">${name}</p>
                    <p class="text-xs text-foreground-subtle font-mono mt-1">${tag}${event.granted ? '' : ' - ' + reason}</p>
                </div>
            </div>
        </div>
    `);

    $('#nfc-toast-container').append(toast);
    setTimeout(() => toast.fadeOut(250, () => toast.remove()), 4500);
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

function playNfcSound(granted) {
    try {
        const AudioContext = window.AudioContext || window.webkitAudioContext;
        const ctx = new AudioContext();
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        if (granted) {
            // Suono "tin" (campanello)
            osc.type = 'sine';
            osc.frequency.value = 1500;
            gain.gain.setValueAtTime(0.2, ctx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.8);
            
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.start();
            osc.stop(ctx.currentTime + 0.8);
        } else {
            // Suono "allarme"
            osc.type = 'square';
            osc.frequency.setValueAtTime(400, ctx.currentTime);
            osc.frequency.setValueAtTime(600, ctx.currentTime + 0.2);
            osc.frequency.setValueAtTime(400, ctx.currentTime + 0.4);
            osc.frequency.setValueAtTime(600, ctx.currentTime + 0.6);
            
            gain.gain.setValueAtTime(0.1, ctx.currentTime);
            gain.gain.linearRampToValueAtTime(0.001, ctx.currentTime + 0.8);
            
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.start();
            osc.stop(ctx.currentTime + 0.8);
        }
    } catch (e) {
        console.debug('Audio NFC non disponibile', e);
    }
}



