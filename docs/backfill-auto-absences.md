# Backfill for auto-seeded absences

Earlier builds defaulted every `attendance_records.marking_method` to `manual`, even for rows that were auto-seeded when a session started. The production schema still defaults the column to `manual` (see `marking_method USER-DEFINED DEFAULT 'manual'::marking_method` in Supabase), and the service now overrides that default whenever it seeds an automatic absence. After deploying the fix, run the following SQL in Supabase to correct historical data. It updates only rows that still have the seeded `absent` status and no manual override timestamp beyond creation.

```sql
update attendance_records ar
set marking_method = 'auto'
where ar.status = 'absent'
  and (ar.marked_at = ar.created_at or ar.marked_at is not distinct from ar.created_at)
  and (ar.marking_method is null or ar.marking_method = 'manual');
```

If you track manual overrides differently (e.g., via audit logs), tighten the `where` clause accordingly so that real manual edits stay marked as such.
