#!/usr/bin/env python3
import importlib.util
import pathlib
import unittest
spec=importlib.util.spec_from_file_location('plan', pathlib.Path(__file__).with_name('release-plan.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
class ReleasePlanTest(unittest.TestCase):
    def test_first_merge_bootstraps_one_zero_zero(self):
        p=module.plan(123,'a'*40,[{'tag_name':'v0.10.11-test'}])
        self.assertEqual('v1.0.0',p['tag']);self.assertEqual(100000,p['code'])
    def test_next_merge_increments_and_retry_keeps_own_release(self):
        history=[{'tag_name':'v1.0.0','target_commitish':'a'*40}]
        p=module.plan(124,'b'*40,history)
        self.assertEqual('v1.0.1',p['tag']);self.assertEqual(100001,p['code'])
        history.append({'tag_name':'v1.0.1','target_commitish':'b'*40})
        self.assertEqual(p,module.plan(124,'b'*40,history))
        self.assertEqual('v1.0.0',module.plan(123,'a'*40,history)['tag'])
    def test_draft_version_reserved_never_replaced(self):
        history=[{'tag_name':'v1.0.3','target_commitish':'a'*40,'draft':True}]
        self.assertEqual('v1.0.4',module.plan(42,'b'*40,history)['tag'])
        self.assertEqual('v1.0.3',module.plan(42,'a'*40,history)['tag'])
    def test_rejects_invalid_values(self):
        for value in ('0','-2','hello'):
            with self.assertRaises(ValueError):module.plan(value,'a'*40)
        with self.assertRaises(ValueError):module.plan(42,'main')
if __name__=='__main__':unittest.main()
