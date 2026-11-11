# Verified collection sync API (fork extension, version 1)

This fork adds two `ContentResolver.call` methods to the existing
`content://<applicationId>.flashcards` provider. Both explicitly require the
provider's `<applicationId>.permission.READ_WRITE_DATABASE` permission. No activity
launch or overlay access is needed. Call from a background thread.

- `verified_sync_v1`: `arg` is a caller-generated UUID, `extras["query"]` is the
  Anki search (1–2048 characters). Repeating the same UUID returns its existing
  result. Use a new UUID for each new sync/search. Accepted requests enqueue an
  expedited collection SyncWorker, subject to network policy and Android quotas.
- `verified_sync_status_v1`: same UUID in `arg`; reads the result without starting
  work. An unrecognized or pruned request returns `unknown`.

Every response includes `api_version=1`, `request_id`, and `outcome`.
`pending` means work is queued/running. `busy` means the caller already has work
pending or the existing automation rate limit applies; retry the same UUID later.
Other non-success outcomes include `not_logged_in`, `sync_failed`, `cancelled`,
`direction_required`, `profile_changed`, `query_failed`, and `unknown`.

Only `success` includes `count` (integer, including zero), `profile` (active profile
ID), and `completed_at` (epoch milliseconds). The count is read after collection
sync completes while the profile-switch lock remains held. A verified no-changes
response counts as success. An ambiguous full sync never counts as success. Media
sync is unnecessary for a card-count receipt and is not requested by this API.

Results live in WorkManager's database and are retained for at least seven days
unless app data is cleared. Request IDs are namespaced by Binder caller UID;
clients cannot read another UID's request. Pending work captures the current
profile, and a profile mismatch rejects both execution and later consumption.
Reusing a UUID with a different search does not change the original request.

Clients must validate version, request ID, outcome, profile, nonnegative count,
and timestamp, and skip uploads if any validation fails. AnkiBacklogBee requires a
receipt no more than 15 minutes old. Network failures, Android scheduling delays,
and process death cannot turn a pending or failed request into success.

## Local build

Build the side-by-side fork (does not replace upstream AnkiDroid):

```
MINIFY_ENABLED=false ./gradlew :AnkiDroid:assembleFullRelease \
  -PcustomSuffix=backlogbee -PcustomName='AnkiDroid BacklogBee'
```

Configure a private signing key using the project's `KEYSTOREPATH`, `KEYSTOREPWD`,
`KEYALIAS`, and `KEYPWD` environment variables before distributing/installing a
release. The repository fallback key is public and only suitable for testing.
The app ID is `com.ichi2.anki.backlogbee`. Complete its first-run setup and sync from
AnkiWeb before connecting a client. Keep upstream AnkiDroid installed until the
fork has been validated with the tablet locked.
