#!/usr/bin/env python3
"""Pure stable release planning. Public release metadata only, no credentials."""
import re

def plan(run_number, sha, releases=None):
    number = int(run_number)
    if number <= 0:
        raise ValueError('Invalid build number')
    if not re.fullmatch(r'[0-9a-f]{40}', sha):
        raise ValueError('Expected full commit SHA')
    patches = []
    for release in releases or []:
        match = re.fullmatch(r'v1\.0\.(0|[1-9][0-9]*)', release.get('tag_name', ''))
        if not match:
            continue
        patch = int(match.group(1))
        if release.get('target_commitish') == sha:
            return {'version': f'1.0.{patch}', 'code': 100000 + patch, 'tag': f'v1.0.{patch}', 'sha': sha}
        patches.append(patch)
    patch = max(patches, default=-1) + 1
    if patch >= 2_000_000_000 - 100000:
        raise ValueError('Android versionCode range exhausted')
    version = f'1.0.{patch}'
    return {'version': version, 'code': 100000 + patch, 'tag': f'v{version}', 'sha': sha}

if __name__ == '__main__':
    import json, sys
    history = json.load(open(sys.argv[3])) if len(sys.argv) > 3 else []
    print(json.dumps(plan(sys.argv[1], sys.argv[2], history)))
