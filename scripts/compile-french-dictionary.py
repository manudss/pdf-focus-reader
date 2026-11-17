#!/usr/bin/env python3
"""Rebuild independent spelling FSA from Grammalecte's MPL-2.0 public inflected lexicon.
No user input, article, or OCR data is read by this script. Requires JDK17 and Python3.
"""
import hashlib,io,pathlib,subprocess,tempfile,urllib.request,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory()as tmp:
 d=pathlib.Path(tmp)
 url='https://grammalecte.net/dic/lexique-grammalecte-fr-v7.7.zip'
 raw=urllib.request.urlopen(url).read();assert hashlib.sha256(raw).hexdigest()=='bdccf2065e252010253171000a38de84581407896b6bc60aca7d4d69dab7e4d2', 'Source archive changed; inspect before rebuilding';print('source',url,'sha256',hashlib.sha256(raw).hexdigest())
 z=zipfile.ZipFile(io.BytesIO(raw));text=z.read('lexique-grammalecte-fr-v7.7.txt').decode('utf-8');rows=[x.split('\t')for x in text.splitlines()if '\t'in x and not x.startswith('#')][1:]
 forms=sorted(set(x[2].replace('’',"'")for x in rows),key=lambda s:s.encode('utf-8'))
 (d/'forms.txt').write_text('\n'.join(forms)+'\n',encoding='utf-8')
 for artifact in ['morfologik-fsa','morfologik-fsa-builders']:
  u=f'https://repo.maven.apache.org/maven2/org/carrot2/{artifact}/2.1.9/{artifact}-2.1.9.jar';(d/(artifact+'.jar')).write_bytes(urllib.request.urlopen(u).read())
 (d/'hppc.jar').write_bytes(urllib.request.urlopen('https://repo.maven.apache.org/maven2/com/carrotsearch/hppc/0.7.2/hppc-0.7.2.jar').read())
 source=ROOT/'scripts/CompileFrenchDictionary.java';subprocess.run(['javac','-encoding','UTF-8','-cp',str(d/'*'),'-d',str(d),str(source)],check=True)
 dest=ROOT/'app/src/main/assets/spelling/fr.dict'
 subprocess.run(['java','-Xmx384m','-cp',str(d/'*')+':'+str(d),'CompileFrenchDictionary',str(d/'forms.txt'),str(dest)],check=True)
 print('output sha256',hashlib.sha256(dest.read_bytes()).hexdigest())
