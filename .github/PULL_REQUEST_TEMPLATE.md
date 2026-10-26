## Tracking

Link the issue and its acceptance criteria. Keep one issue type and one triaged priority. Assign a stable-version milestone only when this change is intended for it; leave unscheduled work unassigned. Update Project Status with verification evidence; Done does not imply the feature is shipped. Release Stage belongs only to the release tracker, and new feature scope after RC returns that release to Beta.

## Change

Describe the problem and the resulting behavior.

## Validation

List the build, unit/instrumentation tests and device checks run. State any failures or checks not run.

## Security and compatibility

- [ ] The default build stays offline. Any authorized direct-update networking is confined to public GitHub metadata/APKs, isolated from wallet data; no telemetry is added.
- [ ] Keys, user backups, screenshots of personal data and signing files are excluded.
- [ ] Import limits, cancellation, lock cleanup and backup compatibility considered.
- [ ] Any schema, application ID or signing change is explicitly documented.
- [ ] README and relevant docs match the behavior.

## AI usage

Disclose material AI assistance (analysis, docs, code or tests) and the checks actually verified.
