#!/usr/bin/env python3
"""Regression: the 16 KB check must skip a bundle's 32-bit splits while still enforcing
64-bit alignment, and keep APK inputs strict.

App Bundles legitimately ship armeabi-v7a/x86 splits; Google Play's 16 KB page-size
requirement applies to 64-bit native code only. The previous version raised on any
32-bit ELF, so the release workflow's own invocation on app-release.aab could never
pass. This test fails on that version (check_apk raises on the ok.aab fixture).

Pure Python, no network, no secret.
"""
import importlib.util,pathlib,struct,tempfile,zipfile

SCRIPT=pathlib.Path(__file__).with_name('check-16kb-alignment.py')

def elf64(align):
    data=bytearray(128)
    data[:4]=b'\x7fELF';data[4]=2
    struct.pack_into('<Q',data,32,64)
    struct.pack_into('<H',data,54,56)
    struct.pack_into('<H',data,56,1)
    struct.pack_into('<I',data,64,1)
    struct.pack_into('<Q',data,64+48,align)
    return bytes(data)

def elf32():
    data=bytearray(64);data[:4]=b'\x7fELF';data[4]=1;return bytes(data)

def fixture(path,entries):
    with zipfile.ZipFile(path,'w') as archive:
        for name,blob in entries.items():
            archive.writestr(name,blob)

def main():
    spec=importlib.util.spec_from_file_location('check16',SCRIPT)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    with tempfile.TemporaryDirectory() as tmp:
        tmp=pathlib.Path(tmp)
        ok=tmp/'ok.aab';fixture(ok,{'base/lib/arm64-v8a/liba.so':elf64(16384),'base/lib/armeabi-v7a/liba.so':elf32(),'base/lib/x86/liba.so':elf32()})
        assert module.check_apk(str(ok))==[],'bundle with 32-bit splits and aligned 64-bit libraries must pass'
        bad=tmp/'bad.aab';fixture(bad,{'base/lib/x86_64/liba.so':elf64(4096),'base/lib/x86/liba.so':elf32()})
        assert module.check_apk(str(bad)),'misaligned 64-bit library inside a bundle must be reported'
        apk=tmp/'wrong.apk';fixture(apk,{'lib/armeabi-v7a/liba.so':elf32()})
        try:
            module.check_apk(str(apk))
            raise SystemExit('32-bit library inside an APK under test must stay an error')
        except ValueError:
            pass
    print('16kb-aab regression: 32-bit bundle splits skipped, 64-bit alignment enforced, APK inputs stay strict')

if __name__=='__main__':
    main()
