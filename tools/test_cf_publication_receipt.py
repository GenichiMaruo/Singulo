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
        (d/'provenance.json').write_text(json.dumps({'tag':'v0.19.0-26.1.2N','commit':'a'*40,'run_attempt':1,'jar_sha256':{'neoforge':hashlib.sha256(b'jar').hexdigest()}}))
        (self.root/'cf-lock-receipt.json').write_text(json.dumps({'status':'atomic_lock_intent','ref':'refs/tags/singulo-cf-upload-lock-v1/v0.19.0-26.1.2N','commit':'a'*40}))
        self.env={'PUBLISH_OUTCOME':'success','CF_PROJECT':'1734662','CF_VERSION':'123',
                  'CF_URL':'https://www.curseforge.com/minecraft/mc-mods/singulo/files/123',
                  'CF_FILES':json.dumps([{'id':123,'name':self.name[:-4],'url':'https://cdn.example/file.jar'}]),'EXPECTED_FILE':self.name}
    def test_success(self):
        r,f=make_receipt(self.env,self.root);self.assertFalse(f);self.assertEqual(r['state'],'accepted')
    def test_real_9627445_display_name_output(self):
        self.env.update(CF_VERSION='9627445',CF_URL='https://www.curseforge.com/minecraft/mc-mods/singulo/files/9627445',
            CF_FILES=json.dumps([{'id':9627445,'name':'Singulo-0.19.0-26.1.2N',
                'url':'https://www.curseforge.com/api/v1/mods/1734662/files/9627445/download'}]))
        r,f=make_receipt(self.env,self.root)
        self.assertFalse(f);self.assertEqual(r['state'],'accepted')
        self.assertEqual(r['source_filename'],self.name)
        self.assertEqual(r['source_jar_sha256'],hashlib.sha256(b'jar').hexdigest())
        self.assertEqual(r['cdn_hash_verification'],'not_performed')
        self.assertEqual(r['remote_filename_verification'],'not_performed')
        self.assertNotIn('jar_sha256',r)
    def test_wrong_display_name_rejected(self):
        for name in (self.name,'Singulo-0.19.0-1.21.1N',''):
            self.env['CF_FILES']=json.dumps([{'id':123,'name':name,'url':'https://cdn.example/file.jar'}])
            self.assertTrue(make_receipt(self.env,self.root)[1])
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
    def test_wrong_lock_rejected(self):
        path=self.root/'cf-lock-receipt.json'
        original=json.loads(path.read_text())
        for field,value in [('status','acquired'),('ref','refs/tags/other'),('commit','b'*40)]:
            lock=dict(original);lock[field]=value;path.write_text(json.dumps(lock))
            r,f=make_receipt(self.env,self.root)
            self.assertTrue(f,field);self.assertEqual(r['state'],'unknown')
    def test_placeholder_lock_rejected(self):
        (self.root/'cf-lock-receipt.json').write_text('{"lock":"permanent"}')
        self.assertTrue(make_receipt(self.env,self.root)[1])
    def test_failure_unknown_and_no_secrets(self):
        self.env['PUBLISH_OUTCOME']='failure';self.env['TOKEN']='not-for-receipt'
        r,f=make_receipt(self.env,self.root);self.assertFalse(f);self.assertEqual(r['state'],'unknown')
        self.assertNotIn('not-for-receipt',json.dumps(r));self.assertFalse(r['automatic_retry'])

if __name__=='__main__':unittest.main()
