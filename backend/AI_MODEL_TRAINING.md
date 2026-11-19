# Trening AI Decision Tree modela

## Status dokumenta

Dokument opisuje aktuelni automatski trening u klasama **CustomerFeatures**, **DecisionTreeModel**, **AiTrainingEligibilityService**, **AiTrainingService** i **AiModelService**. Ne postoje aktivna CEO dugmad niti REST endpointi za ručni train, refresh ili activate postupak.

Read-only provera aktivne baze 19. novembra 2026. pokazala je da je trenutno aktivan model porekla **SYNTHETIC_DEMO**. Postoje istorijski modeli drugih provenance vrednosti, ali sam naziv PRODUCTION nije dokaz nezavisno potvrđene poslovne tačnosti. Zato dokument ne navodi procenat tačnosti ili produkcione performanse koje kod i validacioni izveštaj ne potvrđuju.

---

## 1. Pregled treninga

Sistem trenira višeklasni CART Decision Tree koji klasifikuje kupca u LOW, MEDIUM ili HIGH rizik pada kupovne aktivnosti. Training primer nije sirovo trebovanje, već par:

    X = feature vektor kupca u trenutku snapshot-a
    y = ponašanje kupca tokom narednih 60 dana

Automatski proces ima sledeće faze:

1. capture završenih NORMAL trebovanja;
2. dnevni point-in-time feature snapshot;
3. čekanje 60 dana da budući ishod sazri;
4. provera kontinuiteta capture-a;
5. label generisanje;
6. uklanjanje preklapajućih primera;
7. hronološka train/evaluation podela sa purge razmakom;
8. Smile CART trening;
9. poređenje sa baseline-om i aktivnim modelom;
10. automatska promocija samo ako su sve kapije ispunjene.

~~~mermaid
flowchart LR
    O[ai_order_observation] --> S[ai_risk_snapshot]
    S --> M[60 dana sazrevanja]
    O --> L[Label iz budućeg prozora]
    M --> D[Prepared dataset]
    L --> D
    D --> T[Chronological train]
    D --> E[Purged evaluation]
    T --> C[Smile CART kandidat]
    C --> V[Metric gates]
    E --> V
    V --> P{Promotion}
    P -->|da| A[Active model]
    P -->|ne| R[Rejected candidate]
~~~

---

## 2. Prikupljanje podataka

Ulazni poslovni događaj je završeno trebovanje koje ispunjava oba uslova:

    requisition.status = COMPLETED
    requisition.type = NORMAL

GRATIS trebovanja se ne koriste kao prodajna opservacija. **AiCaptureService** iz stavki računa:

    liters = SUM(requisition_item.quantity × product.volume_liters)

U **ai_order_observation** se čuvaju requisition_id, customer_id, created_at, observed_at i liters. Jedinstveni requisition_id omogućava ponovljiv capture bez dupliranja.

Capture se podrazumevano izvršava na 60 sekundi i radi reconciliation svih završenih NORMAL trebovanja bez opservacije. Zbog toga dataset ne zavisi samo od jednog post-commit callback-a.

---

## 3. Feature snapshot-i

Svakog dana u 02:15 **AiAssessmentService.maintainDailySnapshots** računa trenutno poznate feature-e i čuva najviše jedan **ai_risk_snapshot** po kupcu i kalendarskom danu.

Feature vektor ima osam elemenata, u fiksnom redosledu:

    [recencyDays, intervalDays, orders30, orders60,
     orders90, liters60, priorLiters60, relationshipDays]

Snapshot je point-in-time zapis. Opservacija se koristi samo ako važi:

    order.createdAt <= snapshot.at
    order.observedAt <= snapshot.at

Time se sprečava da naknadno poznat događaj promeni istorijski feature vektor.

---

## 4. Kreiranje training dataseta

**AiTrainingEligibilityService.check** učitava capture tačke, order opservacije i snapshot-e. Za svaki snapshot:

1. proverava da li postoji neprekinuto capture pokriće budućeg prozora;
2. rekonstruiše Example sa sačuvanim feature-ima;
3. iz opservacija generiše label;
4. prosleđuje primere metodi **DecisionTreeModel.prepare**.

Postavljene su memorijske zaštite:

- najviše 50.000 snapshot redova;
- najviše 500.000 order opservacija.

Ako je granica prekoračena, eligibility se preskače umesto nekontrolisanog učitavanja u JVM.

---

## 5. Generisanje label-a

Za snapshot u trenutku t posmatra se budući prozor:

    (t, t + 60 dana]

Neka je:

    L_pre = snapshot.features.liters60
    L_future = zbir litara u narednih 60 dana

Label funkcija je:

    HIGH   (2), ako nema nijedne buduće opservacije
    MEDIUM (1), ako postoji opservacija i L_future < 0,5 × L_pre
    LOW    (0), u suprotnom

### Primer

Ako je liters60 = 100 l:

- nema naredne porudžbine → HIGH;
- naredne porudžbine ukupno 35 l → MEDIUM, jer je 35 < 50;
- naredne porudžbine ukupno 70 l → LOW.

Ovo je hardkodovana poslovna definicija ciljne klase. Ona nije isto što i model. Model kasnije uči kako sadašnji feature-i razdvajaju te istorijski formirane klase.

---

## 6. Zahtevi zrelosti podataka

Primer mora da ispuni sve sledeće uslove:

### 6.1. Dovoljni feature podaci

    relationshipDays >= 90
    orders90 >= 3
    liters60 > 0

### 6.2. Sazreo outcome prozor

    snapshot.at + 60 dana <= now

### 6.3. Capture coverage

Između snapshot-a i kraja 60-dnevnog prozora capture tačke ne smeju imati razmak veći od 36 sati, a poslednja tačka mora stići najkasnije 24 sata pre kraja. Bez ovog dokaza „nema porudžbine“ bi mogao zapravo da znači „sistem nije prikupljao podatke“.

### 6.4. Minimalna veličina

- najmanje 150 zrelih, nepreklapajućih primera ukupno;
- najmanje 90 training primera;
- najmanje 30 evaluation primera;
- najmanje 10 primera svake od tri klase u oba skupa.

Ako bilo koji uslov nije ispunjen, trening se preskače sa razlogom; to nije sistemska greška.

---

## 7. Sprečavanje data leakage-a

Data leakage nastaje kada trening koristi informaciju koja u trenutku stvarne procene ne bi bila poznata. Implementacija primenjuje četiri zaštite.

### 7.1. Observed-at ograničenje

Feature koristi samo opservaciju koja je već bila capture-ovana u trenutku snapshot-a.

### 7.2. Sazrevanje label-a

Snapshot ne ulazi u trening dok ne prođe ceo 60-dnevni budući prozor.

### 7.3. Nepreklapajući primeri istog kupca

Snapshot-i istog kupca moraju biti udaljeni najmanje 60 dana. Time skoro identični prozori ne preplavljuju dataset i ne završavaju sa obe strane podele.

### 7.4. Purge na granici

Ako je evaluation cutoff vreme C, training primer se prihvata samo ako važi:

    snapshot.at + 60 dana < C

Evaluation počinje od C. Tako outcome prozor training primera ne ulazi u evaluation period.

---

## 8. Training/evaluation podela

Primeri se sortiraju po vremenu snapshot-a. Cutoff se bira na indeksu 70% sortirane liste. Međutim, konačan odnos nije nužno tačno 70/30, jer se training deo dodatno purge-uje za 60-dnevni outcome prozor.

    cutoff = selected[floor(selected.size × 0,7)].at

    training   = primeri za koje at + 60 dana < cutoff
    evaluation = primeri za koje at >= cutoff

Podela je hronološka, a ne random. To bolje simulira realan slučaj: model uči iz prošlosti i proverava se na kasnijem periodu.

---

## 9. Decision Tree algoritam

Implementacija koristi Smile **DecisionTree.fit** sa CART pristupom. Svaki unutrašnji čvor bira jedan numerički feature i prag. Primer ide levom ili desnom granom dok ne stigne do lista sa klasom.

~~~mermaid
flowchart TD
    A{recencyDays <= 34,5?}
    A -->|da| B{liters60 <= 42?}
    A -->|ne| H[HIGH]
    B -->|da| M[MEDIUM]
    B -->|ne| L[LOW]
~~~

Ovo stablo je **isključivo ilustrativno**. Pragovi 34,5 i 42 nisu preuzeti iz aktivnog modela. Pravo stablo i pragove uči Smile iz konkretnog training skupa.

---

## 10. Hiperparametri

Poziv koristi:

    new DecisionTree.Options(SplitRule.GINI, 5, 16, 10)

| Parametar | Vrednost | Uloga |
|---|---:|---|
| SplitRule | GINI | Mera kvaliteta podele. |
| maxDepth | 5 | Ograničava dubinu i overfitting. |
| maxNodes | 16 | Ograničava broj listova/čvorova modela. |
| nodeSize | 10 | Sprečava podelu premalih grupa. |

Ovi parametri su trenutno fiksni u kodu i zapisuju se u model_configuration.

---

## 11. GINI nečistoća

Za čvor sa K klasa i udelom p_k svake klase:

    GINI = 1 − Σ(k=1..K) p_k²

Ako čvor ima 10 LOW, 5 MEDIUM i 5 HIGH primera, ukupno 20:

    p_LOW = 0,50
    p_MEDIUM = 0,25
    p_HIGH = 0,25

    GINI = 1 − (0,50² + 0,25² + 0,25²)
         = 1 − (0,25 + 0,0625 + 0,0625)
         = 0,625

Čist čvor sa samo jednom klasom ima GINI = 0. Što je vrednost manja, čvor je homogeniji.

---

## 12. Izbor podele stabla

Za kandidat split računa se ponderisana nečistoća dece:

    GINI_split = (n_left / n) × GINI_left
               + (n_right / n) × GINI_right

Dobit je:

    gain = GINI_parent − GINI_split

Smile ispituje moguće pragove numeričkih feature-a i bira podelu sa boljim smanjenjem nečistoće, uz ograničenja maxDepth, maxNodes i nodeSize. Java kod ne sadrži pravilo tipa „ako recencyDays > X onda HIGH“ za modelsku predikciju.

---

## 13. Evaluacione metrike

Za svaku klasu c računaju se:

    precision_c = TP_c / (TP_c + FP_c)
    recall_c    = TP_c / (TP_c + FN_c)
    F1_c        = 2 × precision_c × recall_c
                  / (precision_c + recall_c)

Ako je imenilac nula, implementacija vraća 0.

Macro-F1 tretira klase ravnopravno:

    macroF1 = (F1_LOW + F1_MEDIUM + F1_HIGH) / 3

Zbog macro proseka česta LOW klasa ne može sama da sakrije loš rad nad HIGH klasom.

---

## 14. Confusion matrix

Implementacija koristi matricu matrix[stvarna][predviđena]. Ilustrativni primer:

| Stvarno \ Predviđeno | LOW | MEDIUM | HIGH |
|---|---:|---:|---:|
| LOW | 18 | 2 | 0 |
| MEDIUM | 3 | 12 | 5 |
| HIGH | 0 | 4 | 16 |

Za HIGH bi u ovom sintetičkom primeru važilo:

    precision_HIGH = 16 / (0 + 5 + 16) = 0,762
    recall_HIGH = 16 / 20 = 0,800

Brojevi služe samo objašnjenju formule i nisu rezultat postojećeg modela.

---

## 15. Baseline poređenje

Kandidat se ne poredi sa nulom, već sa jednostavnim ručnim pravilom zasnovanim samo na recencyDays:

    recencyDays > 60  → HIGH
    recencyDays > 30  → MEDIUM
    inače             → LOW

Baseline je namerno jednostavan. Ako trenirano stablo ne nadmaši ovo pravilo, dodatna složenost nije opravdana.

---

## 16. Kriterijumi prihvatanja modela

Prva validaciona kapija u **DecisionTreeModel.train** zahteva:

    candidate.macroF1 > baseline.macroF1 + 0,02

i za svaku klasu:

    candidate.recall[class] >= 0,40

Strogo veće znači da tačno +0,02 nije dovoljno.

Ako kandidat ne ispuni uslov, čuva se kao neaktivan model sa eligible = false i ne može biti promovisan.

---

## 17. Automatska promocija

Ako nema upotrebljivog aktivnog modela, prvi kandidat koji je prošao baseline kapije može automatski postati aktivan.

Ako aktivni model postoji, kandidat i incumbent se porede na istom novom evaluation skupu. Potrebno je:

    candidate.macroF1 > incumbent.macroF1 + 0,02

i za svaku klasu:

    candidate.recall[class] >= incumbent.recall[class] − 0,05

Dakle, kandidat mora značajno da poboljša macro-F1 i ne sme da pogorša recall nijedne klase za više od 0,05.

Promocija zaključava ai_model tabelu, ponovo proverava očekivani aktivni model, deaktivira prethodni, zatvara njegov activation interval i upisuje novi interval. Ako se aktivni UUID promenio tokom evaluacije, rezultat je CONCURRENT_CHANGE i kandidat se ne aktivira.

---

## 18. Verzije i persistence

Svaki kandidat dobija:

- UUID u koloni version;
- čitljivu model_version vrednost V001, V002, ...;
- trained_at i schema_version = 1;
- serijalizovani Smile artifact;
- SHA-256 checksum;
- Evaluation JSON u metrics;
- configuration JSON;
- dataset_fingerprint;
- eligible, active i provenance;
- promotion_decision, promotion_reason i compared_to_model.

**DecisionTreeArtifactCodec** ograničava veličinu na 5 MB i koristi allow-list klasa pri deserijalizaciji. Posle serijalizacije model se učitava nazad i predikcije se porede nad izabranim primerima.

Aktivacija se dodatno beleži u **ai_model_activation**, tako da ostaje vremenska linija zamena.

---

## 19. Uslovi za ponovno treniranje

Eligibility se proverava jednom po jedinstvenom nedeljnom schedule_key. Novi trening se pokreće samo ako:

- dataset prolazi sva pravila zrelosti i podele;
- nema drugog RUNNING treninga;
- fingerprint nije već završen;
- ili je isti fingerprint FAILED_RETRYABLE, ispod maxRetries i retry_after je istekao.

Fingerprint obuhvata pripadnost train/evaluation skupu, customerId, vreme snapshot-a, label, svaki feature i evaluation cutoff. Promena sadržaja ili podele daje novi SHA-256 identitet.

Ako se podaci nisu promenili, rezultat je SKIPPED „Podaci nisu promenjeni od prethodne obuke.“ To je očekivano ponašanje, ne neuspeh.

---

## 20. Obrada grešaka

### 20.1. Koordinacija

**ai_lifecycle_lock** ima owner_id i lease_until. Samo vlasnik lease-a trenira. Startup recovery označava stare RUNNING poslove kao ABANDONED i oslobađa istekle lease vrednosti.

### 20.2. Retry

DataAccessException tokom treninga može dati FAILED_RETRYABLE, retry_after = now + 10 minuta i najviše tri pokušaja. Ostale greške ili iscrpljeni pokušaji daju FAILED.

### 20.3. Monitoring i audit

Ishod se upisuje u **ai_training_dataset**, **ai_job_execution** i **ai_system_state**, a strukturisani JSONL događaj u AI_TRAINING ili AI_ERRORS log. Očekivani SKIPPED ili odbijeni kandidat ne treba automatski tretirati kao pad sistema.

### 20.4. Posle promocije

Uspešna promocija pokreće refresh svih kupaca. Pojedinačni neuspeh procene se stavlja u durable **ai_assessment_work** red, tako da ne ruši celu promociju ili batch.

---

## 21. Ograničenja treninga

1. Trenutno aktivni model je označen kao SYNTHETIC_DEMO; nema osnova za tvrdnju o potvrđenoj realnoj produkcionoj tačnosti.
2. Novi automatski kandidati se čuvaju kao UNVERIFIED čak i kada prođu tehničke kapije.
3. Label meri ponašanje u 60 dana i može propustiti sezonalnost ili duže prodajne cikluse.
4. Izostanak opservacije se tumači kao HIGH samo uz capture coverage, ali i dalje ne objašnjava poslovni razlog.
5. Training skup može sadržati više vremenski odvojenih primera istog kupca; to je dozvoljeno posle 60 dana.
6. Hiperparametri nisu automatski optimizovani.
7. Ne postoji probability calibration niti confidence score u API-ju.
8. Nema ručne poslovne potvrde label-a i nema A/B merenja efekta preporuke.
9. In-memory priprema je ograničena na 50.000 snapshot-a i 500.000 opservacija; veći skup zahteva drugačiji pipeline.
10. Tehničke metrike ne dokazuju da korišćenje modela poboljšava prodaju.

---

## Trenirani model naspram ručnog pravila

| Ručno pravilo | Trenirani Decision Tree |
|---|---|
| Programer unapred bira feature i prag. | Smile bira feature i prag iz training primera. |
| Primer: recencyDays > 60 → HIGH. | Primer: model može prvo podeliti po liters60, zatim po intervalDays. |
| Nema treninga niti evaluacije. | Postoje train/evaluation split, metrike i promotion gates. |
| Promena zahteva izmenu koda. | Novi podaci mogu proizvesti novu verziju artefakta. |

U ovoj implementaciji baseline jeste ručno pravilo, ali produkciona predikcija, kada je assessment status ASSESSED, dolazi iz Smile stabla. Hardkodovani label i kapije određuju šta model treba da nauči i koliko mora da bude dobar; oni ne određuju njegove konkretne split pragove.

## Zaključak

Trening je automatski, hronološki i idempotentan. Najvažnije zaštite su point-in-time snapshot, 60-dnevno sazrevanje, capture coverage, nepreklapajući primeri, purge granica, baseline poređenje, per-class recall, poređenje sa incumbent modelom, fingerprint i transakciona promocija. Sistem je tehnički sposoban da nauči stvarno Decision Tree stablo, ali trenutni aktivni SYNTHETIC_DEMO model ne sme da se predstavlja kao dokaz validirane produkcione preciznosti.
