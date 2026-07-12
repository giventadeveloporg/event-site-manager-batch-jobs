# Operation Log — event-site-manager-batch-jobs

> Append-only. Each entry prefixed with date and operation type.

## [2026-07-12] feature | Official documents batch foundation

- Aligned `EventMedia` with official-document columns (category, year, hierarchy, thumbnails, display priority)
- Added paged repository queries + `OfficialDocumentPagedReader` (default page size 25)
- Scaffolded disabled-by-default jobs: thumbnail, URL refresh, bulk import
- Added read-only preview + dry-run REST endpoints under `/api/batch-jobs/official-documents`
- HTML docs: `documentation/official-documents-batch-jobs.html`
- Existing batch jobs unchanged; mutation flags default to `false`

## [2026-04-15] init | Wiki scaffolded

- Created wiki directory structure (raw/, wiki/, assets/)
- Created index.md, log.md, overview.md starter files
- Karpathy LLM Wiki pattern ready for use
