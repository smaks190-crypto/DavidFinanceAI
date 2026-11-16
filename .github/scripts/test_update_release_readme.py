import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from release_readme import update_readme

SCRIPTS = Path(__file__).resolve().parent
README = "Before\n<!-- LATEST_RELEASE_START -->\nOld release\n<!-- LATEST_RELEASE_END -->\nAfter\n"


class ReadmeRenderingTest(unittest.TestCase):
    def test_preserves_surrounding_document_and_uses_current_repository(self):
        result = update_readme(README, "11.23", 481, "Исправлены платежи", "owner/new-repo")
        self.assertTrue(result.startswith("Before\n"))
        self.assertTrue(result.endswith("\nAfter\n"))
        self.assertIn("Версия v11.23 (сборка 481)", result)
        self.assertIn("https://github.com/owner/new-repo/releases/latest/download/app-release.apk", result)
        self.assertNotIn("Old release", result)
        self.assertEqual(result, update_readme(result, "11.23", 481, "Исправлены платежи", "owner/new-repo"))

    def test_rejects_missing_duplicate_reversed_or_injected_markers(self):
        for content in ("No markers", README + README, "<!-- LATEST_RELEASE_END --><!-- LATEST_RELEASE_START -->"):
            with self.assertRaises(ValueError):
                update_readme(content, "11.23", 481, "Notes", "owner/repo")
        with self.assertRaises(ValueError):
            update_readme(README, "11.23", 481, "<!-- LATEST_RELEASE_END -->", "owner/repo")

    def test_rejects_invalid_version_build_or_repository(self):
        for version, build, repo in (("invalid", 1, "a/b"), ("11.23", 0, "a/b"), ("11.23", 1, "a/b/c")):
            with self.assertRaises(ValueError):
                update_readme(README, version, build, "Notes", repo)


class ProtectedBranchProposalTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="release-readme-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.remote = self.root / "remote.git"
        self.checkout = self.root / "checkout"
        self.git("init", "--bare", "--initial-branch=main", str(self.remote), cwd=self.root)
        self.git("clone", str(self.remote), str(self.checkout), cwd=self.root)
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.invalid")
        (self.checkout / "README.md").write_text(README)
        self.git("add", "README.md")
        self.git("commit", "-m", "Initial document")
        self.git("push", "origin", "HEAD:main")
        self.main_before = self.git("rev-parse", "main", cwd=self.remote).strip()
        hook = self.remote / "hooks/pre-receive"
        hook.write_text("#!/bin/sh\nwhile read old new ref; do\n  if [ \"$ref\" = refs/heads/main ]; then exit 1; fi\ndone\n")
        hook.chmod(0o755)
        fakebin = self.root / "bin"
        fakebin.mkdir()
        gh = fakebin / "gh"
        gh.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
args = sys.argv[1:]
root = pathlib.Path(os.environ["FAKE_GH_ROOT"])
with (root / "gh-calls.jsonl").open("a") as f: f.write(json.dumps(args) + "\\n")
if args[:2] == ["release", "view"]:
    print("Исправлены платежи")
elif args[:2] == ["pr", "list"]:
    state = root / "pr-url"
    if state.exists(): print(state.read_text())
elif args[:2] == ["pr", "create"]:
    if os.environ.get("DENY_PR_CREATION") == "1":
        print("GitHub Actions cannot create pull requests", file=sys.stderr)
        sys.exit(1)
    url = "https://github.com/owner/repo/pull/12"
    (root / "pr-url").write_text(url)
    print(url)
else:
    sys.exit(2)
''')
        gh.chmod(0o755)
        self.env = dict(os.environ, PATH=str(fakebin) + os.pathsep + os.environ["PATH"],
                        FAKE_GH_ROOT=str(self.root), GITHUB_REPOSITORY="owner/repo",
                        RELEASE_VERSION="11.23", GITHUB_RUN_NUMBER="481",
                        RUNNER_TEMP=str(self.root), GITHUB_STEP_SUMMARY=str(self.root / "summary.md"))
        self.branch = "docs/release-readme-v11.23-build.481"

    def git(self, *args, cwd=None):
        return subprocess.check_output(["git", *args], cwd=cwd or self.checkout,
                                       stderr=subprocess.STDOUT, text=True)

    def propose(self, **env):
        return subprocess.run(["bash", str(SCRIPTS / "propose_release_readme.sh")], cwd=self.checkout,
                              env=dict(self.env, **env), text=True, capture_output=True, timeout=20)

    def assert_branch_is_reviewable_and_main_unchanged(self):
        self.assertEqual(self.main_before, self.git("rev-parse", "main", cwd=self.remote).strip())
        content = self.git("show", self.branch + ":README.md", cwd=self.remote)
        self.assertIn("Версия v11.23 (сборка 481)", content)
        self.assertIn("Исправлены платежи", content)

    def test_proposes_a_pr_without_pushing_protected_main_and_retry_is_idempotent(self):
        result = self.propose()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assert_branch_is_reviewable_and_main_unchanged()
        head = self.git("rev-parse", self.branch, cwd=self.remote)
        result = self.propose()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(head, self.git("rev-parse", self.branch, cwd=self.remote))
        calls = [json.loads(s) for s in (self.root / "gh-calls.jsonl").read_text().splitlines()]
        creates = [a for a in calls if a[:2] == ["pr", "create"]]
        self.assertEqual(1, len(creates))
        self.assertEqual("main", creates[0][creates[0].index("--base") + 1])
        self.assertEqual(self.branch, creates[0][creates[0].index("--head") + 1])
        self.assertIn("/pull/12", (self.root / "summary.md").read_text())

    def test_denied_automatic_pr_still_keeps_branch_and_explicit_review_link(self):
        result = self.propose(DENY_PR_CREATION="1")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assert_branch_is_reviewable_and_main_unchanged()
        self.assertIn("::notice::", result.stdout)
        self.assertIn("compare/main..." + self.branch, (self.root / "summary.md").read_text())


if __name__ == "__main__":
    unittest.main()
