from contextlib import redirect_stdout
import io
import tempfile
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

PATH = Path(__file__).resolve().parents[1] / "github_maintenance.py"
SPEC = importlib.util.spec_from_file_location("release_maintenance", PATH)
m = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(m)


BASELINES = {
    "innertubex": "0.11.7", "youtubejs": "18.3.0", "tombulled_innertube": "2.1.19",
    "youtubeexplode": "6.6.3", "youtube_transcript_api": "1.2.5", "ytmusicapi": "1.12.3",
    "yewtube": "2.13.1", "alaouy_youtube": "2.2.6",
}


def release(tag):
    return {"tag_name": tag, "draft": False, "prerelease": False,
            "published_at": "2026-11-13T14:59:23Z", "body": "synthetic-private-body",
            "author": {"login": "synthetic-private-author"},
            "html_url": "https://example.invalid/?token=synthetic-private-url"}


class API:
    def __init__(self):
        self.values = {project: release("v" + version) for project, version in BASELINES.items()}
        self.checked = []

    def latest_release(self, project):
        self.checked.append(project)
        value = self.values[project]
        if isinstance(value, Exception): raise value
        return value

    def request(self, method, path, payload=None):
        assert method == "GET"
        if path.startswith(("/issues?", "/pulls?")): return []
        if path.startswith("/branches?"):
            return [{"name": "main", "protected": False, "commit": {"sha": "a" * 40}}]
        if path == "": return {"default_branch": "main", "delete_branch_on_merge": True}
        raise AssertionError(path)


class Response:
    def __init__(self, status=200, body=b"{}", headers=None):
        self.status, self.body, self.headers = status, body, headers or {}
        self.reads = 0
    def getheader(self, name, default=None): return self.headers.get(name, default)
    def read(self, limit):
        self.reads += 1
        self.limit = limit
        return self.body[:limit]


class Connection:
    def __init__(self, response):
        self.response, self.requests, self.closed = response, [], False
    def request(self, method, target, body=None, headers=None): self.requests.append((method, target, headers))
    def getresponse(self): return self.response
    def close(self): self.closed = True


class CompetitorReleaseTests(unittest.TestCase):
    def test_owner_selected_projects_are_all_observed_once(self):
        api = API()
        observations = m.observe_competitor_releases(api)
        self.assertEqual(list(BASELINES), api.checked)
        self.assertEqual(list(BASELINES), [item["project"] for item in observations])
        self.assertEqual(8, len(observations))

    def test_versions_are_metadata_only_and_changes_need_review(self):
        api = API()
        observations = m.observe_competitor_releases(api)
        self.assertEqual(["MATCHES_BASELINE"] * 8, [x["status"] for x in observations])
        self.assertEqual(list(BASELINES.values()), [x["latest_version"] for x in observations])
        report = {"issues": [{"body": "synthetic-private-issue"}], "pull_requests": [],
                  "mode": "dry-run", "label_updates": 0, "competitor_check": "REQUESTED",
                  "competitor_releases": observations}
        summary = m.render_summary(report)
        self.assertNotIn("synthetic-private", json.dumps(observations) + summary)
        self.assertIn("0.11.7", summary)
        api.values["innertubex"] = release("v0.12.0")
        self.assertEqual("REVIEW_NEEDED", m.observe_competitor_releases(api)[0]["status"])
        for field, value in [("project", "@someone"), ("status", "unknown\n@someone"),
                             ("latest_version", "1.2.3|@someone")]:
            bad = dict(observations[0], **{field: value})
            with self.assertRaises(ValueError):
                m.render_summary(dict(report, competitor_releases=[bad, *observations[1:]]))

    def test_unusual_tags_drafts_and_invalid_dates_are_not_reported_as_versions(self):
        for changes in [{"tag_name": "v1.2.3?token=synthetic-private"},
                        {"tag_name": "v1.2.3-" + "x" * 100}, {"draft": True},
                        {"prerelease": True}, {"published_at": "2026-99-99T00:00:00Z"},
                        {"published_at": "synthetic-private-date"}, {"tag_name": None}]:
            api = API()
            api.values["innertubex"].update(changes)
            result = m.observe_competitor_releases(api)
            self.assertEqual("UNVERIFIED_METADATA", result[0]["status"])
            self.assertEqual("MATCHES_BASELINE", result[1]["status"])
            self.assertNotIn("synthetic-private", json.dumps(result))

    def test_failure_states_are_retained_without_aborting_the_repository_audit(self):
        for error, expected in [(m.GitHubHttpError(403), "ACCESS_BLOCKED"),
                                (m.GitHubHttpError(429), "RATE_LIMITED"),
                                (m.GitHubHttpError(403, rate_limited=True), "RATE_LIMITED"),
                                (m.GitHubHttpError(503), "HTTP_ERROR"),
                                (OSError("synthetic-private-network"), "NETWORK_ERROR"),
                                (m.GitHubResponseLimitError(), "RESPONSE_TOO_LARGE")]:
            api = API(); api.values["innertubex"] = error
            report = m.snapshot(api, check_competitors=True)
            self.assertEqual(expected, report["competitor_releases"][0]["status"])
            self.assertEqual("REQUESTED", report["competitor_check"])
            self.assertTrue(report["inventory_complete"])
            self.assertFalse(report["branch_deletion_enabled"])
            self.assertNotIn("synthetic-private", json.dumps(report))
        api = API()
        self.assertEqual("NOT_REQUESTED", m.snapshot(api)["competitor_check"])
        self.assertEqual([], api.checked)
        api.values["innertubex"] = ValueError("unexpected policy bug")
        with self.assertRaises(ValueError): m.observe_competitor_releases(api)

    def test_cli_check_writes_only_sanitized_report_and_summary_without_label_writes(self):
        api = API()
        with tempfile.TemporaryDirectory() as directory:
            output, summary = Path(directory) / "report.json", Path(directory) / "summary.md"
            argv = ["maintenance", "--repo", "bxanedot/eXcor", "--output", str(output),
                    "--summary-output", str(summary), "--check-competitors"]
            with patch.object(m.sys, "argv", argv), patch.object(m, "GitHub", return_value=api), redirect_stdout(io.StringIO()):
                self.assertEqual(0, m.main())
            report = json.loads(output.read_text())
            self.assertEqual("dry-run", report["mode"])
            self.assertEqual(0, report["label_updates"])
            self.assertEqual(list(BASELINES), api.checked)
            self.assertNotIn("synthetic-private", output.read_text() + summary.read_text())
            self.assertIn("MATCHES_BASELINE", summary.read_text())

    def test_public_routes_drop_authorization_and_reject_other_targets_or_writes(self):
        connection = Connection(Response())
        with patch.dict("os.environ", {"HTTPS_PROXY": ""}), patch.object(m.http.client, "HTTPSConnection", return_value=connection) as connect:
            api = m.GitHub("bxanedot/eXcor", "synthetic-auth")
            for project in BASELINES:
                api.latest_release(project)
            self.assertEqual(8, len(connection.requests))
            for method, target, headers in connection.requests:
                self.assertEqual("GET", method)
                self.assertTrue(target.endswith("/releases/latest"))
                self.assertNotIn("Authorization", headers)
            api.request("GET", "")
            self.assertEqual("Bearer synthetic-auth", connection.requests[-1][2]["Authorization"])
            count = connect.call_count
            for method, path in [("POST", "/repos/MetrolistGroup/innertubex/releases/latest"),
                                 ("GET", "/repos/MetrolistGroup/innertubex/issues"),
                                 ("GET", "https://attacker.invalid/"), ("GET", "/repos/other/repo/releases/latest")]:
                with self.assertRaises(ValueError): api.request(method, path, public_release=True)
            with self.assertRaises(ValueError): api.latest_release("../other")
            self.assertEqual(count, connect.call_count)
            self.assertTrue(connection.closed)

    def test_valid_public_targets_refuse_redirects_and_bound_response_bodies(self):
        for status, headers, limited in [(302, {}, False), (403, {}, False),
                                          (403, {"X-RateLimit-Remaining": "0"}, True),
                                          (403, {"Retry-After": "60"}, True), (429, {}, True)]:
            response = Response(status, headers=headers)
            connection = Connection(response)
            with patch.dict("os.environ", {"HTTPS_PROXY": ""}), patch.object(m.http.client, "HTTPSConnection", return_value=connection):
                with self.assertRaises(m.GitHubHttpError) as failure:
                    m.GitHub("bxanedot/eXcor").latest_release("innertubex")
                self.assertEqual(status, failure.exception.status_code)
                self.assertEqual(limited, failure.exception.rate_limited)
                self.assertEqual(0, response.reads)
                self.assertTrue(connection.closed)
        response = Response(body=b"x" * (m.MAX_BODY + 1))
        connection = Connection(response)
        with patch.dict("os.environ", {"HTTPS_PROXY": ""}), patch.object(m.http.client, "HTTPSConnection", return_value=connection):
            with self.assertRaises(m.GitHubResponseLimitError):
                m.GitHub("bxanedot/eXcor").latest_release("youtubejs")
            self.assertEqual(m.MAX_BODY + 1, response.limit)
            self.assertTrue(connection.closed)


if __name__ == "__main__": unittest.main()
