#!/usr/bin/env python3
"""Pure release planning for a successful, merged main commit. No credentials."""
import re

def plan(run_number, sha):
    number = int(run_number)
    if number <= 0 or number >= 2_000_000_000 - 100_000:
        raise ValueError('Build number outside Android versionCode range')
    if not re.fullmatch(r'[0-9a-f]{40}', sha):
        raise ValueError('Expected full commit SHA')
    version = f'1.0.{number}'
    return {'version': version, 'code': 100_000 + number, 'tag': f'v{version}', 'sha': sha}

if __name__ == '__main__':
    import json, sys
    print(json.dumps(plan(sys.argv[1], sys.argv[2])))
