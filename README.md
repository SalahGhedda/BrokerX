# BrokerX

Prototype monolithique pour la plateforme de courtage BrokerX. Cette iteration introduit une persistence PostgreSQL (avec repli memoire) et une interface web unique (inscription, connexion, portefeuille, ordres et suivi temps reel). La phase courante decoupe le noyau en microservices REST observes et securises.

---

## Prerequis

- Java 17 (JDK) et Maven 3.8+
- Docker (optionnel) pour lancer PostgreSQL via `docker-compose`
- Un port TCP libre (par defaut `8080`) pour l'interface web

---

## Base de donnees

1. Demarrer PostgreSQL (option recommande) :
   ```bash
   docker compose up -d
   ```
   Le fichier `docker-compose.yml` provisionne une instance locale `brokerx_db` avec l'utilisateur/mot de passe `brokerx`.

2. Variables d'environnement reconnues :
   - `BROKERX_DB_URL` (defaut `jdbc:postgresql://localhost:5432/brokerx_db`)
   - `BROKERX_DB_USER` (defaut `brokerx`)
   - `BROKERX_DB_PASSWORD` (defaut `brokerx`)
   - `BROKERX_HTTP_PORT` (defaut `8080`)
   - `BROKERX_USE_IN_MEMORY` (mettre a `true` pour forcer le mode memoire, utile sans base)

Les migrations SQL (cf. `src/main/resources/db/migration/V1__init.sql`) sont executees automatiquement au demarrage.

---

## Construction et execution

```bash
mvn clean package
java -jar target/brokerx-0.0.1-SNAPSHOT.jar
```

Une fois lancee, l'application expose l'interface web a l'adresse :

```
http://localhost:8080
```

> Conseil : laissez la console ouverte, elle affiche l'URL effective et le type de persistance choisi (PostgreSQL ou fallback memoire).

### Configuration via `.env`

Un fichier `.env.example` est fourni. Copiez-le puis ajustez les valeurs selon votre contexte :

```bash
cp .env.example .env
```

Pour lancer l'application Java depuis votre terminal, exportez les variables (exemple bash) :

```bash
export $(grep -v '^#' .env | xargs)
java -jar target/brokerx-0.0.1-SNAPSHOT.jar
```

(Sous PowerShell : `Get-Content .env | ForEach-Object { $name, $value = $_.Split('='); $env:$name = $value }`)

### Lancement via Docker

1. Demarrer la base Postgres locale :
   ```bash
   docker compose up -d
   ```
   Les variables necessaires (`BROKERX_DB_URL`, etc.) correspondent aux valeurs par defaut du `docker-compose.yml`.

2. Construire l'application :
   ```bash
   mvn clean package
   ```

3. Lancer l'application Java en important les variables du `.env` :
   ```bash
   export $(grep -v '^#' .env | xargs)
   java -jar target/brokerx-0.0.1-SNAPSHOT.jar
   ```

Optionnel : pour executer l'application dans un conteneur dedie, build puis run l'image fournie (utiliser un `.env` adapte au reseau docker, par exemple `BROKERX_DB_URL=jdbc:postgresql://brokerx-db:5432/brokerx_db`) :
```bash
docker build -t brokerx-app .
docker run --rm --name brokerx-app --env-file .env -p 8080:8080 brokerx-app
```

### Interface web

Le portail `src/main/resources/public/index.html` offre :
- un parcours d'inscription avec creation de portefeuille et activation automatique,
- une vue de connexion securisee (email + mot de passe hache),
- un tableau de bord post-authentification affichant solde, identifiant compte et statut,
- un module de depot express et un journal d'activite inline,
- une vue stock (liste, suivi, fiche detail) permettant d'acheter un titre suivi dans l'interface,
- un flux de confirmation de compte avec code OTP expose dans l'interface,
- un formulaire de saisie d'ordre (marche/limite) avec controles pre-trade elementaires,
- un flux de donnees de marche quasi temps reel via SSE et snapshots REST.

Les appels reposent sur des endpoints REST JSON exposes par le serveur HTTP embarque.

---

## Mode microservices et gateway

Cette phase introduit un packaging multi-services partageant la meme base. Chaque processus demarre `ServiceLauncher` avec le type de microservice souhaite :

```powershell
mvn clean package
$jar = "target/brokerx-0.0.1-SNAPSHOT.jar"

$env:BROKERX_DB_URL = "jdbc:postgresql://localhost:5432/brokerx_db"
$env:BROKERX_DB_USER = "brokerx"
$env:BROKERX_DB_PASSWORD = "brokerx"

$env:BROKERX_SERVICE = "ORDERS";     $env:BROKERX_HTTP_PORT = "8101"; java -cp $jar com.brokerx.microservices.ServiceLauncher
$env:BROKERX_SERVICE = "PORTFOLIO";  $env:BROKERX_HTTP_PORT = "8201"; java -cp $jar com.brokerx.microservices.ServiceLauncher
$env:BROKERX_SERVICE = "MARKETDATA"; $env:BROKERX_HTTP_PORT = "8301"; java -cp $jar com.brokerx.microservices.ServiceLauncher
$env:BROKERX_SERVICE = "REPORTING";  $env:BROKERX_HTTP_PORT = "8401"; java -cp $jar com.brokerx.microservices.ServiceLauncher
```

Parametres cles :
- `BROKERX_SERVICE` : `ORDERS`, `PORTFOLIO`, `MARKETDATA` ou `REPORTING` (defaut `ORDERS`).
- `BROKERX_HTTP_PORT` : port HTTP expose par le microservice (defaut `8090`).
- `BROKERX_REQUIRE_TOKEN=true` force l'authentification Bearer; utilisez `POST /api/v1/auth/login` du serveur monolithique pour recuperer un jeton valable 4 h.

Une configuration KrakenD est disponible sous `infra/gateway/krakend.json`. Elle agrandit les microservices derriere un endpoint unique `http://localhost:9000/api/*`, applique CORS, rate limiting et un circuit breaker sur le reporting. Adaptez les noms d'hotes (`orders-svc`, `orders-svc-2`, etc.) a votre orchestration (localhost, Docker, Kubernetes). Relancez KrakenD en cas de modification pour recharger la configuration.

Scenarios a valider :
1. Appel direct vs via gateway (`curl http://localhost:8101/orders` vs `curl http://localhost:9000/api/orders`).
2. Scalabilite horizontale : demarrer une seconde instance (`BROKERX_HTTP_PORT=8102`) et verifier la distribution du trafic via les metriques.
3. Tolerance aux pannes : stopper une instance pendant un test de charge et verifier que le gateway degrade proprement le trafic restant.

---

## Tests

```bash
mvn clean test
```

Les tests couvrent :
- la logique metier en memoire (tests unitaires),
- un test d'integration JDBC (`JdbcPersistenceIntegrationTest`) sur une base H2 configuree en mode PostgreSQL,
- `OrderServiceTest` validant controles pre-trade, idempotence et reservations.

---

## Points clefs

- Architecture hexagonale legere (ports/adapters).
- Adapters JDBC bases sur HikariCP (`AccountRepositoryJdbc`, `WalletRepositoryJdbc`, `TransactionRepositoryJdbc`).
- Repli in-memory disponible pour prototyper sans base.
- Interface HTTP compacte via `com.sun.net.httpserver.HttpServer` (REST + SSE).
- Orchestrations metier exposees via `WalletService`, `OrderService`, `MarketDataService`.

### Observabilite et metriques

- Logs structures (`StructuredLogger`) avec correlation minimaliste par requete.
- Endpoint Prometheus `http://localhost:8080/metrics` et `/metrics` sur chaque microservice: compteurs `brokerx_http_*`, `brokerx_orders_total`, `brokerx_wallet_deposits_total` plus metriques JVM.
- Tableaux Grafana : `observability/grafana/golden-signals.json` couvre p95/p99, RPS, erreurs et saturation CPU/RSS.
- Tracing manuel via les evenements `order_event`, `wallet_deposit`, `wallet_balance_*`.

### Caching applicatif

- `StockService` met en cache les quotes (TTL ~1 s) pour les listes globales, suivis par compte et fiche detail.
- Invalidations sur `follow` / `unfollow` et apres executions d'ordres pour eviter le stale majeur.
- Les snapshots marches sont partages entre endpoints REST et SSE pour reduire la charge MarketData/DB.

### Tests de charge et equilibrage

- Scenarios k6 dans `load-testing/` :
  - `k6-orders.js` : navigation + ordres (stages progressifs).
  - `k6-stocks.js` : lecture intensive (stocks / notifications).
  - Exemple : `k6 run load-testing/k6-orders.js --env BASE_URL=http://nginx/api/v1 --env ACCOUNT_ID=... --env TOKEN=...`.
- Exemple edge caching + load balancing : `infra/nginx/brokerx.conf` (least_conn + micro-cache, bypass sur `/accounts/*/orders`).
- Mesurez latence p95/p99, RPS, erreurs, charge CPU/RAM avant/apres cache et avant/apres montee en echelle (1 -> 4 instances).

### API et clients

- Specification OpenAPI : `docs/api/openapi.yaml`.
- Collection Postman : `docs/api/postman_collection.json` (login -> summary -> ordre -> cancel).
