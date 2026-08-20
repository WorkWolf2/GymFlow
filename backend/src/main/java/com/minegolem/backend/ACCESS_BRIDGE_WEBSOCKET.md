# Bridge accessi via WebSocket

## Architettura

Il PC nella palestra apre una connessione WebSocket **in uscita** verso la VPS (`wss://.../ws/access-bridge`). La VPS non deve raggiungere direttamente la rete locale e non richiede porte inoltrate sul router.

```text
Lettore NFC -> bridge locale -> WebSocket sicuro -> VPS -> verifica accesso
                                            <- comando apertura <- pulsante app
bridge locale -> ER750 / centralina -> porta
```

## Cosa configurare sulla VPS

Nel file di configurazione della VPS imposta almeno:

```properties
access.bridge.api-key=una-chiave-lunga-casuale-e-segreta
access.bridge.relay-seconds=3
nfc.tcp.enabled=false
```

`nfc.tcp.enabled` è opzionale: il gateway TCP interno ora è disattivato di default. Impostarlo a `false` esplicitamente evita che venga usato per errore.

Il reverse proxy (Nginx, Caddy o equivalente) deve inoltrare gli upgrade WebSocket sulla stessa applicazione backend. Per Nginx:

```nginx
location /ws/access-bridge {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 90s;
}
```

Il certificato HTTPS deve essere valido: il bridge usa `wss://` quando `VPS_URL` inizia con `https://`.

## Cosa configurare sul PC locale

Apri `access_bridge_local_server.py` e sostituisci:

- `VPS_URL`: URL pubblico della VPS, senza slash finale.
- `BRIDGE_KEY`: esattamente la chiave `access.bridge.api-key` della VPS.
- `GYM_ID`: UUID della palestra a cui appartiene questo lettore.
- `DOOR_READER_HOST` e `DOOR_READER_PORT`: IP e porta della centralina ER750.
- se necessario, porta e host del lettore NFC (`EVENT_SERVER_*`).

Installa una sola dipendenza Python sul PC:

```powershell
py -m pip install websocket-client
```

Poi avvia il bridge:

```powershell
py .\access_bridge_local_server.py
```

L'endpoint locale `http://127.0.0.1:8787/health` indica anche `vpsConnected: true` quando il bridge è registrato. Il bridge tenta automaticamente la riconnessione ogni cinque secondi.

## Protocollo WebSocket

Tutti i messaggi sono JSON UTF-8. Il bridge deve inviare `REGISTER` appena si connette:

```json
{"type":"REGISTER","apiKey":"...","gymId":"UUID","deviceId":"pc-ingresso"}
```

La VPS risponde `REGISTERED`. Solo dopo questa risposta il bridge invia una scansione:

```json
{"type":"SCAN","requestId":"UUID","tagUid":"A1B2C3D4","deviceId":"pc-ingresso","deviceIp":"192.168.1.20"}
```

La risposta è `SCAN_RESULT`, con lo stesso `requestId` e con il precedente oggetto risultato dell'accesso (`granted`, `command`, `relaySeconds`, ecc.). Il bridge apre l'ER750 solo se `granted` è `true` e `command` è `OPEN`.

Quando un operatore preme **Apri porta**, la VPS invia:

```json
{"type":"OPEN_DOOR","commandId":"UUID","relaySeconds":3,"source":"manual-open-door"}
```

Il bridge aziona la porta e risponde:

```json
{"type":"COMMAND_RESULT","commandId":"UUID","success":true}
```

## Sicurezza e operatività

- Non pubblicare le porte locali `2169`, `2167` o `8787` su Internet.
- Mantieni segreta `BRIDGE_KEY` e rigenerala se il PC viene compromesso.
- Usa esclusivamente HTTPS/WSS in produzione.
- Ogni palestra può avere un bridge connesso; una nuova connessione con lo stesso `GYM_ID` sostituisce la precedente.
- Il pulsante della dashboard restituisce `sent: false` quando non esiste un bridge online per la palestra dell'operatore.
