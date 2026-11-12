"""Guard legacy ShaderInstance blend metadata against opaque beam regressions."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent.parent


class LegacyRenderingTests(unittest.TestCase):
    def test_jet_beam_shader_keeps_render_type_additive_blending(self):
        shader = json.loads((ROOT / 'src/main/resources/assets/singulo/shaders/core/jet_beam.json').read_text())
        render_type = (ROOT / 'src/main/java/io/github/genichimaruo/singulo/client/JetForgeRenderTypes.java').read_text(encoding='utf-8')
        beam = render_type.split('static final RenderType BEAM_TYPE =', 1)[1].split('private JetForgeRenderTypes', 1)[0]
        self.assertIn('.setTransparencyState(LIGHTNING_TRANSPARENCY)', beam)
        # 1.20.1 ShaderInstance.apply applies its own blend after the RenderType state.
        # LIGHTNING_TRANSPARENCY uses SRC_ALPHA, ONE for both RGB and alpha.
        self.assertEqual(shader.get('blend'), {'func': 'add', 'srcrgb': 'srcalpha', 'dstrgb': '1'})


if __name__ == '__main__':
    unittest.main()
