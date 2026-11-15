import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from cf_publication_receipt import make_receipt

class ReceiptTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name); d=self.root/'release-artifacts';d.mkdir()
        self.name='Singulo-0.19.0-26.1.2N.jar';(d/self.name).write_bytes(b'jar')
        (d/'provenance.json').write_text(json.dumps({'run_attempt':1,'jar_sha256':{'neoforge':hashlib.sha256(b'jar').hexdigest()}}))
        (self.root/'cf-lock-receipt.json').write_text('{"lock":"permanent"}')
        self.env={'PUBLISH_OUTCOME':'success','CF_PROJECT':'1734662','CF_VERSION':'123',
                  'CF_URL':'https://www.curseforge.com/minecraft/mc-mods/singulo/files/123',
                  'CF_FILES':json.dumps([{'id':123,'name':self.name,'url':'https://cdn.example/file.jar'}]),'EXPECTED_FILE':self.name}
    def test_success(self):
        r,f=make_receipt(self.env,self.root);self.assertFalse(f);self.assertEqual(r['state'],'accepted')
    def test_numeric_project_url(self):
        self.env['CF_URL']=self.env['CF_URL'].replace('/singulo/','/1734662/')
        self.assertEqual(make_receipt(self.env,self.root)[0]['state'],'accepted')
    def test_wrong_file(self):
        self.env['CF_VERSION']='124';r,f=make_receipt(self.env,self.root)
        self.assertTrue(f);self.assertEqual(r['state'],'unknown')
    def test_missing_outputs(self):
        self.env['CF_FILES']='';self.assertTrue(make_receipt(self.env,self.root)[1])
    def test_hash_tamper(self):
        (self.root/'release-artifacts'/self.name).write_bytes(b'bad')
        self.assertTrue(make_receipt(self.env,self.root)[1])
    def test_failure_unknown_and_no_secrets(self):
        self.env['PUBLISH_OUTCOME']='failure';self.env['TOKEN']='not-for-receipt'
        r,f=make_receipt(self.env,self.root);self.assertFalse(f);self.assertEqual(r['state'],'unknown')
        self.assertNotIn('not-for-receipt',json.dumps(r));self.assertFalse(r['automatic_retry'])

if __name__=='__main__':unittest.main()
