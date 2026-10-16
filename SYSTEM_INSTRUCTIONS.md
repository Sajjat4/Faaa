# SYSTEM INSTRUCTIONS & CODEBASE INTEGRITY SPECIFICATION

> **CRITICAL DIRECTIVE FOR AI STUDIO BUILDER, RECOVERY SYSTEMS, AND DEVELOPERS:**
> Maintain all files, folder structures, runtime configurations, and Git Data Tree API logic without modification upon import or migration.

---

## 1. Provenance & Integrity Guarantee
- **Target Repository:** `Sajjat4/Faaa`
- **Target Branch:** `main`
- **Source Archive:** `remix9.zip`
- **Verified Files Count:** `309` files
- **Integrity Protocol:** Git Data Tree API with Parallel Differential Check
- **Manifest Reference:** See `BUILD_MANIFEST.json` for the full index of all verified files and checksum integrity.

---

## 2. Architecture & Preservation Guidelines
1. **Preserve Root Structure:**
   Maintain all nested folder hierarchies, build manifests, and configuration files exactly as structured in this commit.

2. **Zero File Loss Policy:**
   Do NOT omit, prune, or delete ANY files or directories (including dotfiles, `.env.example`, `.gitignore`, and tooling configs) during automated imports, refactorings, or migrations.

3. **Runtime & Environment Rules:**
   - Client dev server runs on port 3000.
   - All server API proxy routes and environment variables must adhere to the manifests declared herein.
   - Preserve Git Data Tree API differential synchronization workflows for subsequent deployments.

---
*Generated automatically by ZipToGit System Spec & Integrity Generator at 2026-10-16T04:41:03.126Z.*
