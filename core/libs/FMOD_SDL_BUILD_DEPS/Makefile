.PHONY: all stub

FMOD_VERSION = 10
SDL2_PREFIX = ""

all: stub
	cp ../FMOD_SDL/FMOD_SDL.c .
# Copied from FMOD_SDL's makefile, altered slightly in order to be nicer with CI
	$(CC) -O3 -Wall -pedantic -fpic -fPIC -shared -o libfmod_SDL.so FMOD_SDL.c `$(SDL2_PREFIX)/bin/sdl2-config --libs --cflags` libfmod.so.$(FMOD_VERSION)

stub:
	$(CC) -fPIC -shared -o libfmod.so.10 fmod_stub.c
