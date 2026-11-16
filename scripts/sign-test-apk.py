#!/usr/bin/env python3
"""Restore approved encrypted CI secret only to runner temp, sign APK, and delete secrets.
Never print signing material. Only the signed APK and public certificate digest are outputs.
"""
import os,json,base64,pathlib,subprocess,hashlib
bundle=json.loads(os.environ.pop('PDF_FOCUS_TEST_SIGNING_BUNDLE'))
tmp=pathlib.Path(os.environ['RUNNER_TEMP'])/'pdf-focus-test-signing';tmp.mkdir(mode=0o700,exist_ok=True)
key=tmp/'key.p12';password=tmp/'password';keypassword=tmp/'key-password';key.write_bytes(base64.b64decode(bundle['keystore']));password.write_text(bundle['password']);key.chmod(0o600);password.chmod(0o600);keypassword.write_text(bundle['password']);keypassword.chmod(0o600)
try:
 sdk=pathlib.Path(os.environ['ANDROID_HOME']);bt=sdk/'build-tools'/'35.0.0'
 cert=subprocess.run(['keytool','-exportcert','-keystore',str(key),'-storepass:file',str(password),'-alias',bundle['alias']],check=True,capture_output=True).stdout
 digest=hashlib.sha256(cert).hexdigest()
 assert digest=='bbdae9f61a83de058513b89ba9e57af2ac2e29831a5c25e7445a30c7e8f0ea16', 'Signing identity mismatch'
 out=pathlib.Path('signed-test');out.mkdir(exist_ok=True)
 src=pathlib.Path('app/build/outputs/apk/debug/app-arm64-v8a-debug.apk');dest=out/'pdf-focus-test-0.10.3-arm64.apk'
 subprocess.run([str(bt/'apksigner'),'sign','--ks',str(key),'--ks-pass','file:'+str(password),'--key-pass','file:'+str(keypassword),'--ks-key-alias',bundle['alias'],'--out',str(dest),str(src)],check=True,capture_output=True)
 subprocess.run([str(bt/'apksigner'),'verify','--verbose',str(dest)],check=True,capture_output=True)
 (out/'SHA256SUMS.txt').write_text(hashlib.sha256(dest.read_bytes()).hexdigest()+'  '+dest.name+'\nCertificate SHA256 '+digest+'\n')
 print('Signed PDF Focus Test APK verified with pinned public certificate.')
except subprocess.CalledProcessError as error:
 message=(error.stderr or b'').decode('utf-8',errors='replace')
 for value in [bundle['password'],bundle['keystore']]: message=message.replace(value,'[secret]')
 print(message[:2000])
 raise SystemExit('Signing validation failed; no APK published')
finally:
 key.unlink(missing_ok=True);password.unlink(missing_ok=True);keypassword.unlink(missing_ok=True);tmp.rmdir()
