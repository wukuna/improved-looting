# Improved Looting roadmap

Status: initial Plugin Hub submission prepared on 2026-10-30 after local player testing. The planned features within the documented scope are implemented; monster-source filtering remains unavailable in the current API.

Implemented: unified labels/icons/values/highlights/beams; custom sounds and controls; despawn warnings; nearby browser; rule inspector and overrides; notification cards; categories; external Wiki drop-table/create-rule workflow; richer visual editing; full preset backups and conservative migration; original-resolution images; label/timer controls; bounded dense-scene processing; sub-world beam locations; source-availability explanation; optional resolved-display provider and a compatible Improved Right-Clicks adapter.

Scope limits are explicit in README.md. Wiki tables open in the user's browser, source attribution is unavailable because RuneLite 1.13.4 discards item-stack locations, and unsupported arbitrary settings are not migrated. These are deliberate boundaries, not fabricated fallback data.

Ongoing validation: Check filters and overrides, custom audio, expiry warnings, busy ground piles, sub-world visuals, logout/profile changes and Right-Clicks integration. Build/test/package verification does not establish live activation.

Improved Right-Clicks continues to own menus; Interface Layout owns interface positioning. Historical tracking and GPU depth work remain separate. Do not re-enable BYO Notifier or add TFA behavior. No gameplay automation, client restart or external messages.
