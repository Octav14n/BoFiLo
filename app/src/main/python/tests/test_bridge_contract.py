"""
Bridge contract test: ensures Python handler interface matches
what Kotlin's StoryDownloadHelper exposes to Chaquopy.

If this test fails, either:
- A Kotlin method was renamed/removed (fix StoryDownloadHelper.kt)
- A Python method was renamed/removed (fix helper.py Handler class)
- ProGuard stripped a method (fix proguard-rules.pro)
"""
import inspect
import compat
from helper import Handler

# Methods that Python calls on the Kotlin handler object (via Chaquopy).
# Keys are method names; values are expected parameter names (excluding 'self').
BRIDGE_METHODS = {
    'add_output':   ['output'],
    'start':        ['url'],
    'title':        ['value'],
    'filename':     ['value'],
    'web_request':  ['method', 'url'],
    'get_login':    ['password_only'],
    'is_adult':     [],
    'chapters':     ['now', 'max'],
    'finish':       ['success'],
}


class TestBridgeContract:
    """Verify the Python Handler has all methods Kotlin expects."""

    def test_all_bridge_methods_exist(self):
        handler = Handler("https://example.com/test")
        for method_name in BRIDGE_METHODS:
            assert hasattr(handler, method_name), (
                "Handler missing method '%s' — Kotlin's StoryDownloadHelper "
                "calls this via Chaquopy" % method_name
            )

    def test_bridge_method_signatures(self):
        for method_name, expected_params in BRIDGE_METHODS.items():
            method = getattr(Handler, method_name)
            sig = inspect.signature(method)
            # Get params excluding 'self'
            params = [
                p.name for p in sig.parameters.values()
                if p.name != 'self'
                and p.kind not in (p.VAR_POSITIONAL, p.VAR_KEYWORD)
            ]
            assert len(params) == len(expected_params), (
                "Handler.%s has %d params %s, expected %d %s" % (
                    method_name, len(params), params,
                    len(expected_params), expected_params
                )
            )

    def test_handler_callable(self):
        """Verify Handler can be instantiated and basic methods work."""
        handler = Handler("https://example.com/test")
        handler.add_output("test output")
        assert handler.output == "test output"

        # title() is a method that sets self._title
        Handler.title(handler, "Test Title")
        assert handler._title == "Test Title"

        handler.chapters(3, 10)
        assert handler.chapters_now == 3
        assert handler.chapters_max == 10

        handler.finish(True)
        assert handler.is_finished is True


class TestCompatVerify:
    """Verify the FanFicFare compatibility layer."""

    def test_verify_passes(self):
        compat.verify()

    def test_fff_version_parsed(self):
        assert isinstance(compat.FFF_VERSION, tuple)
        assert len(compat.FFF_VERSION) >= 2
        assert all(isinstance(v, int) for v in compat.FFF_VERSION)

    def test_exports_exist(self):
        assert compat.Fetcher is not None
        assert compat.FetcherResponse is not None
        assert compat.FailedToLogin is not None
        assert compat.AdultCheckRequired is not None
        assert callable(compat.cli_main)
        assert callable(compat.read_defaults_ini)
        assert callable(compat.get_original_get_adapter)
