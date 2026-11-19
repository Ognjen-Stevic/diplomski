# Arhitektura AI Decision Tree modula

## Status dokumenta

Ovaj dokument opisuje trenutno implementirano stanje AI modula u aplikaciji **Sena Spirits – Respekt**. Izvor istine su aktuelni Java i React/TypeScript kod, PostgreSQL šema, konfiguracija i testovi. Dokument ne opisuje ranije uklonjene ručne CEO akcije za trening, osvežavanje ili aktivaciju modela.

Provera aktivne baze izvršena je read-only upitima 19. novembra 2026. Baza sadrži modele različitog porekla, ali je trenutno aktivni model označen kao **SYNTHETIC_DEMO**. Zbog toga ovaj dokument ne tvrdi da postoji poslovno validiran produkcioni model niti navodi izmišljene vrednosti tačnosti.

---

## 1. Uvod

### 1.1. Poslovni problem

Komercijalista često ima veliki broj kupaca i ne može jednako intenzivno da prati svakog od njih. Kupac može postepeno da smanji učestalost poručivanja, količinu ili potpuno prestane da kupuje. Ako se promena primeti kasno, propušta se vreme za preventivni kontakt.

AI modul zato procenjuje **rizik pada buduće kupovne aktivnosti kupca** na osnovu istorije završenih NORMAL trebovanja. Rezultat pomaže korisniku da odredi prioritet rada; ne donosi poslovnu odluku umesto korisnika.

### 1.2. Zašto se koristi AI

Ručno pravilo, na primer „visok rizik ako nije bilo porudžbine 60 dana“, koristi jedan unapred izabran prag. Trenirani model istovremeno razmatra osam pokazatelja i pragove uči iz istorijskih primera. Time može da prepozna kombinacije kao što su:

- duga pauza od poslednje porudžbine;
- rast prosečnog razmaka između porudžbina;
- pad broja porudžbina;
- pad litraže u odnosu na prethodni period;
- različito značenje istog pada kod novog i dugogodišnjeg kupca.

### 1.3. Zašto Decision Tree

Izabran je klasifikacioni **CART Decision Tree** iz biblioteke Smile 4.4.0 zato što:

- daje klasu LOW, MEDIUM ili HIGH;
- putanja od korena do lista može da se prikaže korisniku;
- pragovi i ulazne vrednosti su razumljivi;
- model je dovoljno mali za izvršavanje unutar Spring Boot procesa;
- artefakt može da se verzionira, proveri i čuva u PostgreSQL-u.

### 1.4. Šta sistem predviđa

Za zreli istorijski primer posmatra se ponašanje kupca u narednih 60 dana:

- **LOW** — postoji buduća kupovina i litraža je najmanje 50% litraže iz prethodnih 60 dana;
- **MEDIUM** — postoji buduća kupovina, ali je litraža manja od 50%;
- **HIGH** — u narednih 60 dana nema kupovine.

Trenirani model u realnoj proceni ne vidi budućnost. On na osnovu sadašnjih feature-a predviđa kojoj od ove tri istorijski definisane klase kupac najviše liči.

### 1.5. Šta sistem ne predviđa

Sistem ne predviđa tačan datum sledeće porudžbine, tačnu količinu ili prihod, razlog pada aktivnosti, kreditni rizik, uspeh budućeg kontakta niti uzročno-posledičnu vezu između akcije i ponašanja kupca. Preporuke su statičan tekst vezan za procenjenu klasu, a ne drugi generativni AI model.

---

## 2. Arhitektura AI sistema

### 2.1. Mesto u aplikaciji

AI je interni modul istog Spring Boot backenda. Ne postoji odvojeni Python servis. Smile model se trenira, učitava i izvršava u JVM-u, dok React prikazuje rezultate preko REST API-ja.

~~~mermaid
flowchart LR
    UI[React / TypeScript] -->|JWT + REST| API[CustomerRiskController]
    API --> RS[CustomerRiskService]
    RS --> STORE[RiskStore / JdbcTemplate]
    STORE --> DB[(PostgreSQL)]
    REQ[RequisitionServiceImpl] --> OUT[AiCaptureOutbox]
    OUT --> DB
    SCH[AiLifecycleScheduler] --> CAP[AiCaptureService]
    SCH --> ASS[AiAssessmentService]
    SCH --> TRAIN[AiTrainingService]
    CAP --> DB
    ASS --> MODEL[AiModelService]
    TRAIN --> SMILE[Smile CART DecisionTree]
    MODEL --> SMILE
    MODEL --> DB
~~~

### 2.2. Paketi i odgovornosti

| Paket / klasa | Odgovornost |
|---|---|
| **ai.api.CustomerRiskController** | Aktuelni read API i evidentiranje aktivnosti. |
| **ai.service.CustomerRiskService** | Autorizovani katalog, detalj, zbirni pregled i aktivnosti. |
| **ai.service.AiAssessmentService** | Feature izračunavanje, predikcija, istorija, snapshot i retry red. |
| **ai.lifecycle.capture** | Outbox, reconciliation i pretvaranje trebovanja u opservacije. |
| **ai.lifecycle.training** | Eligibility, dataset fingerprint, trening, evaluacija i koordinacija lease-a. |
| **ai.lifecycle.model.AiModelService** | Bezbedno učitavanje, checksum, cache i transakciona promocija. |
| **ai.model.DecisionTreeModel** | Smile trening, predikcija, evaluacija i putanja kroz stablo. |
| **ai.model.DecisionTreeArtifactCodec** | Ograničena Java serijalizacija, allow-list i SHA-256. |
| **ai.domain.CustomerFeatures** | Jedinstveno feature i label izračunavanje. |
| **ai.persistence.RiskStore** | Parametrizovani SQL, scope, mapiranje i pristup AI tabelama. |
| **ai.audit.AiAuditLogger** | Best-effort JSONL audit tokovi; PostgreSQL ostaje izvor istine. |
| **ai.service.AiOperationalStatusService** | Health stanje, heartbeat i kategorizovane greške. |

### 2.3. Odvajanje odgovornosti

- Poslovni servis trebovanja samo prijavljuje završeno NORMAL trebovanje.
- Capture sloj ne trenira model; stvara opservaciju i red za procenu.
- Assessment sloj ne trenira niti promoviše model.
- Training sloj ne menja trebovanja i kupce.
- Model service jedini učitava i promoviše verzionisani artefakt.
- Customer access policy i SQL scope ograničavaju podatke pre paginacije.

### 2.4. Smile integracija

Trening koristi **smile.classification.DecisionTree.fit** i numerički DataFrame sa osam kolona.

| Parametar | Vrednost |
|---|---:|
| Algoritam | Smile 4.4.0 CART |
| Split pravilo | GINI |
| maxDepth | 5 |
| maxNodes | 16 |
| nodeSize | 10 |

Pragove podela ne određuje Java poslovno pravilo. Smile ih bira tokom treninga tako da smanji GINI nečistoću.

---

## 3. Tok AI podataka

~~~mermaid
flowchart TD
    A[Requisition prelazi u COMPLETED] --> B{type = NORMAL?}
    B -->|ne, GRATIS| X[Ne ulazi u AI prodajne opservacije]
    B -->|da| C[afterCommit outbox enqueue]
    C --> D[ai_outbox_event]
    D --> E[AiCaptureService]
    E --> F[ai_order_observation]
    E --> G[ai_assessment_work]
    G --> H[CustomerFeatures.extract]
    H --> I{Dovoljno podataka i aktivan model?}
    I -->|da| J[Smile predikcija LOW / MEDIUM / HIGH]
    I -->|ne| K[Status dostupnosti]
    J --> L[ai_risk_assessment + ai_risk_history]
    K --> L
    H --> M[Dnevni ai_risk_snapshot]
    M --> N[60 dana sazrevanja + capture coverage]
    N --> O[ai_training_dataset]
    O --> P[Trening i evaluacija]
    P --> Q[ai_model kandidat]
    Q --> R{Promotion gates}
    R -->|prošao| S[ai_model_activation + active model]
    R -->|nije prošao| T[Sačuvan odbijeni kandidat]
    S --> U[Refresh svih procena]
    L --> V[React AI Analytics]
~~~

### 3.1. Capture završene prodaje

Kada **RequisitionServiceImpl** prvi put sačuva prelaz NORMAL trebovanja u COMPLETED, **AiCaptureOutbox.afterCommit** pokušava da upiše jedinstveni događaj. Periodični reconciliation dodatno pronalazi svako završeno NORMAL trebovanje bez opservacije. Zato neuspeh post-commit enqueue poziva ne znači trajni gubitak događaja.

**AiCaptureService** na svakih 60 sekundi, po podrazumevanoj konfiguraciji:

1. vraća istekle PROCESSING događaje;
2. dopunjava nedostajuće outbox događaje;
3. preuzima najviše 100 redova uz FOR UPDATE SKIP LOCKED;
4. računa litre kao zbir quantity × volume_liters;
5. upisuje jedinstvenu opservaciju po trebovanju;
6. stavlja kupca u assessment work red;
7. obrađuje pending i zastarele procene.

### 3.2. Dnevni snapshot

U 02:15, u zoni Europe/Belgrade, sistem ponovo procenjuje kupce u batch-evima od 500 i pokušava da sačuva najviše jedan feature snapshot po kupcu i danu. Ovo je point-in-time zapis; stari snapshot se ne rekonstruiše naknadno iz podataka koji tada nisu bili poznati.

### 3.3. Trening i aktivacija

Nedeljom u 03:00 proverava se eligibility. Trening se pokreće samo ako postoji nov, zreo i validan dataset. Kandidat se evaluira protiv jednostavnog baseline-a i, ako postoji, protiv aktivnog modela. Promocija je automatska i transakciona. Posle promocije pokreće se refresh svih kupaca bez pravljenja dodatnog dnevnog snapshot-a.

---

## 4. Feature engineering

Sve feature-e računa **CustomerFeatures.extract**. U račun ulaze samo opservacije kod kojih su i createdAt i observedAt najkasnije u trenutku procene. Intervali su sa otvorenom levom i zatvorenom desnom granicom: (at − period, at].

| Feature | Značenje i izvor | Izračunavanje | Zašto je važan |
|---|---|---|---|
| **recencyDays** | Dani od poslednjeg poznatog NORMAL trebovanja. | at − poslednja opservacija; bez opservacija at − created_at kupca. | Veća pauza često ukazuje na prekid ritma. |
| **intervalDays** | Prosečan razmak opservacija u poslednjih 90 dana. | (poslednja − prva) / (broj − 1); 0 za manje od dve. | Rast razmaka može pokazati usporavanje. |
| **orders30** | Broj opservacija u poslednjih 30 dana. | COUNT u (at−30d, at]. | Kratkoročna aktivnost. |
| **orders60** | Broj opservacija u poslednjih 60 dana. | COUNT u (at−60d, at]. | Stabilniji kratkoročni ritam. |
| **orders90** | Broj opservacija u poslednjih 90 dana. | COUNT u (at−90d, at]. | Istorija potrebna i za minimum podataka. |
| **liters60** | Litraža u poslednjih 60 dana. | SUM liters u (at−60d, at]. | Trenutni obim i osnova label-a. |
| **priorLiters60** | Litraža u prethodnom 60-dnevnom prozoru. | SUM liters u (at−120d, at−60d]. | Pokazuje promenu između perioda. |
| **relationshipDays** | Starost odnosa sa kupcem. | at − customer.created_at. | Razdvaja novi od dugoročnog odnosa. |

### 4.1. Numerički primer

Za procenu 1. oktobra, kupca kreiranog pre 300 dana i poznate NORMAL opservacije 10. juna 40 l, 20. jula 30 l, 20. avgusta 20 l i 10. septembra 10 l, približno važi:

- recencyDays = 21;
- orders30 = 1, orders60 = 2, orders90 = 3;
- liters60 = 30, priorLiters60 = 70;
- intervalDays = (10. septembar − 20. jul) / 2 = 26;
- relationshipDays = 300.

Primer je ilustrativan. Stvarni pragovi stabla zavise od treniranog artefakta.

### 4.2. Minimum za predikciju

Features.sufficient zahteva relationshipDays ≥ 90, orders90 ≥ 3 i liters60 > 0. Ako uslov nije ispunjen, Decision Tree se ne poziva.

---

## 5. Klasifikacija rizika i dostupnost procene

### 5.1. Prediktivne klase

| Klasa | Tumačenje | Prikazana preporuka |
|---|---|---|
| **LOW** | Obrazac najviše liči na očuvanu aktivnost. | Nastavite redovno praćenje kupca. |
| **MEDIUM** | Obrazac liči na značajan pad, ali ne potpuni izostanak. | Pratite poručivanje i razmotrite preventivni kontakt. |
| **HIGH** | Obrazac liči na kupce bez buduće kupovine u label prozoru. | Kontaktirajte kupca i proverite razlog pada aktivnosti. |

Ove klase nisu verovatnoće i UI ne prikazuje procenat sigurnosti.

### 5.2. Stanja dostupnosti

| Stanje | Gde postoji | Značenje |
|---|---|---|
| **INSUFFICIENT_DATA** | Level i assessment status | Nema minimuma podataka; stablo nije pozvano. |
| **MODEL_UNAVAILABLE** | Assessment status | Feature-i su dovoljni, ali nema učitljivog aktivnog modela; level se trenutno čuva kao INSUFFICIENT_DATA. |
| **NOT_ASSESSED** | Koncept/UI opis, nije Java enum niti DB status literal | Još nema reda procene; left join daje at = null i prikaz „nedovoljno podataka“. |
| **ASSESSED** | Assessment status | Stablo je izvršeno i level je LOW, MEDIUM ili HIGH. |

Stale je true ako procena ne postoji, starija je od dva dana ili model_version nije aktivna verzija.

---

## 6. Automatski AI lifecycle

| Operacija | Podrazumevani raspored | Konfiguracija |
|---|---|---|
| Capture, reconciliation i work queue | fixed delay 60 s | AI_CAPTURE_DELAY_MS |
| Dnevna procena i snapshot | svakog dana 02:15 | AI_ASSESSMENT_CRON |
| Eligibility i mogući trening | nedelja 03:00 | AI_TRAINING_CRON |
| Vremenska zona | Europe/Belgrade | AI_TIME_ZONE |
| Scheduler | uključen | AI_SCHEDULING_ENABLED |
| Maksimum retry pokušaja | 3 | AI_MAX_RETRIES |
| Training lease | PT1H | AI_LOCK_LEASE |

### 6.1. Startup recovery

Na ApplicationReadyEvent sistem upisuje heartbeat, označava napuštene RUNNING treninge, oslobađa istekli training lease, vraća istekle outbox događaje, odmah radi reconciliation/capture i beleži najskoriji propušteni nedeljni termin kao MISSED.

### 6.2. Retry i izolacija greške

Outbox i assessment work koriste PENDING, PROCESSING, COMPLETED i FAILED, lease vreme i SKIP LOCKED. Jedan neuspešan kupac ne prekida ceo batch. Assessment retry koristi rastući backoff do terminalnog trećeg neuspeha; reconciliation periodično vraća stare failed outbox događaje.

### 6.3. Monitoring

**ai_job_execution** čuva izvršenja, **ai_system_state** trenutni status, heartbeat i poslednje greške. JSONL logovi se razdvajaju na decisions, training, lifecycle i errors. U aktuelnom React frontend-u ne postoji CEO monitoring/admin ekran; monitoring je backend i operativna mogućnost, ne korisničko dugme.

---

## 7. Funkcionalnosti po ulozi

Svi /api/ai/** zahtevi zahtevaju ulogu SALES_REP, MANAGER ili CEO i authority customers.read. BOOKKEEPER nema pristup.

### 7.1. SALES_REP

- vidi samo sopstvene kupce dozvoljenog statusa;
- vidi RiskOverview na početnoj strani i RiskBadge na /kupci;
- koristi /ai-analitika sa pretragom, filterom i paginacijom;
- otvara feature-e, putanju stabla, preporuku i aktivnosti;
- jedini može da evidentira CONTACT ishod za svog kupca.

### 7.2. MANAGER

- vidi kupce prodajnih predstavnika svog tima;
- vidi timski zbir, listu i detalj procene;
- može da pretražuje i filtrira;
- ne može da bira proizvoljan managerId niti da upisuje CONTACT.

### 7.3. CEO

- vidi kompanijski scope kupaca;
- RiskOverview prikazuje timske redove i broj visokorizičnih kupaca;
- može da otvori managerId filter i detalj kupca;
- nema ručne train, refresh ili activate kontrole;
- nema aktuelni frontend ekran za ai_system_state, job history ili listu modela.

### 7.4. Aktuelni API

| Metod i putanja | Namena |
|---|---|
| GET /api/ai/customers/catalog | Katalog filtriran obaveznim risk parametrom; podrazumevano 24 po strani. |
| POST /api/ai/customers/risks/batch | Bedževi za najviše 100 ID-jeva. |
| GET /api/ai/customers/risks | Risk lista; podrazumevano 20 po strani. |
| GET /api/ai/summary | Distribucija, povećan rizik, procenat HIGH i CEO timovi. |
| GET /api/ai/customers/{id}/risk | Detalj procene. |
| GET /api/ai/customers/{id}/activities | Poslednjih 100 aktivnosti. |
| POST /api/ai/customers/{id}/activities | Aktivnost; servis dopušta samo SALES_REP vlasniku. |

Putanje /api/ai/admin/train, /refresh, /models i /monitoring nemaju aktivne kontrolere i vraćaju 404 za CEO.

---

## 8. PostgreSQL arhitektura AI modula

| Tabela | Uloga |
|---|---|
| **ai_order_observation** | Jedna NORMAL prodajna opservacija po završenom trebovanju. |
| **ai_risk_snapshot** | Dnevni point-in-time feature vektor. |
| **ai_risk_assessment** | Poslednja procena po kupcu. |
| **ai_risk_history** | Append-only istorija level vrednosti. |
| **ai_risk_activity** | Aktivnost vezana za istorijsku procenu. |
| **ai_outbox_event** | Durable capture događaj sa retry/lease stanjem. |
| **ai_assessment_work** | Konsolidovani work red po kupcu. |
| **ai_training_dataset** | Fingerprint, split statistika, status i rezultat treninga. |
| **ai_model** | UUID, Vxxx, artefakt, checksum, metrike, provenance i promotion odluka. |
| **ai_model_activation** | Istorija važenja modela. |
| **ai_job_execution** | Audit lifecycle poslova. |
| **ai_lifecycle_lock** | Distribuirani training lease. |
| **ai_capture_run** | Dokaz kontinuiteta capture-a. |
| **ai_system_state** | Singleton health i monitoring stanje. |
| **ai_schema_migration** | Evidencija AI verzije šeme; runtime ne pokreće migration alat. |

~~~mermaid
erDiagram
    REQUISITION ||--o| AI_OUTBOX_EVENT : triggers
    REQUISITION ||--o| AI_ORDER_OBSERVATION : captured_as
    REQUISITION o|--o{ AI_ASSESSMENT_WORK : source
    CUSTOMER ||--o{ AI_ORDER_OBSERVATION : has
    CUSTOMER ||--o{ AI_RISK_SNAPSHOT : snapshots
    CUSTOMER ||--o| AI_RISK_ASSESSMENT : current
    CUSTOMER ||--o{ AI_RISK_HISTORY : history
    CUSTOMER ||--o| AI_ASSESSMENT_WORK : queued
    AI_MODEL ||--o{ AI_RISK_ASSESSMENT : predicts
    AI_MODEL ||--o{ AI_RISK_HISTORY : predicts
    AI_MODEL ||--o{ AI_MODEL_ACTIVATION : timeline
    AI_MODEL o|--o{ AI_TRAINING_DATASET : produced
    AI_RISK_HISTORY ||--o{ AI_RISK_ACTIVITY : referenced_by
    APP_USER ||--o{ AI_RISK_ACTIVITY : records
~~~

Ključne DB odbrane su UNIQUE requisition_id za outbox i opservaciju, PK customer_id za aktuelnu procenu i work red, UNIQUE customer_id + at za snapshot, PK fingerprint, parcijalni UNIQUE za jedan aktivni model i FK veze ka kupcu, trebovanju, modelu i korisniku.

---

## 9. Bezbednost i pouzdanost

### 9.1. Autorizacija i vidljivost

Spring Security proverava JWT, ulogu i customers.read. **RiskScopeFactory** zatim u parametrizovani SQL ugrađuje status i scope: sopstveni kupci, kupci tima ili svi kupci. Filteri se primenjuju pre LIMIT/OFFSET i count upita. Customer catalog dodatno proverava hidrirane entitete; konkurentna promena vraća 409 umesto tihe neusklađenosti stranice.

### 9.2. Transakciona doslednost

- Poslovna transakcija commit-uje pre post-commit enqueue pokušaja.
- Reconciliation nadoknađuje događaj ako enqueue nije uspeo.
- Assessment i history se upisuju u istoj transakciji.
- Promocija zaključava ai_model, proverava očekivani aktivni UUID, zatvara prethodnu aktivaciju i aktivira kandidata.

### 9.3. Integritet modela

Artefakt ima SHA-256 checksum. Deserijalizacija dopušta samo ograničen skup klasa, maksimalno 5 MB, ograničenu dubinu, broj referenci i nizove. Posle treninga radi se serialize/deserialize round-trip i porede predikcije.

### 9.4. Idempotentnost, konkurentnost i audit

ON CONFLICT, fingerprint, schedule_key, generation, lease i SKIP LOCKED omogućavaju bezbedno ponavljanje. Training lease sprečava paralelni trening, a ekskluzivno zaključavanje dve promocije. PostgreSQL je autoritativan; JSONL audit je best-effort i njegov neuspeh ne poništava DB transakciju.

---

## 10. Ograničenja

1. **Aktivni model je SYNTHETIC_DEMO.** To nije dokaz poslovne tačnosti na realnim podacima.
2. Novi automatski modeli dobijaju provenance **UNVERIFIED**. Validacione kapije nisu formalna poslovna verifikacija.
3. Label je izveden samo iz kasnijih NORMAL trebovanja; ne objašnjava razlog pada.
4. Potrebno je najmanje 150 zrelih nepreklapajućih snapshot-a, najmanje 90 train i 30 evaluation primera i po 10 svake klase u oba dela.
5. Snapshot postoji samo od trenutka rada schedulera; nema backdated rekonstrukcije.
6. Feature-i ne uključuju cenu, maržu, vrstu proizvoda, kontakt aktivnosti, sezonalnost ili spoljne podatke.
7. Procena je kategorija, ne kalibrisana verovatnoća.
8. Preporuke su unapred definisane po klasi.
9. UI nema model registry, job monitoring ni ručnu administraciju.
10. Veći obim podataka zahteva novo merenje batch, memorijskih i SQL granica.

---

## Zaključak

Implementacija predstavlja stvarno trenirani, verzionisani i automatski upravljani Decision Tree, a ne skup hardkodovanih risk pragova. Hardkodovani su poslovna definicija label-a, minimalni kvalitet podataka, granice složenosti i validacione kapije. Smile iz training primera uči split pragove. Arhitektura povezuje poslovnu transakciju, durable capture, point-in-time feature-e, hronološku evaluaciju, kontrolisanu promociju i role-based prikaz, uz jasno ograničenje da trenutni aktivni demo model nije dokaz produkcione tačnosti.
