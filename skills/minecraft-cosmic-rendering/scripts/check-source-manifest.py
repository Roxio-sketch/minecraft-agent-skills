"""Read-only verification of an archived source snapshot and recorded line anchors."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile

def sha(data):return hashlib.sha256(data).hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive',type=Path)
    parser.add_argument('manifest',type=Path)
    args=parser.parse_args()
    recorded=json.loads(args.manifest.read_text(encoding='utf-8-sig'))
    assert sha(args.archive.read_bytes())==recorded['source']['sha256'],'ZIP hash mismatch'
    with zipfile.ZipFile(args.archive) as archive:
        assert archive.testzip() is None,'ZIP CRC mismatch'
        for name,entry in recorded['entries'].items():
            blob=archive.read(name)
            assert sha(blob)==entry['sha256'],name
        for anchor in recorded['anchors']:
            lines=archive.read(anchor['entry']).decode('utf-8-sig').splitlines()
            selected='\n'.join(lines[anchor['startLine']-1:anchor['endLine']])
            assert sha(selected.encode('utf-8'))==anchor['rangeSha256Utf8Lf'],anchor
    print(json.dumps({'passed':True,'anchors':len(recorded['anchors']),'runtimeVerified':False}))

if __name__=='__main__':main()
