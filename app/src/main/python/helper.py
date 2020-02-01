#!/usr/bin/env python3
import os
from io import StringIO
import sys
from contextlib import redirect_stdout, redirect_stderr

import fanficfare.cli

with open(os.path.dirname(fanficfare.__file__) + '/defaults.ini', 'r') as f:
    default_ini = f.read()
personal_ini = None
originalGetAdapter = fanficfare.cli.adapters.getAdapter
handler = None


class MyStdOut:
    def __init__(self):
        self.stdout = sys.stdout

    def write(self, output):
        if handler:
            handler.output(output)
        else:
            self.stdout.write('<%s>' % output.replace('\n', '\\n'))
            if '\n' in output:
                self.stdout.write('\n')
            self.stdout.flush()

    def flush(self):
        pass


sys.stdout = MyStdOut()


class MyStory(object):
    def __init__(self, story):
        self.story = story

    def addChapter(self, chap, newchap=False):
        self.story.addChapter(chap, newchap)
        # print('story Chapter %d / %d' % (len(self.story.chapters), self.story.getChapterCount()))
        if handler:
            handler.chapters(len(self.story.chapters), self.story.getChapterCount())

    def setMetadata(self, key, value, condremoveentities=True):
        self.story.setMetadata(key, value, condremoveentities)
        if key == 'numChapters':
            if handler:
                handler.chapters(0, self.story.getChapterCount())
        elif key == 'title' and handler:
            handler.title(value)
            # print("Story Chapter count: %d" % self._maxChapterCount)

    def formatFileName(self, template, allowunsafefilename=True):
        filename = self.story.formatFileName(template, allowunsafefilename)
        if handler:
            handler.filename(filename)
        return filename

    def __getattr__(self, attr):
        # print('story.%s: (%s)' % (attr, str(getattr(self.story, attr))))
        # print('  -> Chapter %d / %d' % (len(self.story.chapters), self.story.getChapterCount()))
        return getattr(self.story, attr)


class MyAdapter(object):
    def __init__(self, adapter):
        self.adapter = adapter

    def getStoryMetadataOnly(self, get_cover=True):
        for _ in range(0, 2 if handler else 1):
            try:
                return self.adapter.getStoryMetadataOnly(get_cover)
            except fanficfare.cli.exceptions.FailedToLogin as f:
                if not handler:
                    raise f
                if f.passwdonly:
                    print('Story requires a password.')
                    [self.adapter.password, _] = handler.getLogin(True)
                else:
                    print('Login Failed, Need Username/Password.')
                    [self.adapter.password, self.adapter.username] = handler.getLogin(False)
            except fanficfare.cli.exceptions.AdultCheckRequired as e:
                if handler and handler.getIsAdult():
                    self.adapter.is_adult = True
                else:
                    raise e

    def __getattr__(self, attr):
        # print('story.%s: (%s)' % (attr, str(getattr(self.story, attr))))
        # print('  -> Chapter %d / %d' % (len(self.story.chapters), self.story.getChapterCount()))
        return getattr(self.adapter, attr)


def my_get_adapter(config, url, anyurl=False):
    adapter = originalGetAdapter(config, url, anyurl)
    if handler:
        handler.start(adapter.url)
    adapter.story = MyStory(adapter.story)
    return MyAdapter(adapter)


def my_call(*popenargs, timeout=None, **kwargs):
    pass


def read_personal_ini(path):
    global personal_ini
    with open(path, "r") as f:
        personal_ini = f.read()


def start(my_handler, url, save_cache=False):
    global handler
    handler = my_handler
    fanficfare.cli.adapters.getAdapter = my_get_adapter
    fanficfare.cli.call = my_call
    print("Now starting Story with url '%s'." % url)
    options = [
        # '--meta-only',
        # '--json-meta',
        # '--no-meta-chapters',
        # '--progress',
        # '--debug',
        '-u',
        '--non-interactive',
        url
    ]
    if save_cache:
        options.insert(0, '--save-cache')

    fanficfare.cli.main(options, passed_personalini=personal_ini, passed_defaultsini=default_ini)


if __name__ == "__main__":
    start(None, 'https://www.fanfiction.net/s/2318355/1/Make-A-Wish', True)
