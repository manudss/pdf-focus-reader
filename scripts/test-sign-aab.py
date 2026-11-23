#!/usr/bin/env python3
"""Regression: the release script must sign the App Bundle with jarsigner, never apksigner.

apksigner is APK-only: build-tools 36 aborts on a bundle (MinSdkVersionException, missing
root AndroidManifest.xml; bundles carry base/manifest/AndroidManifest.xml), and the official
documentation states 'You can't use apksigner to sign your app bundle'
(https://developer.android.com/tools/bundletool). The previous AAB signing path could never
succeed; this test fails on it, because sign_aab() does not exist there.

Needs JDK tools on PATH (keytool, jarsigner), as provided by actions/setup-java. No network,
no secret: everything uses a throwaway key created in a temp directory and deleted after.
"""
import hashlib,importlib.util,pathlib,re,shutil,subprocess,tempfile,zipfile

SCRIPT=pathlib.Path(__file__).with_name('sign-release-apks.py')

def run(cmd):
    return subprocess.run(cmd,check=True,capture_output=True)

def main():
    keytool=shutil.which('keytool');jarsigner=shutil.which('jarsigner')
    assert keytool and jarsigner,'JDK tools (keytool, jarsigner) must be on PATH'
    spec=importlib.util.spec_from_file_location('sign_release',SCRIPT)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    with tempfile.TemporaryDirectory() as tmp:
        tmp=pathlib.Path(tmp)
        key=tmp/'test.p12';pw=tmp/'pw';kpw=tmp/'kpw'
        pw.write_text('throwaway-pass');kpw.write_text('throwaway-pass')
        run([keytool,'-genkeypair','-keystore',str(key),'-storetype','PKCS12','-alias','throwaway','-keyalg','RSA','-keysize','2048','-validity','1','-storepass:file',str(pw),'-keypass:file',str(kpw),'-dname','CN=Sign AAB Regression Throwaway'])
        cert=run([keytool,'-exportcert','-keystore',str(key),'-storetype','PKCS12','-storepass:file',str(pw),'-alias','throwaway']).stdout
        digest=hashlib.sha256(cert).hexdigest()
        fixture=tmp/'fixture.aab'
        with zipfile.ZipFile(fixture,'w') as bundle:
            bundle.writestr('base/manifest/AndroidManifest.xml',b'binary-manifest-placeholder')
            bundle.writestr('base/dex/classes.dex',b'dex-placeholder')
            bundle.writestr('BundleConfig.pb',b'config-placeholder')
        dest=tmp/'signed.aab'
        module.sign_aab(key,pw,kpw,'throwaway',fixture,dest,digest)
        check=run([jarsigner,'-verify','-certs',str(dest)])
        verified=(check.stdout+check.stderr).decode()
        assert 'jar verified' in verified.lower(),'jarsigner did not verify the signed bundle'
        printed=run([keytool,'-printcert','-jarfile',str(dest)]).stdout.decode()
        match=re.search(r'SHA-?256:\s*((?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2})',printed)
        assert match and match.group(1).replace(':','').lower()==digest,'signed bundle certificate is not the throwaway identity'
        try:
            module.sign_aab(key,pw,kpw,'throwaway',fixture,tmp/'wrong.aab','0'*64)
            raise SystemExit('digest mismatch on the signed bundle was not detected')
        except AssertionError:
            pass
    text=SCRIPT.read_text()
    assert module.PINNED_CERT_SHA256=='6580f2f9c1ae282d4fa05a20415c001eaa80e0d187ae8099ce1043c378dd0cb9','pinned stable identity changed unexpectedly'
    assert 'PDF_FOCUS_STABLE_CERT_SHA256' not in text,'the pinned release identity must never be overridable by the environment'
    workflows=pathlib.Path(__file__).parent.parent/'.github'/'workflows'
    for workflow in workflows.glob('*.yml'):
        assert 'PDF_FOCUS_STABLE_CERT_SHA256' not in workflow.read_text(),f'{workflow.name} must not override the pinned release identity'
    for line in text.splitlines():
        if 'subprocess.run' in line:
            assert not ('apksigner' in line and re.search(r'\baab\b',line,re.IGNORECASE)),'apksigner must never be invoked on the App Bundle: '+line
    print('sign-aab regression: jarsigner path verified, pinned identity enforced, apksigner absent from the bundle path')

if __name__=='__main__':
    main()
