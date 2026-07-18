# AlfaPizza

AlfaPizza è un sistema per la gestione dei turni settimanali dei rider di una pizzeria. Il progetto comprende un'app Android per amministratori e rider, un servizio REST in Node.js e un database MongoDB. Il server mantiene lo stato operativo, applica autorizzazioni e regole di calendario e genera le assegnazioni settimanali.

Questa edizione nasce come evoluzione di una versione precedente più semplice. La logica che decide accessi, visibilità dei calendari, vincoli e scambi non è più affidata al solo client: il backend è la fonte autoritativa e l'app Android presenta le operazioni consentite dal ruolo autenticato.

## Screenshot dell'applicazione

|  |  |
| :---: | :---: |
| ![Login](assets/Login.jpeg) | ![Home](assets/Calendario.jpeg) |

|  |  |
| :---: | :---: |
| ![Swaps Rider](assets/Settimana.jpeg) | ![Swaps Admin](assets/Cambi.jpeg) |
## Funzionalità

### Amministratore

- Creazione ed eliminazione dei rider.
- Generazione di un codice rider e di una password temporanea numerica di otto cifre. La password compare, dopo la creazione riuscita, in una finestra con pulsante di conferma e non è recuperabile successivamente dall'app.
- Configurazione separata della settimana corrente e della successiva: numero di rider richiesti per giorno, minimo e massimo di turni per rider e giorno di pubblicazione.
- Generazione o rigenerazione automatica dei due calendari.
- Modifica manuale delle assegnazioni, con aggiornamento server-side dei colori e delle anomalie al salvataggio.
- Visualizzazione delle anomalie: turni scoperti, rider sotto il minimo e configurazione con minimo maggiore del massimo.
- Approvazione o rifiuto degli scambi già accettati da un secondo rider.
- Eliminazione sicura di un rider: i suoi turni diventano turni scoperti, mentre vincoli, swap e sessioni associati vengono rimossi.

### Rider

- Consultazione del calendario corrente e, dal giorno di pubblicazione, del calendario successivo.
- Inserimento e rimozione dei vincoli per la settimana successiva entro il giorno di pubblicazione incluso.
- Consultazione in sola lettura dei vincoli della settimana corrente.
- Creazione, accettazione e annullamento delle richieste di cambio turno compatibili con il calendario.
- Aggiornamento di email, telefono e password personale.

### Funzioni comuni

- Interfaccia in italiano e inglese.
- Notifiche interne relative ad aggiornamenti dei calendari e richieste di swap.
- Rilevamento dell'assenza di connettività e visualizzazione di uno stato offline.
- Logout locale immediato anche se la richiesta di revoca al server non può essere completata.

## Architettura

| Livello | Tecnologie | Responsabilità |
| --- | --- | --- |
| App Android | Kotlin, Jetpack Compose, Navigation Compose, ViewModel, Volley | Interfaccia, navigazione per ruolo, stato delle schermate e chiamate HTTP |
| Backend | Node.js, Express, Mongoose, bcryptjs | API REST, autenticazione, autorizzazione, calendario, vincoli, swap e automazioni |
| Persistenza | MongoDB | Dati applicativi, sessioni revocabili e stato idempotente delle automazioni |
| Scheduler | Qualsiasi servizio capace di effettuare richieste HTTPS pianificate | Richiamo protetto della rotazione settimanale e del controllo di pubblicazione |

L'app usa una singola `MainActivity` e schermate Compose organizzate in model, view e ViewModel. L'URL del backend viene inserito in `BuildConfig` durante la compilazione. In produzione il servizio deve essere esposto tramite HTTPS; Express non configura direttamente i certificati TLS e può essere posto dietro il proxy del provider scelto.

Le principali collezioni MongoDB sono:

| Collezione | Contenuto |
| --- | --- |
| `users` | Admin, rider, password bcrypt e dati di profilo |
| `calendars` | Calendario corrente e calendario successivo, distinti da `isNext` |
| `weekStructure` | Parametri operativi della settimana corrente e successiva |
| `constraints` | Vincoli correnti e futuri dei rider |
| `swaps` | Stato delle richieste di cambio turno |
| `authSessions` | Hash dei token di sessione e relativa scadenza |
| `automationState` | Lock e settimana dell'ultima rotazione completata |

`authSessions` e `automationState` sono collezioni interne: non sono esposte dalle API generiche dell'applicazione.

### Autenticazione e sessioni

- `POST /api/login` verifica la password con bcrypt e restituisce un token di sessione casuale e opaco.
- Il token completo viene restituito nella risposta di login e non viene salvato in chiaro dal server, che conserva il relativo hash SHA-256, il codice utente e la scadenza.
- L'app memorizza token e dati minimi di sessione tramite `EncryptedSharedPreferences` e invia `Authorization: Bearer <token>` a ogni API protetta.
- Il server ricava utente e ruolo dalla sessione valida; non considera attendibili identità o ruoli dichiarati dal client.
- Il logout revoca la sessione corrente. Il cambio password, l'eliminazione dell'utente e il reset amministrativo revocano tutte le sessioni dell'account interessato.
- Una risposta `401` invalida la sessione locale e riporta l'app alla schermata di login.
- I rider non ricevono password, recapiti o ultimo accesso degli altri utenti. Le anomalie del calendario sono visibili soltanto all'amministratore.

## Regole operative e ruoli

### Calendario corrente, successivo e pubblicazione

Il sistema mantiene due calendari e due strutture settimanali:

- `isNext: false` identifica la settimana corrente;
- `isNext: true` identifica la settimana successiva.

L'amministratore può vedere e modificare entrambi i calendari. Il rider riceve sempre quello corrente, ma il server non gli restituisce quello successivo prima del giorno indicato da `publicationDay`. La visibilità è valutata a ogni richiesta usando `APP_TIME_ZONE`: il calendario successivo è visibile quando l'indice del giorno corrente è maggiore o uguale a `publicationDay`, con `0` per lunedì e `6` per domenica.

Lo stesso giorno è anche l'ultimo giorno utile, incluso, per modificare i vincoli futuri. Dopo tale giorno il server rifiuta le modifiche del rider. Il valore `lastDayConstraint` è mantenuto uguale nelle due strutture settimanali e viene riportato anche come `publicationDay` nei rispettivi calendari.

La pubblicazione non richiede un cambio di stato persistito: l'accesso viene filtrato dinamicamente dal server. L'endpoint `/api/cron/check-publication` esegue soltanto un controllo pianificato e scrive un messaggio nel log; non rende visibile un calendario che le regole temporali considerano ancora non pubblicato.

### Generazione automatica e vincoli

Per ogni giorno l'algoritmo crea il numero di slot indicato in `listShift`. Per ogni slot ricalcola i rider eleggibili e applica il seguente ordine:

1. rider ancora sotto `minRider`;
2. rider senza preferenza sul giorno rispetto a rider con preferenza;
3. rider con meno turni già assegnati;
4. codice rider, come criterio deterministico finale.

La distinzione tra regole rigide e obiettivi è la seguente:

| Regola | Comportamento nella generazione automatica |
| --- | --- |
| Vincolo assoluto, `priority: 1` | Regola rigida: il rider viene escluso completamente dai candidati per quel giorno |
| Preferenza, `priority: 2` | Criterio non rigido: il rider viene sfavorito, ma può essere assegnato per copertura o bilanciamento |
| Massimo settimanale, `maxRider` | Regola rigida: il generatore non supera il limite |
| Un solo turno per rider nello stesso giorno | Regola rigida durante la generazione |
| Minimo settimanale, `minRider` | Obiettivo: i rider sotto il minimo hanno priorità, ma il risultato non è garantito se gli slot o i candidati non bastano |
| Numero di slot giornalieri, `listShift` | Il calendario mantiene il numero richiesto; uno slot non assegnabile viene registrato come rider mancante con codice `-99` |

Se un rider rimane sotto il minimo viene prodotta un'anomalia; se nessun rider è eleggibile, lo slot scoperto viene marcato in rosso. Il colore giallo indica un'assegnazione su una preferenza, il rosso un vincolo assoluto o un turno scoperto, mentre l'assenza di colore indica che non è presente alcun vincolo per quel giorno.

La modifica manuale dell'amministratore è distinta dalla generazione: l'app evita di proporre due volte lo stesso rider nello stesso giorno, ma consente di assegnare un rider anche su un suo vincolo assoluto. In questo caso il server accetta l'override amministrativo e ricalcola il pallino in rosso. Al salvataggio manuale e alla successiva lettura il backend ricalcola i colori; al salvataggio aggiorna anche le anomalie, senza richiedere una nuova generazione.

Il campo `permanent` non aumenta la forza del vincolo. Durante la rotazione settimanale un vincolo permanente futuro diventa corrente e viene copiato anche nella nuova settimana successiva; il rider può comunque rimuovere la copia futura quando le comunicazioni sono aperte.

### Scambi di turno

Uno swap segue tre passaggi: richiesta del primo rider, accettazione di un secondo rider e approvazione o rifiuto dell'amministratore.

Il server valida lo stato del calendario in ogni fase:

- il richiedente deve lavorare nel giorno ceduto e non deve già lavorare nel giorno desiderato;
- il rider che accetta deve lavorare nel giorno desiderato ed essere libero nel giorno ceduto;
- richiedente e accettante devono essere utenti diversi;
- per la settimana corrente non sono ammessi giorni già trascorsi;
- per la settimana successiva non sono ammessi swap prima della pubblicazione;
- al momento dell'approvazione le assegnazioni vengono verificate di nuovo, così uno swap divenuto incompatibile non viene applicato.

Quando lo swap è approvato, il server scambia i due codici nel calendario e ricalcola i colori dei turni risultanti. La rigenerazione riuscita del calendario corrente elimina le richieste di swap della settimana corrente; la cancellazione avviene soltanto dopo una generazione completata.

### Rotazione settimanale

La rotazione esegue, in una transazione MongoDB, queste operazioni:

1. copia il calendario successivo come calendario corrente;
2. copia la struttura successiva come struttura corrente;
3. elimina gli swap correnti e porta gli swap futuri nella settimana corrente;
4. elimina i vecchi vincoli correnti e porta i vincoli futuri nella settimana corrente;
5. ricrea nella settimana successiva le copie dei vincoli permanenti;
6. genera un nuovo calendario successivo.

L'operazione usa una chiave per settimana e un lock persistito. Richiami duplicati nella stessa settimana vengono ignorati e un errore interrompe la transazione. Il database deve quindi supportare le transazioni MongoDB; MongoDB Atlas è un'opzione compatibile, ma non è un requisito di hosting.

## Configurazione

### Prerequisiti

- Node.js e npm per il backend.
- Un database MongoDB raggiungibile dal server e con supporto alle transazioni per la rotazione settimanale.
- JDK 17 e Android SDK 35 per la build Android.
- Android 7.0 o successivo sul dispositivo, perché `minSdk` è 24.

### Variabili d'ambiente del server

Il file `Server/.env.example` elenca i valori attesi. Il progetto non carica automaticamente un file `.env`: in locale le variabili devono essere esportate nella shell, mentre in produzione devono essere configurate nel pannello o nel secret manager del provider.

| Variabile | Obbligatoria | Default | Uso |
| --- | --- | --- | --- |
| `MONGO_URI` | Sì | Nessuno | Stringa di connessione e database MongoDB; il server interrompe l'avvio se manca |
| `PORT` | No | `3000` | Porta HTTP su cui Express rimane in ascolto |
| `APP_TIME_ZONE` | No | `Europe/Rome` | Fuso usato per giorno corrente, pubblicazione e chiave settimanale |
| `CRON_SECRET` | Sì per le automazioni | Nessuno | Segreto confrontato con l'header `x-cron-secret`; se assente gli endpoint cron rispondono `503` |
| `SESSION_TTL_HOURS` | No | `168` | Durata delle sessioni Bearer; il server impone almeno un'ora |
| `ALLOW_ADMIN_RESET` | No | Disabilitato | Abilita solo con il valore esatto `true` l'endpoint di manutenzione per il reset admin, comunque protetto da `CRON_SECRET` |

`ALLOW_ADMIN_RESET` deve rimanere disabilitato durante il normale funzionamento.

### Configurazione Android

Creare `App/local.properties`, che è escluso da Git:

```properties
sdk.dir=/percorso/del/tuo/Android/sdk
BASE_URL=https://api.example.com
```

`BASE_URL` non deve terminare con `/` e viene incorporato nell'APK in fase di compilazione. Il manifest attuale non abilita il traffico HTTP in chiaro: per un dispositivo moderno usare un endpoint HTTPS anche in collaudo, oppure predisporre una configurazione di rete limitata alla sola variante debug.

### Dati iniziali

Il repository non contiene credenziali né un bootstrap automatico dell'amministratore. Prima del primo login occorre inserire nella collezione `users` un documento admin con email normalizzata in minuscolo, codice numerico univoco, `isAdmin: true` e password già trasformata con bcrypt.

Per generare l'hash senza scrivere la password nella cronologia della shell, installare prima le dipendenze del server ed eseguire:

```bash
cd Server
npm ci
read -s ADMIN_PASSWORD
printf '\n'
export ADMIN_PASSWORD
node -e 'const bcrypt = require("bcryptjs"); console.log(bcrypt.hashSync(process.env.ADMIN_PASSWORD, 10))'
unset ADMIN_PASSWORD
```

Copiare l'hash stampato nel campo `password` del documento MongoDB. Il backend usa lo stesso costo bcrypt, pari a 10, quando crea o aggiorna le password. Una struttura minima è:

```json
{
  "name": "Admin",
  "surname": "AlfaPizza",
  "email": "admin@example.com",
  "phone": "",
  "code": 0,
  "password": "<hash bcrypt>",
  "isAdmin": true
}
```

Aprire quindi la console del database, per esempio con `mongosh "$MONGO_URI"`, e creare il documento tramite un `updateOne` con `upsert`:

```javascript
db.users.updateOne(
  { code: 0 },
  {
    $set: {
      name: "Admin",
      surname: "AlfaPizza",
      email: "admin@example.com",
      phone: "",
      code: 0,
      password: "<hash bcrypt>",
      isAdmin: true
    }
  },
  { upsert: true }
)
```

Usare il database indicato da `MONGO_URI`, sostituire l'hash e scegliere email e codice che non siano già assegnati.

Le due strutture settimanali vengono create automaticamente al primo accesso alle relative API se non esistono. I valori iniziali sono zero rider richiesti per ogni giorno, minimo `1`, massimo `7` e giorno di pubblicazione `3`, cioè giovedì. I calendari vengono creati quando l'amministratore avvia la rispettiva generazione.

## Avvio locale

### Backend

Da una shell POSIX:

```bash
cd Server
npm ci
export MONGO_URI='mongodb://host:porta/AlfaPizza'
export APP_TIME_ZONE='Europe/Rome'
export CRON_SECRET='sostituire-con-un-segreto-casuale-lungo'
npm start
```

Verificare il servizio con:

```bash
curl --fail http://localhost:3000/api/health
```

L'endpoint restituisce `200` quando MongoDB è connesso e `503` quando il processo è attivo ma il database non è disponibile.

### App Android

Dopo aver configurato `App/local.properties`, aprire `App/` in Android Studio oppure usare il wrapper Gradle dalla stessa cartella. Il backend indicato da `BASE_URL` deve essere raggiungibile dal dispositivo o dall'emulatore.

## Installazione e distribuzione

### Backend provider-neutral

Il server può essere distribuito su qualsiasi servizio in grado di eseguire Node.js e raggiungere MongoDB:

1. impostare `Server/` come directory di lavoro;
2. eseguire `npm ci` durante la build o il rilascio;
3. avviare il processo con `npm start`;
4. configurare le variabili della tabella precedente nel provider;
5. esporre il servizio tramite HTTPS;
6. usare `GET /api/health` come health check.

Render può essere usato come esempio di web service Node, ma il codice non dipende dalle sue API. Il workflow `.github/workflows/cron-tasks.yml` contiene attualmente un URL Render esplicito: se si cambia servizio o dominio, occorre aggiornare quell'URL.

### Automazioni pianificate

Gli endpoint sotto `/api/cron/` non usano una sessione utente. Ogni chiamata deve includere lo stesso segreto configurato sul server:

```bash
export SERVER_URL='https://api.example.com'
curl --fail \
  -H "x-cron-secret: $CRON_SECRET" \
  "$SERVER_URL/api/cron/rotate-week"
```

Pianificare `/api/cron/rotate-week` il lunedì a partire dalle 00:00 nel fuso `Europe/Rome`. L'idempotenza protegge da chiamate ripetute nella stessa settimana. `/api/cron/check-publication` può essere richiamato periodicamente per il controllo di log, ma la visibilità del calendario viene comunque applicata direttamente su ogni richiesta.

Il workflow GitHub Actions incluso esegue un controllo ogni ora: durante la prima ora del lunedì richiama la rotazione, negli altri casi richiama il controllo di pubblicazione. Il repository secret GitHub `CRON_SECRET` deve coincidere con la variabile configurata sul server.

### Build e installazione Android

Per creare un APK debug installabile:

```bash
cd App
./gradlew :app:assembleDebug
```

L'output viene scritto in:

```text
App/app/build/outputs/apk/debug/app-debug.apk
```

Con un dispositivo autorizzato tramite ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

La configurazione di firma release non è inclusa nel repository. Per distribuire una build di produzione occorre configurare una chiave privata fuori da Git e generare un APK o Android App Bundle firmato tramite Android Studio o una pipeline protetta.

## Test e verifiche

### Backend

```bash
cd Server
npm test
node --check server.js
```

I test automatici presenti verificano la whitelist delle collezioni API, il parsing Bearer, l'hash dei token e il confronto del segreto cron. Non sono test di integrazione con MongoDB.

### Android

```bash
cd App
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebug
```

I test JVM in `App/app/src/test` verificano il calcolo del giorno nel fuso `Europe/Rome`, la composizione degli snapshot dei vincoli, la serializzazione delle relative modifiche e la traduzione degli indicatori colorati in stati accessibili. Compilazione, lint e generazione dell'APK completano la verifica Android.

## Struttura del repository

| Percorso | Contenuto |
| --- | --- |
| `App/` | Progetto Android, wrapper Gradle e modulo applicativo |
| `App/app/src/main/java/` | Model, networking, sessioni, schermate Compose e ViewModel |
| `App/app/src/main/res/` | Risorse grafiche, temi e traduzioni italiano/inglese |
| `Server/` | Backend Express, configurazione, dipendenze e test Node.js |
| `Server/server.js` | API, autorizzazioni, generatore calendario, swap e cron |
| `Server/security.js` | Token, hash, parsing Bearer, whitelist e confronto dei segreti |
| `Server/test/` | Test automatici delle funzioni di sicurezza |
| `.github/workflows/` | Automazione GitHub Actions per i richiami cron |
| `assets/` | Screenshot usati in questo README |
| `android/` | Contiene il file tracciato `FakeDependency.jar` |
