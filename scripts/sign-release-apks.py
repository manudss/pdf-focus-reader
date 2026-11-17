#!/usr/bin/env python3
"""Restore approved encrypted CI secret only to runner temp, sign APK, and delete secrets.
Never print signing material. Only the signed APK and public certificate digest are outputs.
"""
import os,json,base64,pathlib,re,subprocess,hashlib
bundle=json.loads(os.environ.pop('PDF_FOCUS_ORIGINAL_SIGNING_BUNDLE'))
tmp=pathlib.Path(os.environ['RUNNER_TEMP'])/'pdf-focus-test-signing';tmp.mkdir(mode=0o700,exist_ok=True)
key=tmp/'key.p12';password=tmp/'password';keypassword=tmp/'key-password';key.write_bytes(base64.b64decode(bundle['keystore']));password.write_text(bundle['password']);key.chmod(0o600);password.chmod(0o600);keypassword.write_text(bundle['password']);keypassword.chmod(0o600)
try:
 sdk=pathlib.Path(os.environ['ANDROID_HOME']);bt=sdk/'build-tools'/'35.0.0'
 cert=subprocess.run(['keytool','-exportcert','-keystore',str(key),'-storepass:file',str(password),'-alias',bundle['alias']],check=True,capture_output=True).stdout
 digest=hashlib.sha256(cert).hexdigest()
 assert digest=='7eac14e5a1dc51880a261d090a382fd37410d11e4c9523b332be05d9f6d2e107', 'Signing identity mismatch'
 out=pathlib.Path('signed-release');out.mkdir(exist_ok=True)
 version=os.environ['PDF_FOCUS_RELEASE_VERSION']
 assert re.fullmatch(r'0\.11\.[1-9][0-9]*',version), 'Unexpected release version'
 checks=[]
 for abi,label in [('arm64-v8a','arm64'),('x86_64','x86_64')]:
  src=pathlib.Path(f'app/build/outputs/apk/debug/app-{abi}-debug.apk')
  dest=out/f'pdf-focus-reader-{version}-{label}.apk'
  subprocess.run([str(bt/'apksigner'),'sign','--ks',str(key),'--ks-pass','file:'+str(password),'--key-pass','file:'+str(keypassword),'--ks-key-alias',bundle['alias'],'--out',str(dest),str(src)],check=True,capture_output=True)
  subprocess.run([str(bt/'apksigner'),'verify','--verbose',str(dest)],check=True,capture_output=True)
  metadata=subprocess.run([str(bt/'aapt'),'dump','badging',str(dest)],check=True,capture_output=True).stdout.decode()
  assert f"versionName='{version}'" in metadata, 'APK version does not match planned release'
  assert f"versionCode='{int(os.environ['PDF_FOCUS_RELEASE_CODE'])}'" in metadata, 'APK versionCode mismatch'
  assert "name='com.pdffocus.reader'" in metadata, 'Package identity changed'
  checks.append(hashlib.sha256(dest.read_bytes()).hexdigest()+'  '+dest.name)
 (out/'SHA256SUMS.txt').write_text('\n'.join(checks)+'\nCertificate SHA256 '+digest+'\n')
 print('Signed original PDF Focus Reader APK verified with pinned public certificate.')
except subprocess.CalledProcessError as error:
 message=(error.stderr or b'').decode('utf-8',errors='replace')
 for value in [bundle['password'],bundle['keystore']]: message=message.replace(value,'[secret]')
 print(message[:2000])
 raise SystemExit('Signing validation failed; no APK published')
finally:
 key.unlink(missing_ok=True);password.unlink(missing_ok=True);keypassword.unlink(missing_ok=True);tmp.rmdir()
