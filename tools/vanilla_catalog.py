"""Minecraft 1.21.1 item registry snapshot used by offline recipe tools."""
import json
from pathlib import Path


DIRECTORY = Path(__file__).with_name('vanilla_1_21_1')
ITEM_NAMES = json.loads((DIRECTORY / 'items.json').read_text(encoding='utf-8'))
TAG_MEMBERS = json.loads((DIRECTORY / 'ingredient_tags.json').read_text(encoding='utf-8'))
# The client renders the sheet in ascending registry ID order, 32 icons per row.
ICON_INDEX = {item: index for index, item in enumerate(sorted(ITEM_NAMES))}
