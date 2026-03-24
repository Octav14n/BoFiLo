"""
Versioned compatibility layer for FanFicFare internals.

Every FanFicFare internal accessed by BoFiLo is documented and validated here.
When helper.py needs something from FanFicFare, it imports from this module
instead of reaching into FFF directly. This way:

1. All monkey-patch points are documented in one place
2. verify() catches breakage immediately on import (not at runtime mid-download)
3. FanFicFare version upgrades only require changes to this file

Validated against FanFicFare 4.55.0.
When updating FanFicFare, run: .venv/bin/python -m pytest app/src/main/python/tests/ -v
"""

import os
import fanficfare
import fanficfare.cli
import fanficfare.fetchers.base_fetcher

try:
    FFF_VERSION = tuple(int(x) for x in fanficfare.__version__.split('.'))
except AttributeError:
    # FanFicFare doesn't set __version__; fall back to importlib.metadata
    import importlib.metadata
    FFF_VERSION = tuple(int(x) for x in importlib.metadata.version('FanFicFare').split('.'))

# ---------------------------------------------------------------------------
# Registry of every FanFicFare internal we depend on.
# Format: (module, attribute_name, human-readable label)
# ---------------------------------------------------------------------------

REQUIRED_ATTRS = [
    (fanficfare.cli, 'adapters', 'cli.adapters'),
    (fanficfare.cli, 'call', 'cli.call'),
    (fanficfare.cli, 'main', 'cli.main'),
    (fanficfare.cli, 'exceptions', 'cli.exceptions'),
    (fanficfare.cli.adapters, 'getAdapter', 'cli.adapters.getAdapter'),
    (fanficfare.fetchers.base_fetcher, 'Fetcher', 'fetchers.base_fetcher.Fetcher'),
    (fanficfare.fetchers.base_fetcher, 'FetcherResponse', 'fetchers.base_fetcher.FetcherResponse'),
]

REQUIRED_EXCEPTIONS = [
    (fanficfare.cli.exceptions, 'FailedToLogin'),
    (fanficfare.cli.exceptions, 'AdultCheckRequired'),
]


class IncompatibleFFFError(Exception):
    """Raised when FanFicFare internals we depend on are missing."""
    def __init__(self, missing, version):
        details = ', '.join(missing)
        super().__init__(
            "FanFicFare %s is incompatible with BoFiLo: "
            "missing %s. Update compat.py for this version." % (
                '.'.join(str(v) for v in version), details
            )
        )


def verify():
    """Validate all required FFF internals exist. Call at import time."""
    missing = []
    for module, attr, label in REQUIRED_ATTRS:
        if not hasattr(module, attr):
            missing.append(label)
    for module, attr in REQUIRED_EXCEPTIONS:
        if not hasattr(module, attr):
            missing.append('exceptions.%s' % attr)
    if missing:
        raise IncompatibleFFFError(missing, FFF_VERSION)


# ---------------------------------------------------------------------------
# Controlled mutation — these are the monkey-patches, wrapped so helper.py
# never touches FFF internals directly.
# ---------------------------------------------------------------------------

def get_original_get_adapter():
    """Return the original (unpatched) adapters.getAdapter function."""
    return fanficfare.cli.adapters.getAdapter


def patch_call(replacement):
    """Replace fanficfare.cli.call (subprocess.call) with a no-op for Android."""
    fanficfare.cli.call = replacement


def patch_get_adapter(replacement):
    """Replace adapters.getAdapter with our wrapper that injects MyStory/MyAdapter."""
    fanficfare.cli.adapters.getAdapter = replacement


# ---------------------------------------------------------------------------
# Re-exports — so helper.py imports everything from here, not from FFF.
# ---------------------------------------------------------------------------

Fetcher = fanficfare.fetchers.base_fetcher.Fetcher
FetcherResponse = fanficfare.fetchers.base_fetcher.FetcherResponse
FailedToLogin = fanficfare.cli.exceptions.FailedToLogin
AdultCheckRequired = fanficfare.cli.exceptions.AdultCheckRequired
cli_main = fanficfare.cli.main


def read_defaults_ini():
    """Read FanFicFare's bundled defaults.ini."""
    ini_path = os.path.join(os.path.dirname(fanficfare.__file__), 'defaults.ini')
    with open(ini_path, 'r', encoding='utf-8') as f:
        return f.read()
