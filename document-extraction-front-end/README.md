<div align="center">
  <img src="public/favicon.svg" width="76" alt="Document Extractor" />
  <h1>Document Extractor — Frontend</h1>
  <p><strong>Espace de travail React pour l'extraction documentaire OCR français, anglais et arabe.</strong></p>

  ![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=white)
  ![TypeScript](https://img.shields.io/badge/TypeScript-5.5-3178C6?logo=typescript&logoColor=white)
  ![Vite](https://img.shields.io/badge/Vite-5-646CFF?logo=vite&logoColor=white)
  ![Tests](https://img.shields.io/badge/tests-passing-2EA043)
  ![No LLM](https://img.shields.io/badge/LLM-none-6B7280)

  <p>
    <a href="#français">Français</a> ·
    <a href="#english">English</a>
  </p>
</div>

---

## Français

### Présentation

Document Extractor est le frontend d'une API d'extraction documentaire
**on-premise**. L'utilisateur se connecte à son organisation, dépose un PDF ou une
image, choisit les langues OCR, suit le traitement asynchrone, puis consulte,
recherche, copie et télécharge le texte extrait.

Le front ne contient **aucun modèle d'IA, aucun LLM et aucun appel à un service
d'IA générative**. L'OCR est exécuté par l'API Spring Boot auto-hébergée, avec
Tesseract et des données linguistiques locales.

### Fonctionnalités V1

- **Authentification** : écran de connexion, JWT, session persistée, déconnexion,
  changement de mot de passe et expiration/401 gérée automatiquement.
- **Rôles** : `ADMIN`, `REVIEWER`, `USER` ; le rôle est affiché dans le menu
  utilisateur.
- **Gestion des utilisateurs** : page d’administration pour créer des comptes,
  attribuer un rôle et activer/désactiver un utilisateur.
- **Historique serveur** : liste paginée chargée depuis `/documents`, recherche
  par nom ou contenu, filtre par statut, suppression et vidage.
- **Upload** : PDF, PNG, JPG/JPEG, TIFF, BMP, WEBP ; limite de 50 Mo ; validation
  côté client et messages `ProblemDetail` explicites.
- **Langues OCR** : français, anglais et arabe ; sélection multiple, préférence
  mémorisée, interface RTL complète.
- **Traitement asynchrone** : polling toutes les 1,5 s, états `PENDING`,
  `PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED`, annulation.
- **Aperçu authentifié** : le fichier source est récupéré depuis l'API avec le
  JWT, puis affiché dans le navigateur ; l'aperçu reste donc disponible après
  réouverture depuis l'historique.
- **Résultat en vue scindée** : aperçu PDF/image avec zoom et pagination, texte
  complet ou par page, badge `NATIVE` / `OCR`, confiance OCR.
- **Édition et export** : texte éditable, recherche avec surlignage, copie,
  téléchargement `.txt` et `.json`.
- **Thème clair/sombre**, responsive mobile, tablette et desktop.
- **Accessibilité** : navigation clavier, ARIA, focus visible, zones `aria-live`.

### IA et technologies

Le frontend **n'exécute aucune IA** : l'IA d'OCR **Tesseract 5 (LSTM)** est
exécutée côté API via **Tess4J 5.13.0**. L'interface consomme les résultats et les
champs traçables, sans appel à un LLM ni à un service cloud.

| Technologie | Version | Usage |
| --- | --- | --- |
| React | 18.3 | Interface de revue |
| TypeScript | 5.5 | Typage strict |
| Vite | 5.4 | Build et serveur de développement |
| TanStack Query / Axios | 5 / 1.20 | Données serveur et appels API |
| react-pdf / pdf.js | 7.7 | Aperçu PDF et surlignage |
| Tailwind CSS / shadcn/ui | 3 / Radix UI | Design system |
| i18next | 26 | Français, anglais, arabe (RTL) |
| Vitest / Playwright | 2.1 / 1.64 | Tests unitaires et E2E |

### Durcissement V1.1

- **Profil** : modification du nom d'affichage depuis le menu du compte.
- **Réinitialisation admin** : un administrateur peut définir un nouveau mot de
  passe pour un utilisateur.
- **Politique de mot de passe** : 12 à 128 caractères, contrôlée dans l'interface
  et par l'API.
- **Révocation de session** : après changement de son propre mot de passe, le
  front efface le token et revient à l'écran de connexion ; les anciens JWT sont
  refusés par l'API.
- **Historique progressif** : pagination serveur par pages de 50 avec bouton
  « Charger plus » et conservation des filtres.
- **Aperçu TIFF** : repli explicite lorsque le navigateur ne peut pas rendre le
  TIFF, avec téléchargement du fichier original ; l'OCR reste disponible.
- **Validation E2E** : parcours Playwright connexion/upload/résultat/historique,
  administration et révocation de session.

### Revue des champs et traçabilité

- **Onglet Champs** dans le panneau de résultat : libellé, valeur brute, valeur
  effective, statut `VERIFIED` / `UNVERIFIED` / `AMBIGUOUS` et badge « corrigé ».
- **Source vérifiable** : bouton vers la page source et surlignage de la bbox
  normalisée sur l'aperçu, sans reconstruction de coordonnées côté client.
- **Correction** : dialogue avec motif, historique append-only et valeur effective
  mise à jour ; la valeur brute n'est jamais réécrite.
- **Exports serveur** : les téléchargements TXT/JSON utilisent
  `GET /extractions/{id}/download` et incluent les valeurs effectives ; l'UI ne
  reconstruit plus les fichiers localement.
- **Bidi** : les valeurs utilisent `dir="auto"`, les chiffres arabes/persans sont
  affichés tels quels et la normalisation reste côté API.
- **Tests** : Vitest/MSW pour les champs, corrections, historique et surlignage ;
  parcours Playwright pour la revue et la persistance après réouverture.

### Architecture

```text
Navigateur
  └─ React 18 + TanStack Query
       ├─ AuthProvider            (JWT, /auth/me, /auth/login, /auth/logout)
       ├─ PATCH  /api/v1/auth/me                  (nom d'affichage)
       ├─ POST   /api/v1/auth/password            (changement de mot de passe)
       ├─ GET    /api/v1/users                    (comptes, admin)
       ├─ POST   /api/v1/users                    (création de compte, admin)
       ├─ POST   /api/v1/users/{id}/password      (réinitialisation, admin)
       ├─ POST   /api/v1/extractions              (upload multipart, 202)
       ├─ GET    /api/v1/extractions/{id}         (polling)
       ├─ GET    /api/v1/extractions/{id}/file    (aperçu authentifié)
       ├─ GET    /api/v1/extractions/{id}/fields  (champs, sources, corrections)
       ├─ POST   /api/v1/extractions/{id}/fields/{key}/corrections (correction)
       ├─ GET    /api/v1/extractions/{id}/download (export TXT/JSON serveur)
       ├─ GET    /api/v1/documents                (historique serveur)
       └─ DELETE /api/v1/extractions/{id}         (annulation/suppression)
```

Le front est séparé de l'API et peut être servi par nginx, qui proxyfie
`/api/v1/` vers le backend Spring Boot.

### Stack technique

| Domaine | Choix |
| --- | --- |
| UI | React 18, TypeScript, Vite 5 |
| Styles | Tailwind CSS 3, shadcn/ui (Radix UI), lucide-react |
| Données | TanStack Query 5, Axios |
| Routage | React Router 7 |
| PDF | react-pdf / pdf.js, worker bundlé par Vite |
| i18n | react-i18next, français / anglais / arabe (RTL) |
| Tests | Vitest, React Testing Library, MSW |
| Qualité | ESLint, Prettier, TypeScript strict |

### Démarrage rapide

#### Prérequis

- Node.js **22+** et npm **10+**
- L'API `document-extraction-api` démarrée sur `http://localhost:18080` (Docker)
  ou une autre URL via `VITE_DEV_API_PROXY`

#### Installation

```bash
npm install
cp .env.example .env
npm run dev
```

PowerShell :

```powershell
Copy-Item .env.example .env
npm.cmd run dev
```

Ouvrir `http://localhost:5173`. Le proxy Vite redirige `/api/v1` vers
`http://localhost:18080` par défaut ; définissez `VITE_DEV_API_PROXY` pour cibler
un backend local sur `http://localhost:8080`.

Au premier lancement du backend, utilisez le compte administrateur créé par
l'API :

| Paramètre | Valeur par défaut |
| --- | --- |
| Email | `admin@local.test` |
| Mot de passe | `ChangeMe123!` |

> Changez ce mot de passe côté backend avant toute mise en production.

#### Backend

```bash
# dans le dépôt backend
docker compose up --build -d
```

Pour un lancement local sans Docker, voir le README backend (H2 + stockage
disque).

### Configuration

| Variable | Défaut | Description |
| --- | --- | --- |
| `VITE_API_URL` | `/api/v1` | URL de base de l'API. En développement, le proxy Vite redirige `/api/v1` vers `http://localhost:18080` (modifiable via `VITE_DEV_API_PROXY`). En production derrière nginx : `/api/v1`. |

> `VITE_API_URL` est injectée **au moment du build** par Vite. Pour Docker :
> `docker build --build-arg VITE_API_URL=/api/v1 .`.

### Scripts

| Commande | Rôle |
| --- | --- |
| `npm run dev` | Serveur de développement Vite |
| `npm run typecheck` | Vérification TypeScript sans émission |
| `npm run typecheck:e2e` | Vérification TypeScript des tests Playwright |
| `npm run lint` | Analyse ESLint |
| `npm test` | Tests Vitest en mode CI |
| `npm run test:watch` | Tests en mode interactif |
| `npm run build` | Build de production (`tsc -b` + Vite) |
| `npm run preview` | Prévisualisation du build |
| `npm run test:e2e` | Parcours Playwright sur la stack Docker |
| `npm run test:e2e:ui` | Mode interactif Playwright |

### Session et stockage navigateur

| Clé | Contenu |
| --- | --- |
| `docx.auth.token` | JWT de session |
| `docx.languages` | Langues OCR sélectionnées |
| `docx.theme` | `light` ou `dark` |
| `docx.lang` | `fr`, `en` ou `ar` |

L'historique n'est **plus stocké dans le navigateur** : il vient de l'API
(`/documents`) et respecte les droits de l'organisation. Une réponse `401`
efface le token local et renvoie vers l'écran de connexion.

### Parcours utilisateur

1. **Connexion** : email et mot de passe fournis par l'administrateur ; le mot de
   passe peut être changé depuis le menu du compte.
2. **Administration** : un `ADMIN` crée les comptes, attribue les rôles et
   active/désactive les utilisateurs depuis `/admin/users`.
3. **Accueil** : déposer un fichier, vérifier le nom et la taille, choisir les
   langues OCR, lancer l'extraction.
4. **Traitement** : statut en temps réel, progression et annulation.
5. **Résultat** : comparer l'aperçu et le texte, rechercher, corriger, copier ou
   télécharger.
6. **Historique** : rouvrir un document, y compris son aperçu source, ou le
   supprimer.

### Intégration API

Le client Axios utilise un timeout de **120 s**, aligné sur le proxy nginx. Les
erreurs sont normalisées dans un objet `ApiError` unique :

- erreur réseau ;
- `ProblemDetail` Spring avec `detail` ;
- PDF protégé par mot de passe ;
- fichier trop volumineux ;
- session expirée.

Le polling s'arrête dès qu'un statut terminal est reçu. La suppression d'une
extraction en cours appelle `DELETE`, puis réinitialise l'écran.

### Tests et qualité

```bash
npm run typecheck
npm run typecheck:e2e
npm run lint
npm test
npm run build
```

La suite couvre les erreurs d'upload, le polling, la recherche, le rendu du
résultat, la navigation, la connexion, le profil, le repli TIFF et les composants
critiques. Le build vérifie aussi l'émission du worker pdf.js dans `dist/assets/`.

Les tests E2E nécessitent la stack Compose backend démarrée (frontend sur
`http://localhost:8081` par défaut) :

```bash
npm run test:e2e
```

Ils lisent `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` depuis
`../document-extraction-api/.env`, ou `E2E_ADMIN_EMAIL` / `E2E_ADMIN_PASSWORD`.
`E2E_BASE_URL` permet de cibler une autre URL. Les parcours couvrent
connexion/upload/OCR/aperçu/historique, création et réinitialisation d'un compte,
puis modification du profil et révocation de session.

### Docker

Pour la stack complète (frontend + API + PostgreSQL + MinIO + TLS optionnel),
utilisez le `docker-compose.yml` du dépôt backend et laissez `FRONTEND_CONTEXT`
pointer vers ce dépôt. Voir le README backend et `DEPLOYMENT.md`.

Pour construire uniquement l'image frontend :

```bash
docker build -t document-extractor-web .
docker run --rm -p 8081:80 document-extractor-web
```

Par défaut, nginx sert l'application et proxy `/api/v1/` vers
`http://backend:8080/api/v1/`. Pour un déploiement réel :

1. placez le conteneur front et l'API sur le même réseau Docker ;
2. nommez le service backend `backend`, ou adaptez `nginx.conf` ;
3. terminez TLS par un reverse proxy (Traefik, Caddy, nginx externe) ;
4. fixez `CORS_ALLOWED_ORIGIN` côté API à l'origine publique du front.

Exemple avec un réseau partagé :

```bash
docker network create document-extractor
docker run -d --name backend --network document-extractor \
  -p 8080:8080 document-extraction-api
docker run -d --name web --network document-extractor -p 8081:80 \
  document-extractor-web
```

### Accessibilité et i18n

- Interface disponible en **français**, **anglais** et **arabe** avec passage
  automatique en RTL (`dir="rtl"`).
- Les textes extraits utilisent `dir="auto"` pour respecter la direction de la
  langue détectée, même si l'interface est en français.
- Navigation clavier, focus visible, rôles ARIA et messages de statut annoncés
  via `aria-live`.
- Aucune police externe n'est chargée : l'interface utilise la pile système et
  reste lisible hors ligne.

### Confidentialité et limites

- **Aucun LLM, aucun modèle de vision, aucun service cloud** dans le front.
- Le fichier est envoyé uniquement à l'API configurée. L'API le conserve selon la
  politique de rétention de l'organisation (30 jours par défaut).
- L'aperçu source est récupéré de façon authentifiée et n'est pas mis en cache
  longue durée dans le navigateur.
- La qualité OCR dépend de la netteté, de la résolution et de la langue du
  document. L'écriture manuscrite reste une limite connue de Tesseract.
- Les TIFF sont acceptés par l'OCR ; l'aperçu inline dépend du navigateur. Un
  message de repli et le téléchargement du fichier original sont fournis.
- L'historique est partagé au niveau de l'organisation pour les rôles
  `ADMIN`/`REVIEWER`.
- **Traçabilité** : l'interface affiche uniquement les champs extraits par l'API ;
  seuls `reference_number` et `document_date` sont disponibles ; les surlignages
  sont au niveau ligne ; aucune IA ni `pgvector`.

### Structure du projet

```text
src/
  api/                  client Axios, auth, types, normalisation des erreurs
  components/ui/        composants shadcn/ui
  features/auth/        AuthProvider, LoginPage, ProtectedRoute, changement de mot de passe
  features/users/       administration des comptes et des rôles
  features/extraction/  pages, composants, hooks du parcours principal
  hooks/                hooks génériques
  i18n/                 configuration et traductions fr / en / ar
  lib/                  utilitaires
  test/                 configuration Vitest et handlers MSW
  utils/                validation, stockage, recherche, téléchargement
e2e/                    parcours Playwright de bout en bout
playwright.config.ts    URL, credentials et navigateur E2E
```

### Feuille de route

- Couche IA optionnelle via `FieldRule`, avec source vérifiable obligatoire.
- SSO, facturation et quotas par organisation.
- Export PDF avec couche texte.
- Segmentation de tableaux et reconstruction de l'ordre de lecture.
- Amélioration continue du prétraitement OCR côté API.

### Licence

Ce projet est distribué sous **licence propriétaire DocuScan — tous droits
réservés**. Toute reproduction, modification, distribution ou utilisation
commerciale nécessite une autorisation écrite préalable. Voir le fichier
[`../LICENSE`](../LICENSE).

---

## English

### Overview

Document Extractor is the frontend of an **on-premise** document extraction API.
Users sign in to their organization, upload a PDF or image, choose OCR languages,
follow asynchronous processing, then review, search, copy and download the
extracted text.

The frontend contains **no AI model, no LLM and no generative-AI service call**.
OCR runs in the self-hosted Spring Boot API using Tesseract and local language
data.

### V1 features

- **Authentication**: login screen, JWT, persisted session, logout, password
  change and automatic 401/expiry handling.
- **Roles**: `ADMIN`, `REVIEWER`, `USER`; the role is shown in the user menu.
- **User management**: admin page to create accounts, assign roles and
  enable/disable users.
- **Server history**: paginated `/documents` list, search by name or content,
  status filter, delete and clear.
- **Upload**: PDF, PNG, JPG/JPEG, TIFF, BMP, WEBP; 50 MB limit; client validation
  and explicit `ProblemDetail` messages.
- **OCR languages**: French, English and Arabic; multi-select, remembered
  preference, full RTL interface.
- **Asynchronous processing**: 1.5 s polling, `PENDING`, `PROCESSING`,
  `COMPLETED`, `FAILED`, `CANCELLED` states, cancellation.
- **Authenticated preview**: the source file is fetched from the API with the
  JWT, so preview remains available after reopening from history.
- **Split result view**: PDF/image preview with zoom and pagination, full or
  per-page text, `NATIVE` / `OCR` badge, OCR confidence.
- **Editing and export**: editable text, search highlighting, copy, `.txt` and
  `.json` downloads.
- **Light/dark theme**, responsive mobile, tablet and desktop layouts.
- **Accessibility**: keyboard navigation, ARIA, visible focus, `aria-live`
  regions.

### AI and technologies

The frontend **runs no AI**: the **Tesseract 5 (LSTM)** OCR AI runs in the API
through **Tess4J 5.13.0**. The UI consumes results and traceable fields without
calling an LLM or a cloud service.

| Technology | Version | Purpose |
| --- | --- | --- |
| React | 18.3 | Review UI |
| TypeScript | 5.5 | Strict typing |
| Vite | 5.4 | Build and dev server |
| TanStack Query / Axios | 5 / 1.20 | Server data and API calls |
| react-pdf / pdf.js | 7.7 | PDF preview and highlighting |
| Tailwind CSS / shadcn/ui | 3 / Radix UI | Design system |
| i18next | 26 | French, English, Arabic (RTL) |
| Vitest / Playwright | 2.1 / 1.64 | Unit and E2E tests |

### V1.1 hardening

- **Profile**: edit the display name from the account menu.
- **Admin reset**: an administrator can set a new password for a user.
- **Password policy**: 12–128 characters, enforced by the UI and the API.
- **Session revocation**: after changing their own password, the frontend clears
  the token and returns to login; the API rejects old JWTs.
- **Progressive history**: server pagination in pages of 50 with a “Load more”
  action that preserves filters.
- **TIFF preview**: an explicit fallback when the browser cannot render TIFF, with
  original-file download; OCR remains available.
- **E2E validation**: Playwright flows for login/upload/result/history, user
  administration and session revocation.

### Field review and traceability

- **Fields tab** in the result panel: label, raw value, effective value,
  `VERIFIED` / `UNVERIFIED` / `AMBIGUOUS` status and a “corrected” badge.
- **Verifiable source**: a button jumps to the source page and highlights the
  normalized bbox on the preview, without rebuilding coordinates on the client.
- **Correction**: dialog with a reason, append-only history and an updated
  effective value; the raw value is never rewritten.
- **Server exports**: TXT/JSON downloads use
  `GET /extractions/{id}/download` and include effective values; the UI no longer
  rebuilds files locally.
- **Bidi**: values use `dir="auto"`, Arabic-Indic/Persian digits are displayed
  as-is and normalization stays on the API side.
- **Tests**: Vitest/MSW for fields, corrections, history and highlighting;
  Playwright flow for review and persistence after reopening.

### Architecture

```text
Browser
  └─ React 18 + TanStack Query
       ├─ AuthProvider            (JWT, /auth/me, /auth/login, /auth/logout)
       ├─ PATCH  /api/v1/auth/me                  (display name)
       ├─ POST   /api/v1/auth/password            (password change)
       ├─ GET    /api/v1/users                    (admin accounts)
       ├─ POST   /api/v1/users                    (admin account creation)
       ├─ POST   /api/v1/users/{id}/password      (admin reset)
       ├─ POST   /api/v1/extractions              (multipart upload, 202)
       ├─ GET    /api/v1/extractions/{id}         (polling)
       ├─ GET    /api/v1/extractions/{id}/file    (authenticated preview)
       ├─ GET    /api/v1/extractions/{id}/fields  (fields, sources, corrections)
       ├─ POST   /api/v1/extractions/{id}/fields/{key}/corrections (correction)
       ├─ GET    /api/v1/extractions/{id}/download (server TXT/JSON export)
       ├─ GET    /api/v1/documents                (server history)
       └─ DELETE /api/v1/extractions/{id}         (cancel/delete)
```

The frontend is separate from the API and can be served by nginx, proxying
`/api/v1/` to the Spring Boot backend.

### Quick start

Requirements: Node.js **22+**, npm **10+**, and the `document-extraction-api`
running on `http://localhost:18080` (Docker), or another URL via
`VITE_DEV_API_PROXY`.

```bash
npm install
cp .env.example .env
npm run dev
```

PowerShell:

```powershell
Copy-Item .env.example .env
npm.cmd run dev
```

Open `http://localhost:5173`. The Vite proxy forwards `/api/v1` to
`http://localhost:18080` by default; set `VITE_DEV_API_PROXY` to target a local
backend on `http://localhost:8080`. On first backend start, use:

| Setting | Default |
| --- | --- |
| Email | `admin@local.test` |
| Password | `ChangeMe123!` |

> Change this password on the backend before production.

### Configuration

| Variable | Default | Description |
| --- | --- | --- |
| `VITE_API_URL` | `/api/v1` | API base URL. In development, the Vite proxy forwards `/api/v1` to `http://localhost:18080` (override with `VITE_DEV_API_PROXY`). Production behind nginx: `/api/v1`. |

> Vite injects `VITE_API_URL` **at build time**. For Docker:
> `docker build --build-arg VITE_API_URL=/api/v1 .`.

### Scripts

| Command | Purpose |
| --- | --- |
| `npm run dev` | Vite development server |
| `npm run typecheck` | TypeScript check without emit |
| `npm run typecheck:e2e` | TypeScript check for the Playwright tests |
| `npm run lint` | ESLint |
| `npm test` | Vitest in CI mode |
| `npm run test:watch` | Interactive test mode |
| `npm run build` | Production build (`tsc -b` + Vite) |
| `npm run preview` | Preview the production build |
| `npm run test:e2e` | Playwright flows against the Docker stack |
| `npm run test:e2e:ui` | Interactive Playwright mode |

### Session and browser storage

| Key | Content |
| --- | --- |
| `docx.auth.token` | Session JWT |
| `docx.languages` | Selected OCR languages |
| `docx.theme` | `light` or `dark` |
| `docx.lang` | `fr`, `en` or `ar` |

History is **no longer stored in the browser**: it comes from the API
(`/documents`) and follows organization permissions. A `401` response clears the
local token and redirects to login.

### User journey

1. **Login**: email and password provided by the administrator; the password can
   be changed from the account menu.
2. **Administration**: an `ADMIN` creates accounts, assigns roles and
   enables/disables users from `/admin/users`.
3. **Home**: drop a file, check its name and size, choose OCR languages, start
   extraction.
4. **Processing**: live status, progress and cancellation.
5. **Result**: compare preview and text, search, correct, copy or download.
6. **History**: reopen a document, including its source preview, or delete it.

### API integration

The Axios client uses a **120 s** timeout, aligned with the nginx proxy. Errors
are normalized into one `ApiError` shape:

- network error;
- Spring `ProblemDetail` with `detail`;
- password-protected PDF;
- file too large;
- expired session.

Polling stops on a terminal status. Deleting a running extraction calls
`DELETE`, then resets the screen.

### Tests and quality

```bash
npm run typecheck
npm run typecheck:e2e
npm run lint
npm test
npm run build
```

The suite covers upload errors, polling, search, result rendering, navigation,
login, profile, TIFF fallback and critical components. The build also verifies
that the pdf.js worker is emitted into `dist/assets/`.

E2E tests require the backend Compose stack (frontend on `http://localhost:8081`
by default):

```bash
npm run test:e2e
```

They read `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` from
`../document-extraction-api/.env`, or `E2E_ADMIN_EMAIL` / `E2E_ADMIN_PASSWORD`.
`E2E_BASE_URL` targets another URL. Flows cover login/upload/OCR/preview/history,
account creation and reset, then profile update and session revocation.

### Docker

For the full stack (frontend + API + PostgreSQL + MinIO + optional TLS), use the
Compose file in the backend repository and point `FRONTEND_CONTEXT` at this
checkout. See the backend README and `DEPLOYMENT.md`.

To build only the frontend image:

```bash
docker build -t document-extractor-web .
docker run --rm -p 8081:80 document-extractor-web
```

By default, nginx serves the app and proxies `/api/v1/` to
`http://backend:8080/api/v1/`. For a real deployment:

1. put the frontend and API containers on the same Docker network;
2. name the backend service `backend`, or adapt `nginx.conf`;
3. terminate TLS with a reverse proxy (Traefik, Caddy, external nginx);
4. set the API `CORS_ALLOWED_ORIGIN` to the public frontend origin.

Shared-network example:

```bash
docker network create document-extractor
docker run -d --name backend --network document-extractor \
  -p 8080:8080 document-extraction-api
docker run -d --name web --network document-extractor -p 8081:80 \
  document-extractor-web
```

### Accessibility and i18n

- UI available in **French**, **English** and **Arabic**, with automatic RTL
  (`dir="rtl"`).
- Extracted text uses `dir="auto"` so its direction follows the detected
  language, even when the UI is French.
- Keyboard navigation, visible focus, ARIA roles and status messages announced
  through `aria-live`.
- No external fonts are loaded: the UI uses the system font stack and remains
  readable offline.

### Privacy and limitations

- **No LLM, no vision model, no cloud service** in the frontend.
- The file is sent only to the configured API. The API retains it according to
  the organization retention policy (30 days by default).
- The source preview is fetched with authentication and is not cached long-term in
  the browser.
- OCR quality depends on sharpness, resolution and document language. Handwriting
  remains a known limitation of Tesseract.
- TIFF files are accepted by OCR; inline preview depends on the browser. A fallback
  message and original-file download are provided.
- History is shared at organization level for `ADMIN`/`REVIEWER` roles.
- **Traceability**: the UI only displays fields extracted by the API; only
  `reference_number` and `document_date` are available; highlights are
  line-level; no AI or `pgvector`.

### Project structure

```text
src/
  api/                  Axios client, auth, types, error normalization
  components/ui/        shadcn/ui components
  features/auth/        AuthProvider, LoginPage, ProtectedRoute, password change
  features/users/       account and role administration
  features/extraction/  pages, components and hooks for the main flow
  hooks/                generic hooks
  i18n/                 configuration and fr / en / ar translations
  lib/                  utilities
  test/                 Vitest setup and MSW handlers
  utils/                validation, storage, search, download
e2e/                    Playwright end-to-end flows
playwright.config.ts    E2E URL, credentials and browser settings
```

### Roadmap

- Optional AI layer through `FieldRule`, with a mandatory verifiable source.
- SSO, billing and per-organization quotas.
- PDF export with a text layer.
- Table segmentation and reading-order reconstruction.
- Continued OCR preprocessing improvements on the API side.

### License

This project is distributed under the **DocuScan proprietary license — all
rights reserved**. Any reproduction, modification, distribution or commercial
use requires prior written permission. See the [`../LICENSE`](../LICENSE) file.
