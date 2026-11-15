"""Fail-closed 26.1.2 paired CI provenance and archive integrity regression tests."""
import copy
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import verified_release_2612 as release

class ProvenanceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.source = Path(self.tmp.name)
        (self.source/'gradle.properties').write_text('mod_version=0.19.0\nminecraft_version=26.1.2\nmod_name=Singulo\n')
        self.sha, self.tested, self.tree = 'a'*40, 'b'*40, 'c'*40
        self.tag = 'v0.19.0-26.1.2N'
        jobs = [{'id': i+1, 'name': n, 'conclusion': 'success', 'started_at': '2026-11-15T10:00:00Z',
                 'completed_at': '2026-11-15T10:10:00Z'} for i,n in enumerate(['build-and-gametest','sodium-lighting'])]
        artifacts = [{'id': i+1, 'name': n+'-'+self.tested+'-attempt-1', 'expired': False,
                     'created_at': '2026-11-15T10:09:00Z','digest':'sha256:'+'d'*64}
                     for i,n in enumerate(['pr-validation','sodium-lighting'])]
        self.data = {'actions/runs/1': {'status':'completed','conclusion':'success','event':'pull_request',
                     'path':'.github/workflows/pr-validation.yml','head_repository':{'full_name':'owner/repo'},
                     'head_sha':self.sha,'run_attempt':1,'pull_requests':[{'number': 7}]},
                     'pulls/7':{'merged':True,'base':{'ref':'26.1.2'},'head':{'sha':self.sha,'repo':{'full_name':'owner/repo'}},'merge_commit_sha':self.sha},
                     'commits/'+self.sha+'/pulls?per_page=100':[],
                     'actions/runs/1/jobs?per_page=100':{'jobs':jobs},
                     'actions/runs/1/artifacts?per_page=100':{'total_count':2,'artifacts':artifacts},
                     'git/ref/tags/'+self.tag:{'object':{'type':'commit','sha':self.sha}},
                     'git/commits/'+self.sha:{'tree':{'sha':self.tree}},
                     'git/commits/'+self.tested:{'tree':{'sha':self.tree}},
                     'compare/'+self.sha+'...26.1.2':{'status':'identical'}}
    def inspect(self):
        with patch.object(release,'api',side_effect=lambda repo,e:copy.deepcopy(self.data[e])), patch.object(release,'git',
            side_effect=lambda source,*a:'' if a[0]=='status' else self.tree if a[-1]=='HEAD^{tree}' else self.sha):
            return release.inspect('owner/repo',1,self.tag,self.source)
    def artifacts(self): return self.data['actions/runs/1/artifacts?per_page=100']['artifacts']
    def test_two_verified_artifacts(self): self.assertEqual(set(self.inspect()['artifacts']),{'neoforge','sodium'})
    def test_missing_optional_artifact(self):
        self.artifacts().pop();self.data['actions/runs/1/artifacts?per_page=100']['total_count']=1
        with self.assertRaises(RuntimeError): self.inspect()
    def test_failed_optional_job(self):
        self.data['actions/runs/1/jobs?per_page=100']['jobs'][1]['conclusion']='failure'
        with self.assertRaises(RuntimeError): self.inspect()
    def test_extra_mekanism_job(self):
        self.data['actions/runs/1/jobs?per_page=100']['jobs'].append({'name':'optional-mekanism','conclusion':'success'})
        with self.assertRaises(RuntimeError): self.inspect()
    def test_both_artifact_digest_required(self):
        for i in range(2):
            old=self.artifacts()[i].pop('digest')
            with self.assertRaises(RuntimeError): self.inspect()
            self.artifacts()[i]['digest']=old
    def test_optional_stale_attempt(self):
        self.artifacts()[1]['created_at']='2026-11-15T09:09:00Z'
        with self.assertRaises(RuntimeError): self.inspect()
    def test_optional_future_attempt(self):
        self.artifacts()[1]['name']=self.artifacts()[1]['name'].replace('attempt-1','attempt-2')
        with self.assertRaises(RuntimeError): self.inspect()
    def test_optional_expired(self):
        self.artifacts()[1]['expired']=True
        with self.assertRaises(RuntimeError): self.inspect()
    def test_optional_other_tree(self):
        other='e'*40;self.artifacts()[1]['name']=self.artifacts()[1]['name'].replace(self.tested,other)
        self.data['git/commits/'+other]={'tree':{'sha':'f'*40}}
        with self.assertRaises(RuntimeError): self.inspect()
    def test_optional_other_commit_same_tree(self):
        other='e'*40;self.artifacts()[1]['name']=self.artifacts()[1]['name'].replace(self.tested,other)
        self.data['git/commits/'+other]={'tree':{'sha':self.tree}}
        with self.assertRaises(RuntimeError): self.inspect()
    def test_wrong_pr_base_rejected(self):
        self.data['pulls/7']['base']['ref']='1.21.1'
        with self.assertRaises(RuntimeError): self.inspect()
    def test_missing_pr_association_rejected(self):
        self.data['actions/runs/1']['pull_requests']=[]
        with self.assertRaises(RuntimeError): self.inspect()
    def test_fallback_merged_pr(self):
        self.data['actions/runs/1']['pull_requests']=[]
        self.data['commits/'+self.sha+'/pulls?per_page=100']=[{'number':7}]
        self.assertEqual(self.inspect()['merged_pr']['number'],7)
    def test_unmerged_pr(self):
        self.data['pulls/7']['merged']=False
        with self.assertRaises(RuntimeError): self.inspect()
    def test_wrong_head(self):
        self.data['pulls/7']['head']['sha']='e'*40
        with self.assertRaises(RuntimeError): self.inspect()
    def test_wrong_repo(self):
        self.data['pulls/7']['head']['repo']['full_name']='fork/repo'
        with self.assertRaises(RuntimeError): self.inspect()
    def test_merge_tree_mismatch(self):
        self.data['pulls/7']['merge_commit_sha']='f'*40
        self.data['git/commits/'+'f'*40]={'tree':{'sha':'e'*40}}
        with self.assertRaises(RuntimeError): self.inspect()
    def test_ambiguous_merged_pr(self):
        self.data['actions/runs/1']['pull_requests'].append({'number':8})
        self.data['pulls/8']=copy.deepcopy(self.data['pulls/7'])
        with self.assertRaises(RuntimeError): self.inspect()
    def test_incomplete_fallback_page(self):
        self.data['actions/runs/1']['pull_requests']=[]
        self.data['commits/'+self.sha+'/pulls?per_page=100']=[{'number':i+1} for i in range(100)]
        with self.assertRaises(RuntimeError): self.inspect()
    def test_duplicate_optional_artifact_rejected(self):
        self.artifacts().append(copy.deepcopy(self.artifacts()[1]))
        self.data['actions/runs/1/artifacts?per_page=100']['total_count']=3
        with self.assertRaises(RuntimeError): self.inspect()
    def test_pr_only(self):
        self.data['actions/runs/1']['event']='push'
        with self.assertRaises(RuntimeError): self.inspect()
    def test_tag_on_maintained_branch(self):
        self.data['compare/'+self.sha+'...26.1.2']['status']='diverged'
        with self.assertRaises(RuntimeError): self.inspect()
    def test_dirty_checkout(self):
        with patch.object(release,'git',return_value=' M modified'),self.assertRaises(RuntimeError):
            release.inspect('owner/repo',1,self.tag,self.source)
    def test_wrong_minecraft(self):
        (self.source/'gradle.properties').write_text('mod_version=0.19.0\nminecraft_version=1.21.1\nmod_name=Singulo')
        with self.assertRaises(RuntimeError): self.inspect()
    def test_duplicate_marker_rejected(self):
        (self.source/'log').write_text('OK\nOK\n')
        with self.assertRaises(RuntimeError): release.require_markers(self.source,'log',['OK'])
    def test_no_copy_without_visual_evidence(self):
        with patch.object(release,'validate_visual_evidence',side_effect=RuntimeError('missing')),patch.object(release.shutil,'copyfile') as cp:
            with self.assertRaises(RuntimeError): release.promote(self.inspect(),self.source,self.source/'output',self.source)
            cp.assert_not_called()

class OutlineTests(unittest.TestCase):
    def test_exact_contract(self):
        from PIL import Image
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);d=root/'outline-review/screenshots';d.mkdir(parents=True)
            logs=root/'outline-review/logs';logs.mkdir()
            log='SINGULO_OUTLINE_COMPLETE 4 5\n'+''.join(f'SINGULO_OUTLINE_ENTITY phase={p} index={i} redPixels=1 darkPixels=1\n' for p in range(5) for i in range(4))
            (logs/'latest.log').write_text(log)
            for i in range(5): Image.new('RGB',(1280,800)).save(d/f'outline-{i}.png')
            release.validate_outline(root)
            (logs/'latest.log').write_text(log+log.splitlines()[1])
            with self.assertRaises(RuntimeError):release.validate_outline(root)
            (logs/'latest.log').write_text(log)
            Image.new('RGB',(1,1)).save(d/'outline-4.png')
            with self.assertRaises(RuntimeError):release.validate_outline(root)

class ArchiveTests(unittest.TestCase):
    def archive(self,name='evidence.txt'):
        out=io.BytesIO()
        with zipfile.ZipFile(out,'w') as z:z.writestr(name,'verified')
        return out.getvalue()
    def metadata(self,data):return {'repository':'owner/repo','artifacts':{'neoforge':{'name':'artifact','id':1,'digest':'sha256:'+hashlib.sha256(data).hexdigest()}}}
    def test_download_and_tamper_detection(self):
        data=self.archive();m=self.metadata(data)
        with tempfile.TemporaryDirectory() as temp,patch.object(release.subprocess,'check_output',return_value=data):
            root=Path(temp);release.download(m,root);release.verify_download_receipts(m,root)
            (root/'artifact/evidence.txt').write_text('changed')
            with self.assertRaises(RuntimeError):release.verify_download_receipts(m,root)
    def test_hash_mismatch(self):
        with tempfile.TemporaryDirectory() as temp,patch.object(release.subprocess,'check_output',return_value=b'bad'):
            with self.assertRaises(RuntimeError):release.download(self.metadata(self.archive()),Path(temp))
    def test_traversal_rejected(self):
        data=self.archive('../outside')
        with tempfile.TemporaryDirectory() as temp,patch.object(release.subprocess,'check_output',return_value=data):
            with self.assertRaises(RuntimeError):release.download(self.metadata(data),Path(temp))

if __name__=='__main__': unittest.main()
