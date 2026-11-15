"""Prevent a deferred Minecraft.stop() from reentering consumed shader evidence."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent.parent
CLIENT = ROOT / 'tools/review/cosmic-src/io/github/genichimaruo/singulo/client'

class CosmicLifecycleTests(unittest.TestCase):
    def test_completion_is_terminal_before_reload_branch(self):
        source = (CLIENT / 'CosmicWorldReviewHarness.java').read_text(encoding='utf-8')
        tick = source[source.index('public static void tick('):]
        guard = tick.index('if(complete)return;')
        self.assertLess(guard, tick.index('if(reload!=null)'))
        finish = tick.index('complete=true;')
        self.assertLess(tick.index('reload.join();'), finish)
        self.assertLess(tick.index('CosmicGeometryReviewHarness.afterReload(oldShaders);'), finish)
        self.assertLess(finish, tick.index('SINGULO_COSMIC_COMPLETE 18'))
        self.assertLess(finish, tick.index('mc.stop();'))
        self.assertEqual(source.count('complete=true;'), 1)

    def test_consumed_shader_capture_fails_closed(self):
        source = (CLIENT / 'CosmicGeometryReviewHarness.java').read_text(encoding='utf-8')
        after = source[source.index('static void afterReload('):source.index('static void verify(')]
        self.assertIn('previous.size()!=2', after)
        self.assertIn('tracked.size()!=2', after)
        self.assertLess(after.index('!tracked.containsKey(old)'), after.index('var current=shaders();'))
        self.assertIn('throw new IllegalStateException("Missing or already-consumed', after)
        self.assertLess(after.index('tracked.clear();'), after.index('SINGULO_COSMIC_SHADER_RELOAD_COMPLETE'))

if __name__ == '__main__':
    unittest.main()
