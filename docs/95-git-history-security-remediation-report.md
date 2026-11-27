# Milestone 7A — Git History Security Remediation Report

**Repository**: `prolt-operations`  
**Execution Date**: November 27, 2026  
**Branch**: `master`  
**Remote**: `origin` (`https://github.com/AnkitJha35/prolt-operations.git`)  
**Remediation Strategy**: Clean History Purge (Option A via `git-filter-repo`)  
**Final Verdict**: `REMEDIATION COMPLETE — READY FOR FINAL PUSH AUDIT`

---

## 1. Executive Summary & Objective

During Milestone 7 (Initial GitHub Push Audit), the planned initial push of `prolt-operations` to GitHub was blocked due to two critical historical violations originating in commit `ce64696` ("Phase 1: Core Foundation & Flyway Baseline"):
1. **GitHub Hard File Size Limit Violation (GH001)**: `backups/prolt_operations_pre_v7_20261030_180304.dump` (size 1,037,656,438 bytes / 989.58 MB) was tracked in git history, exceeding GitHub's 100 MB hard limit.
2. **Plaintext Credential Leak in History**: `src/main/resources/application-localvalidate.properties` contained a plaintext database password (`spring.datasource.password=probus@220706`) in historical commit `ce64696`.

Because the remote repository `https://github.com/AnkitJha35/prolt-operations.git` was verified to be completely empty (0 branches, 0 tags, 0 commits), a clean local history purge (Option A) was authorized.

The objective of Milestone 7A was to:
- Create full, independent recovery backups outside the repository.
- Execute a complete, permanent purge of the 1 GB database dump and `application-localvalidate.properties` from all historical commits in local `master`.
- Verify total repository health, integrity, and compliance with GitHub standards.
- Re-attach the remote origin and verify zero regressions in application code, Enterprise V2, Flyway safety gates, and build pipelines.
- **Strictly enforce**: No push to GitHub during this milestone.

---

## 2. Pre-Remediation State & Offending Artifacts

- **Pre-rewrite HEAD Commit SHA**: `6c00a90de1711fd3c36fbf3f196498f091c4c2e4`
- **Total Historical Commits**: 45 commits
- **Offending Artifacts Identified**:
  - `backups/prolt_operations_pre_v7_20261030_180304.dump`: Blob ID `267e0a99b1c75e8e93f2169880c410c60532a6f1`, size `1037656438` bytes (989.58 MB). Introduced in commit `ce64696`.
  - `src/main/resources/application-localvalidate.properties`: Contained database credential `spring.datasource.password=probus@220706`. Introduced in commit `ce64696`.
- **Pre-rewrite Pack Size**: ~1.2 GB.

---

## 3. Pre-Execution Safety & Backup Verification

Before modifying any Git references, three independent, redundant backups were created and verified outside the repository workspace:

1. **Bare Mirror Repository Clone**:
   - Location: `/home/ankit-jha/probus-workspace/prolt-operations-backup-pre-m7a.git`
   - Command: `git clone --mirror /home/ankit-jha/probus-workspace/prolt-operations ...`
   - Status: Verified complete.

2. **Full Git Bundle**:
   - Location: `/home/ankit-jha/probus-workspace/prolt-operations-backup-pre-m7a.bundle`
   - Command: `git bundle create /home/ankit-jha/probus-workspace/prolt-operations-backup-pre-m7a.bundle --all`
   - Verification: `git bundle verify /home/ankit-jha/probus-workspace/prolt-operations-backup-pre-m7a.bundle`
   - Output: `The bundle records a complete history. /home/ankit-jha/probus-workspace/prolt-operations-backup-pre-m7a.bundle is okay`

3. **Physical Untracked Local Configuration & Database Backups**:
   - Location: `/home/ankit-jha/probus-workspace/local_untracked_files_backup/`
   - Preserved Artifacts:
     - All 6 database dump files (`backups/*.dump`) totaling 5.8 GB.
     - All local properties files (`application-dev.properties`, `application-local.properties`, `application-localvalidate.properties`, `application-staging.properties`, `application-prod.properties`, `application.properties`).

---

## 4. History Purge Execution

- **Tool**: `git-filter-repo` (executed via Python uvx: `/home/ankit-jha/.local/bin/uvx git-filter-repo`)
- **Execution Command**:
  ```bash
  /home/ankit-jha/.local/bin/uvx git-filter-repo \
    --path backups/prolt_operations_pre_v7_20261030_180304.dump \
    --path src/main/resources/application-localvalidate.properties \
    --invert-paths --force
  ```
- **Execution Output**:
  - Processed 45 commits across 1,430 objects in 0.78 seconds.
  - Successfully rewritten object graph.
- **Remote Re-association**:
  - `git-filter-repo` safety protocol automatically detached remote `origin`.
  - Re-attached remote `origin` pointing to `https://github.com/AnkitJha35/prolt-operations.git`:
    ```bash
    git remote add origin https://github.com/AnkitJha35/prolt-operations.git
    ```

---

## 5. Post-Remediation Verification & Audit Evidence

### 5.1 Commit Graph & Hash Audit
- **New HEAD SHA**: `140679983f6406ceaf6d343bf9de82bb63b16849`
- **Total Commit Count**: 45 commits (1-to-1 correspondence with original history, without the offending blobs).
- **Initial Baseline Commit**: `a1f965f` ("Phase 1: Core Foundation & Flyway Baseline") replaces old commit `ce64696`.
- **Top 5 Commits on `master`**:
  ```
  1406799 (HEAD -> master) docs: record Milestone 7 GitHub initial push audit and block report
  2ce172b docs: add engineering audit reports and verification deliverables (docs/42 to docs/92)
  2449ca7 feat(starrocks): align schema, controllers, and verify integration test suite
  05d3742 refactor(calculator): decompose legacy saving calculator engine into domain modules
  8ab86fa feat(security): implement OTP-only authentication, token versioning, and refresh token architecture
  ```

### 5.2 Verification of Complete Blob Purge
1. **Tracked Files in HEAD**:
   ```bash
   git ls-tree -r HEAD | grep -E "application-localvalidate|\.dump"
   ```
   - **Result**: `0` matches (exit code 1).
2. **Reachable History Search**:
   ```bash
   git log --all --full-history -- backups/prolt_operations_pre_v7_20261030_180304.dump src/main/resources/application-localvalidate.properties
   ```
   - **Result**: `0` commits found. Completely erased from git history.
3. **Large Blob Verification across All Reachable Objects**:
   ```bash
   git rev-list --objects --all | git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' | awk '$1=="blob" && $3 > 10485760 {print $3, $4}'
   ```
   - **Result**: `0` blobs over 10 MB exist.
4. **Repository Size Reduction**:
   - Pack size reduced from **~1,200 MiB** to **9.92 MiB** (`git count-objects -vH`).
   - Largest remaining blob in repository: `5,152,227` bytes (~4.91 MB, `src/main/resources/templates/TECHNICAL`).
   - 100% compliant with GitHub 100 MB hard limit (GH001) and 50 MB warning threshold.

### 5.3 Git Repository Integrity
- **Full Integrity Check**:
  ```bash
  git fsck --full
  ```
  - **Result**: 0 corruptions, 0 broken links, 0 dangling objects. Clean pass.

### 5.4 Enterprise V2 & Core System Invariance
- **Enterprise V2 Calculator**:
  ```bash
  git diff 163ae0a..HEAD -- src/main/java/com/sense/prolt_operations/service/SavingsCalculatorNewService.java
  ```
  - **Result**: `0` differences. `SavingsCalculatorNewService.java` is 100% untouched and unchanged since its introduction.
- **Flyway Safety Gate**:
  - `src/main/resources/application.properties:50`: `spring.flyway.target=${FLYWAY_TARGET:8}` is preserved intact.
- **Production Profile**:
  - `src/main/resources/application-prod.properties` remains tracked and present in HEAD (`e942758a7a1599d172dd31323f73bc00791822df`).
- **Offline Build & Test Compilation**:
  - `./mvnw test-compile -DskipTests=true`
  - **Result**: `BUILD SUCCESS` (0 compilation errors across all source and test classes).

### 5.5 Node.js Reference Repository Safeguard
- `IEX-Dashboard/backend` was completely unreferenced and untouched throughout Milestone 7A.

---

## 6. Summary Comparison Table

| Metric / Check | Pre-Remediation (Milestone 7) | Post-Remediation (Milestone 7A) | Status |
| :--- | :--- | :--- | :--- |
| **HEAD SHA** | `6c00a90de1711fd3c36fbf3f196498f091c4c2e4` | `140679983f6406ceaf6d343bf9de82bb63b16849` | Cleanly rewritten |
| **Total Commits** | 45 | 45 | Preserved |
| **Pack Size** | ~1.2 GB | **9.92 MiB** | **99.2% reduction** |
| **Max Blob Size** | 989.58 MB (`prolt_operations_pre_v7...dump`) | **4.91 MB** (`templates/TECHNICAL`) | **<100 MB compliant** |
| **Historical Dumps** | 1 tracked dump in commit `ce64696` | **0** in all commits | **Erased** |
| **Historical Password File** | Tracked in `ce64696` (`application-localvalidate.properties`) | **0** in all commits | **Erased** |
| **`git fsck --full`** | Clean | **Clean (0 errors)** | **Verified** |
| **Enterprise V2** | Untouched | **Untouched (0 diff)** | **Preserved** |
| **Flyway Target** | `spring.flyway.target=${FLYWAY_TARGET:8}` | `spring.flyway.target=${FLYWAY_TARGET:8}` | **Preserved** |
| **Production Properties** | Tracked | **Tracked** | **Preserved** |
| **`mvn test-compile`** | BUILD SUCCESS | **BUILD SUCCESS** | **Verified** |
| **GitHub Remote** | Empty | **Empty (no push executed)** | **Safe** |

---

## 7. Final Verdict & Next Steps

### Verdict
**`REMEDIATION COMPLETE — READY FOR FINAL PUSH AUDIT`**

All blocking conditions identified in Milestone 7 have been resolved. The local Git history has been cleansed of oversized database dumps and sensitive configuration files without compromising legitimate code, test suites, database migrations, or historical commit structures.

### Prohibited Actions Acknowledged
- **NO PUSH HAS BEEN EXECUTED TO GITHUB.**
- The repository remains in a local-only state pending user review and authorization for the final push audit.
