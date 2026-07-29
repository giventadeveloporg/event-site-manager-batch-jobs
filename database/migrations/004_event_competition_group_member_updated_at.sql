-- Add updated_at required by batch-jobs Entity EventCompetitionGroupMember
-- (prod uses spring.jpa.hibernate.ddl-auto=validate; missing column crashes startup)
ALTER TABLE public.event_competition_group_member
  ADD COLUMN IF NOT EXISTS updated_at timestamp without time zone DEFAULT now() NOT NULL;

UPDATE public.event_competition_group_member
SET updated_at = COALESCE(updated_at, created_at, now())
WHERE updated_at IS NULL;
