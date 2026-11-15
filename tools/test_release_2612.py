"""26.1.2 packaging safety guards remain fail-closed."""
from pathlib import Path
import tempfile
import unittest
import zipfile
import check_release_2612 as release

META = """[[mods]]
modId="singulo"
version="0.19.0"
[[dependencies.singulo]]
modId="minecraft"
type="required"
versionRange="[26.1.2]"
[[dependencies.singulo]]
modId="neoforge"
type="required"
versionRange="[26.1.2.126,)"
"""
class DistributionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
    def jar(self, name="Singulo-0.19.0-26.1.2N.jar", meta=META, extra=None, major=69):
        path=self.root/name
        with zipfile.ZipFile(path,'w') as z:
            z.writestr('META-INF/neoforge.mods.toml',meta)
            z.writestr('io/github/genichimaruo/singulo/Singulo.class',bytes.fromhex('cafebabe0000')+major.to_bytes(2,'big'))
            for n in extra or []: z.writestr(n,b'')
        return path
    def test_java25_neoforge_distribution(self):
        release.validate_distribution(self.jar())
    def test_newer_class_rejected(self):
        with self.assertRaisesRegex(RuntimeError,'Java 25'): release.validate_distribution(self.jar(major=70))
    def test_old_minecraft_rejected(self):
        with self.assertRaises(RuntimeError): release.validate_distribution(self.jar(meta=META.replace('[26.1.2]','[1.21.1]')))
    def test_mismatched_name_rejected(self):
        with self.assertRaises(RuntimeError): release.validate_distribution(self.jar(name='Singulo-0.19.0-1.21.1N.jar'))
    def test_optional_loader_rejected(self):
        with self.assertRaises(RuntimeError): release.validate_distribution(self.jar(meta=META.replace('type="required"','type="optional"')))
    def test_development_and_mekanism_leaks_rejected(self):
        for entry in ['a/FooGameTests.class','a/ReviewHarness.class','a/ReviewScreenshot.class','a/ReviewLightmap.class','a/test/harness/Helper.class','singulo-test-suites.list','a/compat/mekanism/Bridge.class']:
            with self.subTest(entry=entry),self.assertRaises(RuntimeError): release.validate_distribution(self.jar(extra=[entry]))
    def test_duplicate_entries_rejected(self):
        with self.assertRaises(RuntimeError): release.validate_distribution(self.jar(extra=['x','x']))
class CompletionTests(unittest.TestCase):
    def test_exact_custom_plus_vanilla(self):
        release.validate_completion('All 816 required tests passed\nSINGULO_VANILLA_TEST minecraft:always_pass passed required=false',815)
    def test_missing_duplicate_or_failed_vanilla_rejected(self):
        for marker in ['', 'SINGULO_VANILLA_TEST minecraft:always_pass failed required=false', 'SINGULO_VANILLA_TEST minecraft:always_pass passed required=true', 'SINGULO_VANILLA_TEST minecraft:other passed required=false', 'SINGULO_VANILLA_TEST minecraft:always_pass passed required=false\n'*2]:
            with self.subTest(marker=marker), self.assertRaises(RuntimeError):
                release.validate_completion('All 816 required tests passed\n'+marker,815)
    def test_custom_only_total_rejected(self):
        with self.assertRaises(RuntimeError): release.validate_completion('All 815 required tests passed\nSINGULO_VANILLA_TEST minecraft:always_pass passed required=false',815)
if __name__=='__main__': unittest.main()
