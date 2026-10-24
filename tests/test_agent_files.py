"""One set of project rules for every coding agent: AGENTS.md. CLAUDE.md and GEMINI.md only import it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
# Claude Code and Gemini CLI both read "@path" lines as imports; Codex reads AGENTS.md itself.
POINTERS = {"CLAUDE.md": "@AGENTS.md", "GEMINI.md": "@./AGENTS.md"}


class AgentFilesTest(unittest.TestCase):
    def test_rules_live_in_agents_md(self):
        rules = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        for section in ("## How to work with the user", "## Setup on a new TV", "## Releases"):
            self.assertIn(section, rules)

    def test_pointers_only_import_agents_md(self):
        for name, line in POINTERS.items():
            text = (ROOT / name).read_text(encoding="utf-8")
            lines = text.strip().splitlines()
            self.assertEqual(lines[-1], line, f"{name} must end with the import {line}")
            self.assertEqual([l for l in lines if l.startswith("@")], [line], f"{name} imports only AGENTS.md")
            self.assertNotIn("## ", text, f"{name} has rules of its own: move them to AGENTS.md")
            self.assertLess(len(lines), 10, f"{name} has rules of its own: move them to AGENTS.md")


if __name__ == "__main__":
    unittest.main()
