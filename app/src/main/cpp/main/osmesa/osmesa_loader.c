//
// OSMesa library loader
//
#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>
#include "osmesa_loader.h"
#include "GL/osmesa.h"

// Function pointers
GLboolean (*OSMesaMakeCurrent_p) (OSMesaContext ctx, void *buffer, GLenum type,
                                  GLsizei width, GLsizei height);
OSMesaContext (*OSMesaGetCurrentContext_p) (void);
OSMesaContext (*OSMesaCreateContext_p) (GLenum format, OSMesaContext sharelist);
OSMesaContext (*OSMesaCreateContextAttribs_p) (const int *attribList, OSMesaContext sharelist);
void (*OSMesaDestroyContext_p) (OSMesaContext ctx);
void (*OSMesaPixelStore_p) (GLint pname, GLint value);
GLubyte* (*glGetString_p) (GLenum name);
void (*glFinish_p) (void);
void (*glClearColor_p) (GLclampf red, GLclampf green, GLclampf blue, GLclampf alpha);
void (*glClear_p) (GLbitfield mask);
void (*glReadPixels_p) (GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, void *data);
void* (*OSMesaGetProcAddress_p)(const char* funcName);

bool dlsym_OSMesa(void) {
    // Load libOSMesa.so
    void* dl_handle = dlopen("libOSMesa.so", RTLD_LOCAL | RTLD_LAZY);
    if (dl_handle == NULL) {
        return false;
    }
    
    // Get OSMesaGetProcAddress first, then use it to get other functions
    OSMesaGetProcAddress_p = (void* (*)(const char*)) dlsym(dl_handle, "OSMesaGetProcAddress");
    if (OSMesaGetProcAddress_p == NULL) {
        printf("Failed to get OSMesaGetProcAddress: %s\n", dlerror());
        return false;
    }
    
    // Resolve all OSMesa functions using OSMesaGetProcAddress
    OSMesaMakeCurrent_p = (GLboolean (*)(OSMesaContext, void*, GLenum, GLsizei, GLsizei))
        OSMesaGetProcAddress_p("OSMesaMakeCurrent");
    OSMesaGetCurrentContext_p = (OSMesaContext (*)(void))
        OSMesaGetProcAddress_p("OSMesaGetCurrentContext");
    OSMesaCreateContext_p = (OSMesaContext (*)(GLenum, OSMesaContext))
        OSMesaGetProcAddress_p("OSMesaCreateContext");
    OSMesaCreateContextAttribs_p = (OSMesaContext (*)(const int*, OSMesaContext))
        OSMesaGetProcAddress_p("OSMesaCreateContextAttribs");
    OSMesaDestroyContext_p = (void (*)(OSMesaContext))
        OSMesaGetProcAddress_p("OSMesaDestroyContext");
    OSMesaPixelStore_p = (void (*)(GLint, GLint))
        OSMesaGetProcAddress_p("OSMesaPixelStore");
    
    // Resolve OpenGL functions
    glGetString_p = (GLubyte* (*)(GLenum))
        OSMesaGetProcAddress_p("glGetString");
    glFinish_p = (void (*)(void))
        OSMesaGetProcAddress_p("glFinish");
    glClearColor_p = (void (*)(GLclampf, GLclampf, GLclampf, GLclampf))
        OSMesaGetProcAddress_p("glClearColor");
    glClear_p = (void (*)(GLbitfield))
        OSMesaGetProcAddress_p("glClear");
    glReadPixels_p = (void (*)(GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, void*))
        OSMesaGetProcAddress_p("glReadPixels");
    
    return true;
}

