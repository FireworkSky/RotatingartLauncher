/*
    SDL_android_main.c, placed in the public domain by Sam Lantinga  3/13/14

    As of SDL 2.0.6 this file is no longer necessary.
*/

/* vi: set ts=4 sw=4 expandtab: */


#define SDL_MAIN_HANDLED

#include "../../SDL_internal.h"



typedef void (*Main)();
Main CurrentMain;

__attribute__ ((visibility("default"))) void SetMain(Main main) {
    CurrentMain = main;
}

__attribute__ ((visibility("default"))) int SDL_main(int argc, char* argv[]) {
    CurrentMain();
    return 0;
}
