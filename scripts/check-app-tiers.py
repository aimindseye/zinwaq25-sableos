#!/usr/bin/env python3
"""Check product/q25/app_tiers.tsv against product/q25/apps.tsv.

Every enabled apps.tsv package needs exactly one tier row, tiers are core, recommended or optional,
every `requires` package exists, and a core app never requires a non-core app.
"""
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
TIERS = ("core", "recommended", "optional")
RANK = {"core": 0, "recommended": 1, "optional": 2}
# LineageOS apps the Q25 keeps; not rows of apps.tsv.
STOCK = {"com.android.contacts", "com.android.contactspicker"}


def rows(path):
    for n, line in enumerate(path.read_text().splitlines(), 1):
        if line.startswith("#") or not line.strip():
            continue
        yield n, line.split("\t")


def main():
    errors = []
    built = {}
    for n, c in rows(REPO / "product/q25/apps.tsv"):
        if c[0] == "module" or len(c) < 9:
            continue
        built[c[5]] = c[8] == "yes"
    tier = {}
    requires = {}
    seen_header = False
    for n, c in rows(REPO / "product/q25/app_tiers.tsv"):
        if c[0] == "package":
            seen_header = True
            continue
        if len(c) != 3:
            errors.append(f"app_tiers.tsv:{n}: expected 3 columns")
            continue
        pkg, t, req = c
        if pkg in tier:
            errors.append(f"app_tiers.tsv:{n}: {pkg} listed twice")
        if t not in TIERS:
            errors.append(f"app_tiers.tsv:{n}: {pkg} has unknown tier {t!r}")
        tier[pkg] = t
        requires[pkg] = [] if req == "-" else req.split(",")
        if pkg not in built and pkg not in STOCK:
            errors.append(f"app_tiers.tsv:{n}: {pkg} is not in apps.tsv")
        elif built.get(pkg) is False:
            errors.append(f"app_tiers.tsv:{n}: {pkg} is disabled in apps.tsv")
    if not seen_header:
        errors.append("app_tiers.tsv: missing header row")
    for pkg, enabled in built.items():
        if enabled and pkg not in tier:
            errors.append(f"{pkg}: enabled in apps.tsv but has no tier")
    for pkg, reqs in requires.items():
        for r in reqs:
            if r not in tier:
                errors.append(f"{pkg}: requires unknown package {r}")
            elif tier[pkg] in RANK and tier[r] in RANK and RANK[tier[r]] > RANK[tier[pkg]]:
                errors.append(f"{pkg} ({tier[pkg]}) requires {r} ({tier[r]}); a required app cannot be less always-on")
    for e in errors:
        print("FAIL ", e)
    if not errors:
        counts = {t: sum(1 for v in tier.values() if v == t) for t in TIERS}
        print("PASS  app tiers", counts)
    return 1 if errors else 0


sys.exit(main())
