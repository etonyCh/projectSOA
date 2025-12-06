
## 📚 Gestion de Bibliothèque — Projet SOA (SOAP + REST)

Ce dépôt contient une application pédagogique de gestion de bibliothèque implémentée en Java. Le projet illustre une architecture hybride exposant :

- un service SOAP (pour l'administration / bibliothécaires)
- un service REST (pour les étudiants via une interface web)
- une persistance MySQL gérée par une classe `Database` maison

Ce README présente l'architecture, l'installation, le lancement, et des exemples d'API.

**Status** : code de démonstration — adapté pour étude et enrichissement.

---

## 🏗️ Architecture (rapide)

- **SOAP (JAX-WS)** : endpoints pour les actions administratives (utilisé par l'interface Swing).
- **REST (HttpServer)** : endpoints JSON pour le catalogue, emprunts, réservations, retours et profil étudiant.
- **GUI Admin** : application Java Swing (classe `com.biblio.gui.BibliothequeGUI`).
- **Frontend Étudiant** : fichiers statiques servis depuis `src/main/resources/web` (HTML/CSS/JS).
- **DB** : MySQL (schéma créé automatiquement au démarrage par `Database.initialize()`).

---

## 🚀 Fonctionnalités principales

- Recherche de livres (titre, auteur, catégorie)
- Emprunt et retour de livres
- Réservation et annulation de réservation
- Gestion des étudiants et des livres (admin via Swing)
- Consultation des pénalités de retard

---

## 🛠️ Technologies

- Java 17+
- Maven
- MySQL (JDBC)
- JAX-WS (SOAP)
- com.sun.net.httpserver (REST)
- Gson pour JSON
- Java Swing pour l'interface admin

---

## ⚙️ Prérequis

- Java 17 (JDK)
- Maven
- MySQL (local ou distant)

---

## Installation & Configuration

1. Créez la base de données MySQL (exécution unique) :

```sql
CREATE DATABASE biblio_soa;
```

2. Mettez à jour la configuration de connexion si nécessaire :

Ouvrez `src/main/java/com/biblio/data/Database.java` et modifiez les constantes :

```java
private static final String URL = "jdbc:mysql://localhost:3306/biblio_soa";
private static final String USER = "root";
private static final String PASSWORD = ""; // votre mot de passe
```

3. Construisez le projet (depuis la racine du repo) :

```powershell
mvn -DskipTests package
```

4. Lancement :

- Depuis un IDE : exécutez `com.biblio.MainApp`.
- Ou via JAR (après `mvn package`) :

```powershell
java -jar target\projet-biblio-soa-1.0-SNAPSHOT.jar
```

Au démarrage, l'application initialise la base (si besoin) et lance SOAP + REST + l'interface admin.

---

## Endpoints REST utiles (exemples)

Base : `http://localhost:9091` (ou le port configuré dans `MainApp`)

- Authentification (POST)
    - `POST /api/auth?email=...&pass=...` — retourne `{ "success": true, "id": <etudiantId> }` ou `{ "success": false }`.

- Catalogue
    - `GET /api/livres` — liste tous les livres
    - `GET /api/livres?q=mot-clé` — recherche

- Emprunts
    - `POST /api/emprunts?etudiantId=<id>&livreId=<id>` — créer un emprunt si disponible
    - `GET /api/emprunts?etudiantId=<id>` — emprunts d'un étudiant

- Réservations
    - `POST /api/reservations?etudiantId=<id>&livreId=<id>` — réserver
    - `DELETE /api/reservations?reservationId=<id>` — annuler

- Retour
    - `POST /api/return?etudiantId=<id>&livreId=<id>` — enregistre un retour

- Profil étudiant
    - `GET /api/profile?etudiantId=<id>` — retourne `{ id, nom, email }`

Remarque : les handlers REST sont dans `src/main/java/com/biblio/rest/EtudiantRestServer.java`.

---

## SOAP (admin)

WSDL accessible (après lancement) :

```
http://localhost:9090/ws/biblio?wsdl
```

Utilisez `SoapUI` ou un client JAX-WS pour tester les opérations administratives.

---

## Notes de développement & sécurité

- Mot de passe : le projet contient des points où des mots de passe sont stockés en clair. Une dépendance `org.mindrot:jbcrypt` est présente dans le `pom.xml` pour faciliter le hachage.
- Migration recommandée : utiliser BCrypt pour hacher les mots de passe (`BCrypt.hashpw`) et vérifier via `BCrypt.checkpw`. Une migration transparente peut être implémentée côté `Database.checkLogin` : tenter d'abord `checkpw`, si l'entrée ressemble à un mot de passe en clair et le compare correctement, re-hasher et sauvegarder la valeur.

---

## Tests

Exécutez les tests unitaires (si présents) :

```powershell
mvn test
```

---

## Contribution

Contributions bienvenues : ouvrez une issue ou un pull request. Pour les modifications majeures (migration DB, refactor), documentez la procédure de migration.

---

## Licence

Ce projet est fourni à des fins pédagogiques. Ajoutez ici la licence désirée (MIT, Apache-2.0, etc.).

---

## Auteur

Projet réalisé dans le cadre d'un cours SOA.

