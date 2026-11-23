#!/usr/bin/env python3
"""Restore approved encrypted CI secret only to runner temp, sign APK, and delete secrets.
Never print signing material. Only the signed APK and public certificate digest are outputs.
"""
import os,json,base64,pathlib,re,subprocess,hashlib

PINNED_CERT_SHA256='6580f2f9c1ae282d4fa05a20415c001eaa80e0d187ae8099ce1043c378dd0cb9'

def sign_aab(key,password,keypassword,alias,src,dest,expected_digest):
    """Sign an App Bundle with its JAR signature and verify the pinned identity.
    apksigner is APK-only (it aborts on a bundle's base/manifest layout, demanding a root
    AndroidManifest.xml); the documented bundle signing tool is jarsigner:
    https://developer.android.com/tools/bundletool ('You can't use apksigner to sign your
    app bundle'). Verification re-reads the signer certificate from the signed bundle and
    compares it to the pinned stable identity, so a wrong key fails before publication."""
    subprocess.run(['jarsigner','-keystore',str(key),'-storetype','PKCS12','-storepass:file',str(password),'-keypass:file',str(keypassword),'-signedjar',str(dest),str(src),alias],check=True,capture_output=True)
    subprocess.run(['jarsigner','-verify','-certs',str(dest)],check=True,capture_output=True)
    printed=subprocess.run(['keytool','-printcert','-jarfile',str(dest)],check=True,capture_output=True).stdout.decode()
    match=re.search(r'SHA-?256:\s*((?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2})',printed)
    assert match, 'No signer certificate fingerprint found on the signed bundle'
    assert match.group(1).replace(':','').lower()==expected_digest, 'Signed bundle certificate does not match the pinned stable identity'

def main():
    bundle=json.loads(os.environ.pop('PDF_FOCUS_STABLE_SIGNING_BUNDLE'))
    tmp=pathlib.Path(os.environ['RUNNER_TEMP'])/'pdf-focus-stable-signing';tmp.mkdir(mode=0o700,exist_ok=True)
    key=tmp/'key.p12';password=tmp/'password';keypassword=tmp/'key-password';key.write_bytes(base64.b64decode(bundle['keystore']));password.write_text(bundle['password']);key.chmod(0o600);password.chmod(0o600);keypassword.write_text(bundle['password']);keypassword.chmod(0o600)
    # The pinned stable identity is a constant, never an environment override: no CI
    # misconfiguration can silently substitute another signing identity for a release.
    expected=PINNED_CERT_SHA256
    try:
     sdk=pathlib.Path(os.environ['ANDROID_HOME']);bt=sdk/'build-tools'/'36.0.0'
     cert=subprocess.run(['keytool','-exportcert','-keystore',str(key),'-storepass:file',str(password),'-alias',bundle['alias']],check=True,capture_output=True).stdout
     digest=hashlib.sha256(cert).hexdigest()
     assert digest==expected, 'Signing identity mismatch'
     out=pathlib.Path('signed-release');out.mkdir(exist_ok=True)
     version=os.environ['PDF_FOCUS_RELEASE_VERSION']
     assert re.fullmatch(r'1\.0\.(0|[1-9][0-9]*)',version), 'Unexpected release version'
     checks=[]
     for abi,label in [('arm64-v8a','arm64'),('x86_64','x86_64')]:
      src=pathlib.Path(f'app/build/outputs/apk/release/app-{abi}-release-unsigned.apk')
      dest=out/f'pdf-focus-reader-{version}-{label}.apk'
      subprocess.run([str(bt/'apksigner'),'sign','--ks',str(key),'--ks-pass','file:'+str(password),'--key-pass','file:'+str(keypassword),'--ks-key-alias',bundle['alias'],'--out',str(dest),str(src)],check=True,capture_output=True)
      subprocess.run([str(bt/'apksigner'),'verify','--verbose',str(dest)],check=True,capture_output=True)
      metadata=subprocess.run([str(bt/'aapt'),'dump','badging',str(dest)],check=True,capture_output=True).stdout.decode()
      assert f"versionName='{version}'" in metadata, 'APK version does not match planned release'
      assert f"versionCode='{int(os.environ['PDF_FOCUS_RELEASE_CODE'])}'" in metadata, 'APK versionCode mismatch'
      assert "name='com.pdffocus.reader'" in metadata, 'Package identity changed'
      assert "application-debuggable" not in metadata, 'Debuggable APK cannot be a stable release'
      checks.append(hashlib.sha256(dest.read_bytes()).hexdigest()+'  '+dest.name)
     # Google Play uploads an Android App Bundle. A bundle is JAR-signed (jarsigner);
     # apksigner is APK-only and cannot sign one.
     aab_src=pathlib.Path('app/build/outputs/bundle/release/app-release.aab')
     assert aab_src.is_file(), 'App Bundle missing: run bundleRelease in the same Gradle invocation'
     aab=out/f'pdf-focus-reader-{version}.aab'
     sign_aab(key,password,keypassword,bundle['alias'],aab_src,aab,expected)
     checks.append(hashlib.sha256(aab.read_bytes()).hexdigest()+'  '+aab.name)
     (out/'SHA256SUMS.txt').write_text('\n'.join(checks)+'\nCertificate SHA256 '+digest+'\n')
     print('Signed stable PDF Focus Reader APK verified with pinned public certificate.')
    except subprocess.CalledProcessError as error:
     message=(error.stderr or b'').decode('utf-8',errors='replace')
     for value in [bundle['password'],bundle['keystore']]: message=message.replace(value,'[secret]')
     print(message[:2000])
     raise SystemExit('Signing validation failed; no APK published')
    finally:
     key.unlink(missing_ok=True);password.unlink(missing_ok=True);keypassword.unlink(missing_ok=True);tmp.rmdir()

if __name__=='__main__':
    main()
