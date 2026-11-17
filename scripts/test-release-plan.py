#!/usr/bin/env python3
import importlib.util
import pathlib
import unittest
spec=importlib.util.spec_from_file_location('plan', pathlib.Path(__file__).with_name('release-plan.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
class ReleasePlanTest(unittest.TestCase):
    def test_retries_are_idempotent(self):
        self.assertEqual(module.plan(42,'a'*40),module.plan(42,'a'*40))
    def test_new_build_has_higher_android_version(self):
        self.assertGreater(module.plan(43,'b'*40)['code'],module.plan(42,'a'*40)['code'])
        self.assertGreater(module.plan(42,'a'*40)['code'],12)
    def test_rejects_untrusted_or_invalid_values(self):
        for value in ('0','-2','2000000000','hello'):
            with self.assertRaises(ValueError):module.plan(value,'a'*40)
        with self.assertRaises(ValueError):module.plan(42,'main')
if __name__=='__main__':unittest.main()
