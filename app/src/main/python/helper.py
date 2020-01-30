#!/usr/bin/env python3
import os

import fanficfare.cli

with open(os.path.dirname(fanficfare.__file__) + '/defaults.ini', 'r') as f:
    default_ini = f.read()
originalGetAdapter = fanficfare.cli.adapters.getAdapter
handler = None


class MyStory(object):
    def __init__(self, story):
        self.story = story

    def addChapter(self, chap, newchap=False):
        self.story.addChapter(chap, newchap)
        print('story Chapter %d / %d' % (len(self.story.chapters), self.story.getChapterCount()))
        if handler:
            handler.chapters(len(self.story.chapters), self.story.getChapterCount())

    def setMetadata(self, key, value, condremoveentities=True):
        self.story.setMetadata(key, value, condremoveentities)
        if key == "numChapters":
            if handler:
                handler.chapters(0, self.story.getChapterCount())
        elif key == "title" and handler:
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


def my_get_adapter(config, url, anyurl=False):
    adapter = originalGetAdapter(config, url, anyurl)
    if handler:
        handler.start(adapter.url)
    adapter.story = MyStory(adapter.story)
    return adapter


def start(my_handler, url, save_cache=False):
    global handler
    handler = my_handler
    fanficfare.cli.adapters.getAdapter = my_get_adapter
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
    fanficfare.cli.main(options, passed_defaultsini=default_ini)


if __name__ == "__main__":
    start('https://www.fanfiction.net/s/12979639/1/Jane-Arc-s-Yuri-Harem')