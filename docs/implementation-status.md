# État d'implémentation — traçabilité des champs

- Branche : `DocuScan-AI`
- Dernière vérification : 2026-11-10
- Périmètre : traçabilité déterministe des champs, complétée par une extraction IA locale optionnelle (Ollama), sans `pgvector`

## Résumé

Toutes les exigences fonctionnelles de cette tranche sont implémentées et
vérifiées localement. Les éléments restants relèvent soit de la validation CI
finale (job Docker/PostgreSQL/MinIO), soit d'itérations futures explicitement
hors périmètre.

## IA intégrée dans cette version

Deux usages de l'IA, tous deux **locaux** :

- **OCR** : **Tesseract 5**, moteur à réseaux de neurones **LSTM**, intégré via
  **Tess4J 5.13.0** (langues `fra`, `eng`, `ara`), pour les PDF scannés et les
  images ;
- **Extraction de champs** : **Ollama** avec `llama3.2:1b`, utilisé uniquement
  pour les clés absentes du résultat déterministe. L'option est désactivée par
  défaut (`OLLAMA_ENABLED=false`).

Aucun appel cloud, aucun embedding et aucun `pgvector`.

Les champs `reference_number` et `document_date` restent extraits **d'abord** par
des règles déterministes. Une valeur Ollama n'est acceptée que si elle apparaît
verbatim dans le texte, sur une ligne avec libellé reconnu ou seule ; sinon elle
est ignorée ou marquée `UNVERIFIED`. Aucune géométrie n'est inventée.

| Technologie | Version | Usage |
| --- | --- | --- |
| Tesseract 5 (LSTM) | Tess4J 5.13.0 | OCR local français, anglais et arabe |
| Apache PDFBox | 3.0.5 | Texte natif, rendu et positions des lignes |
| Ollama | 0.35.1 (testé) | Extraction IA locale optionnelle des champs manquants |
| llama3.2:1b | — | Modèle local par défaut |
| Spring Boot / Java | 3.3.13 / 21 | API, worker et sécurité |
| PostgreSQL 16 / H2 | — | Production et tests |
| Flyway | — | Migrations additives |
| React / TypeScript / Vite | 18.3 / 5.5 / 5.4 | Interface de revue |
| Vitest / Playwright | 2.1 / 1.64 | Tests unitaires et E2E |

## Matrice de vérification

| # | Exigence | État | Preuve |
| --- | --- | --- | --- |
| 1 | Travailler sur la branche existante `DocuScan-AI` | ✅ | Branche unique, historique dédié (dont le commit Ollama `ce06006`) |
| 2 | IA générative locale optionnelle, sans cloud ni `pgvector` | ✅ | Ollama `llama3.2:1b` désactivé par défaut ; aucun appel cloud, embedding ou dépendance vectorielle |
| 3 | Migrations Flyway additives | ✅ | `V3__field_traceability.sql` : colonnes et tables ajoutées, aucune colonne réécrite |
| 4 | Compatibilité TXT/JSON existante | ✅ | JSON additif (`schema_version`, `fields`) ; TXT conserve le texte puis ajoute la section des champs |
| 5 | Isolation par organisation et règles `ADMIN`/`REVIEWER`/`USER` | ✅ | Requêtes filtrées par `organization_id` ; accès vérifié avant lecture et correction |
| 6 | Valeurs brute, normalisée et effective séparées | ✅ | `raw_value`, `normalized_value`, valeur effective calculée dans `FieldService` |
| 7 | Corrections append-only | ✅ | `field_corrections` : historique complet, `superseded_at`, dernier correctif actif |
| 8 | Corrections appliquées à toute la lignée | ✅ | Résolution par `lineage_id` + `field_key` ; test de re-extraction |
| 9 | Chiffres arabes/persans, ordre logique et bidi | ✅ | Valeur brute préservée ; repli des chiffres réservé à la comparaison ; `dir="auto"` |
| 10 | Aucun champ inventé ; source fiable ou `UNVERIFIED` | ✅ | Règles et extracteur Ollama mappent la valeur à un span existant, sinon `UNVERIFIED` ou rejet |
| 11 | Dates ambiguës jamais devinées | ✅ | `DocumentDateRule` retourne `AMBIGUOUS` si l'ordre ou le calendrier n'est pas prouvé |
| 12 | Deux chemins d'export mis à jour | ✅ | `ExtractionController` et ZIP `DocumentController` utilisent `ExtractionExportService` |
| 13 | Revue dans l'interface | ✅ | Onglet **Champs**, statuts, source, surlignage, correction et historique |
| 14 | Aucune valeur de champ dans les logs | ✅ | Vérification des appels de journalisation ; seules les erreurs techniques sont journalisées |
| 15 | Tests backend, frontend et E2E | ✅ | 90 tests backend, 44 tests frontend, 3/3 E2E local |
| 16 | CI racine sur `master` avec job de couverture frontend | ✅ | `.github/workflows/ci.yml` : backend, frontend, coverage, Docker |
| 17 | IA locale optionnelle sans casser la traçabilité | ✅ | Ollama complète uniquement les clés manquantes ; validation verbatim, span, dates et statut ; panne Ollama sans impact sur les champs déterministes ; E2E API réel : 2 champs dont 1 `OLLAMA` vérifié avec source |
| 18 | Champs disponibles au statut `COMPLETED` | ✅ | Extraction des champs exécutée avant la persistance de l'instantané terminé ; `GET /fields` est complet dès que le client voit `COMPLETED` |
| 19 | Tests IA indépendants d'Ollama | ✅ | Client HTTP simulé pour 23 tests ; test réel opt-in `OLLAMA_LIVE_TEST=true`, 1/1 vert localement |

## Vérifications exécutées

| Commande | Résultat |
| --- | --- |
| `mvn -B verify` (API) | **90 tests**, 0 échec, 1 ignoré (live Ollama), `BUILD SUCCESS` |
| `mvn -Dtest=OllamaLiveTest test` avec `OLLAMA_LIVE_TEST=true` | **1/1**, modèle `llama3.2:1b`, 36,76 s |
| E2E API réel avec `OLLAMA_ENABLED=true` | `COMPLETED` après 14 s, **2 champs** (1 `RULE`, 1 `OLLAMA`), source vérifiée pour chacun |
| PostgreSQL local 18.6, profil `postgres`, rôle `document` | API `UP`, Flyway V1–V3, 2 champs et 1 correction relus dans `extracted_fields` / `field_corrections` |
| `npm test` (frontend) | **44 tests**, 13 fichiers |
| `npm run test:coverage` | **44 tests**, 60 % instructions, 73,62 % branches |
| `npm run typecheck` | OK |
| `npm run typecheck:e2e` | OK |
| `npm run lint` | 0 erreur, 6 avertissements `react-refresh` préexistants |
| `npm run build` | OK (avertissements de taille de bundle uniquement) |
| `npm run test:e2e` | **3/3** le 2026-11-10 sur stack locale H2 + Vite, après le correctif `COMPLETED`/champs |
| OpenAPI `/v3/api-docs` | `/fields`, `/corrections`, `/download`, `/documents/zip` exposés |

Le job CI Docker (PostgreSQL + MinIO + OCR + E2E) n'a pas été exécuté dans cet
environnement. Il constitue la validation restante avant fusion.

## Ce qui reste

### Validation de livraison

1. Pousser `DocuScan-AI` et ouvrir la pull request vers `master`.
2. Laisser la CI exécuter les quatre jobs, en particulier
   `Docker integration (PostgreSQL + MinIO + OCR + E2E)`.
3. Corriger uniquement si la CI révèle un écart propre à PostgreSQL ou à
   l'environnement Docker.
4. Merger après CI verte.

### Hors périmètre assumé

- Embeddings, `pgvector` et recherche sémantique.
- Champs métier supplémentaires (montant, fournisseur, échéance, etc.).
- Spans au niveau mot ou caractère.
- Workflow de validation à quatre yeux, annulation ou suppression de correction.
- Table dédiée aux spans (ils restent dans `pages_json`).
- SSO, MFA, facturation, quotas par organisation.
- File Redis ou file distribuée.
- Export PDF avec couche texte, segmentation de tableaux et reconstruction de
  l'ordre de lecture.

### Améliorations optionnelles

- Ajouter des seuils de couverture bloquants dans la CI.
- Épingler l'image MinIO au lieu du miroir `latest`.
- Valider la précision OCR sur un corpus client anonymisé.
- Extraire les spans dans une table dédiée si `pages_json` devient trop volumineux.

## Références

- Décisions d'architecture : [`docs/adr/0001-field-traceability.md`](adr/0001-field-traceability.md) et [`docs/adr/0002-ollama-field-extraction.md`](adr/0002-ollama-field-extraction.md)
- API : [`document-extraction-api/README.md`](../document-extraction-api/README.md)
- Frontend : [`document-extraction-front-end/README.md`](../document-extraction-front-end/README.md)
