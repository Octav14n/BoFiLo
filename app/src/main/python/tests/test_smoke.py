"""
Integration smoke test: downloads AO3 story #2080878 ("I Am Groot")
using cached HTTP responses. No network access required.

Verifies:
- FanFicFare processes the story successfully
- An EPUB is produced with expected structure
- Handler callbacks fire (title, chapters, filename, finish)
- compat layer works end-to-end

Note: We do NOT inject MyFetcher here. On Android, AO3 requests go through
GeckoView via MyFetcher, but for testing we let FFF use its default
RequestsFetcher backed by BasicCache. This tests the FFF pipeline itself.
"""
import os
import pickle
import threading
import zipfile

import compat

FIXTURE_DIR = os.path.join(os.path.dirname(__file__), 'fixtures')
CACHE_FILE = os.path.join(FIXTURE_DIR, 'ao3_2080878_cache.pickle')
STORY_URL = 'https://archiveofourown.org/works/2080878'


def _make_test_adapter_wrapper(original_get_adapter, handler_fn):
    """Create an adapter wrapper that intercepts Story callbacks but does NOT
    inject MyFetcher. This lets the default RequestsFetcher + BasicCache work."""
    from helper import MyStory, MyAdapter

    def test_get_adapter(config, url, anyurl=False):
        adapter = original_get_adapter(config, url, anyurl)
        handler = handler_fn()
        if handler:
            handler.start(adapter.url)
        adapter.story = MyStory(adapter.story)
        return MyAdapter(adapter)

    return test_get_adapter


def _run_download(handler, tmp_path, include_images="false"):
    """Run FanFicFare with pre-loaded cache. Returns the handler."""
    from fanficfare.fetchers.cache_basic import BasicCache
    import helper

    os.chdir(str(tmp_path))

    # Register handler for this thread
    helper.handlers[threading.get_ident()] = handler

    # Wrap adapters for Story/Adapter interception, but NO MyFetcher
    original = compat.get_original_get_adapter()
    test_wrapper = _make_test_adapter_wrapper(original, helper.get_handler)
    compat.patch_get_adapter(test_wrapper)

    # Pre-load the cache — FFF's CLI checks hasattr(options, 'basic_cache')
    # but we can't easily inject into the options namespace from outside.
    # Instead, we use --save-cache which loads global_cache, and we place
    # our fixture as the global_cache file.
    cache_link = os.path.join(str(tmp_path), 'global_cache')
    os.symlink(CACHE_FILE, cache_link)

    default_ini = compat.read_defaults_ini()
    app_ini = "\n[defaults]\ninclude_images: %s\n" % include_images

    try:
        compat.cli_main(
            ['--save-cache', '--non-interactive', '--update-epub', STORY_URL],
            passed_personalini=app_ini,
            passed_defaultsini=default_ini,
        )
        handler.finish(True)
    except Exception as e:
        handler.finish(False)
        raise
    finally:
        # Restore original adapter
        compat.patch_get_adapter(original)

    return handler


class TestSmokeDownload:
    """End-to-end test using cached AO3 HTTP responses."""

    def test_download_produces_epub(self, tmp_path):
        """Download 'I Am Groot' from cache, verify EPUB output."""
        from helper import Handler
        handler = Handler(STORY_URL)
        _run_download(handler, tmp_path)

        assert handler.is_finished, "Handler.finish() was not called"

        # Find the EPUB
        epubs = [f for f in os.listdir(str(tmp_path)) if f.endswith('.epub')]
        assert len(epubs) == 1, "Expected 1 EPUB, found: %s" % epubs
        epub_path = os.path.join(str(tmp_path), epubs[0])

        # Verify EPUB structure
        with zipfile.ZipFile(epub_path) as z:
            names = z.namelist()
            assert 'mimetype' in names
            assert 'content.opf' in names
            xhtml_files = [n for n in names if n.endswith('.xhtml')]
            assert len(xhtml_files) >= 1, "No chapter files found in EPUB"

    def test_handler_receives_title(self, tmp_path):
        """Verify handler gets title from FFF via MyStory wrapper."""
        from helper import Handler
        handler = Handler(STORY_URL)
        _run_download(handler, tmp_path)

        # title is set via setMetadata('title', ...) -> handler.title(value)
        # which sets self._title
        assert handler._title is not None, "Handler never received title callback"
        assert "Groot" in handler._title, (
            "Expected 'Groot' in title, got: %s" % handler._title
        )

    def test_handler_receives_chapters(self, tmp_path):
        """Verify handler gets chapter progress callbacks."""
        from helper import Handler
        handler = Handler(STORY_URL)
        _run_download(handler, tmp_path)

        assert handler.chapters_max is not None, "Handler never received chapters callback"
        assert handler.chapters_max >= 1

    def test_fixture_exists(self):
        """Sanity check that the cache fixture file exists and is valid."""
        assert os.path.isfile(CACHE_FILE), "Missing fixture: %s" % CACHE_FILE

        with open(CACHE_FILE, 'rb') as f:
            cache = pickle.load(f)

        assert STORY_URL in cache, "Cache missing story URL"
        assert len(cache) >= 3, "Cache should have at least 3 URLs, has %d" % len(cache)
