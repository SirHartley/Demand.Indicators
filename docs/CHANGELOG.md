# Changelog guide

How agents write entries in the root `Changelog`. Read it for every implementation task. Version numbers, release steps and how the release tooling reads the file are in [RELEASE.md](RELEASE.md). The wording follows the `house-style` skill's "Changelog entries" section, which wins over this guide if the two differ.

## When to write an entry

- Every implementation task adds or amends its entries in the current unreleased section before merge, in the same commit as the change.
- Read-only work and documentation-only changes add nothing. Say why in the PR when a change needs no entry.
- Check whether the feature has been released: look for it under every version heading without `UNRELEASED`.
  - Not released yet: edit its `Added -` entry so it describes the feature as it now stands. No `Fixed -`, `Adjusted -` or `Removed -` line for it.
  - Released: the change gets its own entry.
- Record only completed changes from the actual diff. Never list plans or unfinished work.

## File layout

`Changelog` at the repository root, plain text, no extension. The fixed `Known issues` and `Notes` sections come first (`None` when empty), then the `Changelog` section with versions newest first and a blank line between them:

```text
1.0.g - UNRELEASED
Fixed - Indicators no longer disappear when LunaLib is not installed

1.0.f
Added - Setting to invert the arrows
```

- Version heading: the version alone on its line, optionally followed by ` - UNRELEASED`. No `v` prefix, no date, no Markdown. The release tooling reads these headings, so keep them in this form.
- Entries: one change per line, starting `Added - `, `Adjusted - `, `Fixed - ` or `Removed - `, in that order.
- A `Note - ` line directly below an entry explains a limit or something the player must do.
- The mod's versions are short enough to need no category headings. Add one (`General`, `Settings`) only when a version collects many entries.
- Do not present a refactor, build change or documentation edit as a gameplay change. A build or tooling change normally gets no entry.
- If no unreleased section exists, add one above the newest version. Ask the user which version comes next if it has not been chosen; do not invent a new version per commit.
- Keep the existing lines as the user wrote them (`Fixed - Marines got their rank back`). Never tidy, shorten or merge them unasked.

## Wording

Entries are player-facing and use the user's forms:

- Short: stay under 80 characters. Capital letter after the dash, no full stop on a one-clause line.
- Name what the player sees: the arrows, the cargo screen, the setting by its name in `data/config/LunaSettings.csv` (`Low Impact Mode`, `Invert Indicators`). Never class names or the call chain.
- `Added - ` names the thing and what it does: `Added - Setting to invert the arrows`.
- `Adjusted - ` gives numbers as old `->` new with the unit, or a direction word (Reduced, Increased).
- `Fixed - ` describes what the player saw: `Fixed - Arrows no longer show on storage` or `Fixed - Crash when opening a market without storage`.
- `Removed - ` names the thing, then the reason in parentheses.
- Parentheses carry the short extra: `(was 3)`, `(vanilla game limitation)`, credits to real people.
- No semicolons, curly quotes or hyphen used as a dash. No first person, jokes, caps, exclamation marks or hedges about whether a fix works; untested status goes in the PR.
- No agent or model names, links, validation logs or commit hashes.

## Amending versus adding

- Combine entries when several edits fix the same player-visible problem.
- An unreleased feature removed again before release loses its `Added -` entry; it gets no `Removed -` line.
- Never repeat a change once per file, step or PR.

## Maintenance

Update this guide when the `Changelog` layout or entry format changes, or when the house-style "Changelog entries" section changes. Release mechanics belong in [RELEASE.md](RELEASE.md).
