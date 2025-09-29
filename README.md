# BrokerX

## Prérequis

- **Java 17+** (JDK)
- **Maven 3.8+**
- (Optionnel) **Docker** pour l'exécution conteneurisée
- (Optionnel) Accès au **runner self-hosted** si vous voulez déployer via GitHub Actions

---

## Installation & Build

### 1. Cloner le projet
```bash
git clone https://github.com/SalahGhedda/BrokerX.git
cd BrokerX
```

### 2. Compiler & packager
```bash
mvn clean package -DskipTests
```

---

## Lancer les tests

Pour exécuter tous les tests (unitaires et intégration) :
```bash
mvn clean verify
```
---

## Exécuter l'application

### 1. En local (Java direct)
```bash
java -cp target/classes com.brokerx.bootstrap.Application
```
```
```
### 2. Via JAR exécutable
```bash
java -jar target/brokerx-0.0.1-SNAPSHOT.jar
```

---

### 3. Via Docker
Construire l'image :
```bash
docker build -t brokerx:latest .
```

Lancer le container :
```bash
docker run --rm brokerx:latest
```

---