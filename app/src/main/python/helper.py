#!/usr/bin/env python3
import sys


# wrapper for sys.stdout to redirect it to ui console
class MyStdOut:
    def __init__(self, out, prefix=''):
        self.stdout = out
        self.prefix = prefix

    def write(self, output):
        handler = get_handler()
        if handler:
            handler.add_output(self.prefix + output)
        else:
            self.stdout.write(self.prefix + output)

    def __getattr__(self, attr):
        return getattr(self.stdout, attr)


sys.stdout = MyStdOut(sys.stdout)
sys.stderr = MyStdOut(sys.stderr, "\nError: ")

import fanficfare.fetcher
import fanficfare.cli
import os
import threading

# read defaults.ini
with open(os.path.dirname(fanficfare.__file__) + '/defaults.ini', 'r', encoding="utf-8") as f:
    default_ini = f.read()
# no personal ini provided by default
personal_ini = None
originalGetAdapter = fanficfare.cli.adapters.getAdapter
handlers = dict()
handler_thread_ident = 0


# Calling scripts/anything is not allowed/possible/easy on android
def my_call(*popenargs, timeout=None, **kwargs):
    pass


fanficfare.cli.call = my_call


class Handler:
    def __init__(self, url: str):
        self.chapters_now = 0
        self.chapters_max = None
        self.title = None
        self.url = url
        self.filename = None
        self.output = ''
        self.is_finished = False

    def reset(self):
        self.output = ''
        self.is_finished = False

    def chapters(self, chapters_now: int, chapters_max: int):
        self.chapters_now = chapters_now
        self.chapters_max = chapters_max

    def get_login(self, password_only: bool = False):
        raise ValueError('no [password, username] supplied.')

    def web_request(self, method, url, **kargs):
        print('can not perform web request "%s" for url "%s", args: ' % (method, url), kargs)
        raise NotImplementedError('can not perform web request "%s" for url "%s" [args: %s]' % (method, url, str(kargs)))

    def is_adult(self):
        return False

    def start(self, url: str):
        pass

    def add_output(self, txt: str):
        self.output += txt

    def finish(self, success):
        self.is_finished = True


# wrapper for FanFicFare Story to access progress, title and filename of the downloaded story
class MyStory(object):
    def __init__(self, story):
        self.story = story
        self.handler = get_handler()

    def addChapter(self, chap, newchap=False):
        self.story.addChapter(chap, newchap)
        # print('story Chapter %d / %d' % (len(self.story.chapters), self.story.getChapterCount()))
        if self.handler:
            self.handler.chapters(len(self.story.chapters), self.story.getChapterCount())

    def setMetadata(self, key, value, condremoveentities=True):
        self.story.setMetadata(key, value, condremoveentities)
        if self.handler:
            if key == 'numChapters':
                self.handler.chapters(0, self.story.getChapterCount())
            elif key == 'title':
                self.handler.title(value)
                # print("Story Chapter count: %d" % self._maxChapterCount)

    def formatFileName(self, template, allowunsafefilename=True):
        filename = self.story.formatFileName(template, allowunsafefilename)
        if self.handler:
            self.handler.filename(filename)
        return filename

    def __setattr__(self, key, value):
        if key in ['handler', 'story']:
            super(MyStory, self).__setattr__(key, value)
        else:
            setattr(self.story, key, value)

    def __getattr__(self, attr):
        # print('story.%s: (%s)' % (attr, str(getattr(self.story, attr))))
        # print('  -> Chapter %d / %d' % (len(self.story.chapters), self.story.getChapterCount()))
        return getattr(self.story, attr)


# Wrapper for FanFicFare Adapters to react to login- and adult-check -prompts.
class MyAdapter(object):
    def __init__(self, adapter):
        self.adapter = adapter
        self.handler = get_handler()

    def getStoryMetadataOnly(self, get_cover=True):
        for _ in range(0, 2 if self.handler else 1):
            try:
                return self.adapter.getStoryMetadataOnly(get_cover)
            except fanficfare.cli.exceptions.FailedToLogin as f:
                if not self.handler:
                    raise f
                if f.passwdonly:
                    print('Story requires a password.')
                    [self.adapter.password, _] = self.handler.get_login(True)
                else:
                    print('Login Failed, Need Username/Password.')
                    [self.adapter.password, self.adapter.username] = self.handler.get_login(False)
            except fanficfare.cli.exceptions.AdultCheckRequired as e:
                if self.handler and self.handler.is_adult():
                    self.adapter.is_adult = True
                else:
                    raise e

    def __setattr__(self, key, value):
        if key in ['handler', 'adapter']:
            super(MyAdapter, self).__setattr__(key, value)
        else:
            setattr(self.adapter, key, value)

    def __getattr__(self, attr):
        # print('story.%s: (%s)' % (attr, str(getattr(self.story, attr))))
        # print('  -> Chapter %d / %d' % (len(self.story.chapters), self.story.getChapterCount()))
        return getattr(self.adapter, attr)


class MyFetcher(fanficfare.fetcher.Fetcher):
    def __init__(self, get_config_fn, get_config_list_fn):
        super(MyFetcher, self).__init__(get_config_fn, get_config_list_fn)
        self.handler = get_handler()

    def request(self, *args, **kargs):
        print("Fetcher request fetcher.request(%s, %s)" % (str(args), str(kargs)))
        self.handler.web_request(args[0], args[1], **kargs)

    def __setattr__(self, key, value):
        print("Fetcher setattr fetcher.%s = %s" % (str(key), str(value)))
        super(MyFetcher, self).__setattr__(key, value)

    def __getattr__(self, attr):
        print('Fetcher getattr fetcher.%s' % attr)
        # print('story.%s: (%s)' % (attr, str(getattr(self.story, attr))))
        # print('  -> Chapter %d / %d' % (len(self.story.chapters), self.story.getChapterCount()))
        return getattr(super(MyFetcher, self), attr)


# This function wraps the adapter and story
def my_get_adapter(config, url, anyurl=False):
    adapter = originalGetAdapter(config, url, anyurl)
    #if adapter.getSiteDomain() == 'www.fanfiction.net':
    #    print("Site is FanFiction.net, injecting custom fetcher.")
    #    config.fetcher = MyFetcher(config.getConfig, config.getConfigList)
    if get_handler():
        get_handler().start(adapter.url)
    adapter.story = MyStory(adapter.story)
    return MyAdapter(adapter)


def read_personal_ini(path):
    global personal_ini
    with open(path, "r") as f:
        personal_ini = f.read()


def get_handler() -> Handler:
    return handlers.get(threading.get_ident(), None)


def start(my_handler, url, save_cache=False):
    # set Kotlin-Service interface
    handlers[threading.get_ident()] = my_handler if my_handler is not None else Handler(url)
    # modify FanFicFare to inject custom code
    fanficfare.cli.adapters.getAdapter = my_get_adapter
    # print("Now starting Story with url '%s'." % url)
    options = [
        '--progressbar',
        # '--meta-only',
        # '--json-meta',
        # '--no-meta-chapters',
        # '--progress',
        # '--debug',
        '--update-epub',
        '--non-interactive',
        url
    ]
    if save_cache:
        options.insert(0, '--save-cache')

    # run FanFicFare cli version.
    try:
        fanficfare.cli.main(options, passed_personalini=personal_ini, passed_defaultsini=default_ini)
        get_handler().finish(True)
    except:
        if get_handler():
            get_handler().finish(False)


def unnew(filepath):
    options = [
        '--unnew',
        filepath
    ]
    fanficfare.cli.main(options, passed_personalini=personal_ini, passed_defaultsini=default_ini)


if __name__ == "__main__":
    from os.path import expanduser
    read_personal_ini(expanduser('~/.fanficfare/personal.ini'))
    # start(None, 'https://fictionhunt.com/stories/b8kmkn3/the-champion-reading-i', False)
    start(None, 'https://www.fanfiction.net/s/12302907/5/Si-Vis-Pacem-Para-Bellum', False)
