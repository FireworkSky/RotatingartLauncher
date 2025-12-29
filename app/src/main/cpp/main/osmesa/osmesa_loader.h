//
// OSMesa library loader
//
#ifndef OSMESA_LOADER_H
#define OSMESA_LOADER_H

#include "GL/osmesa.h"
#include <stdbool.h>

// Function pointers for OSMesa functions
extern GLboolean (*OSMesaMakeCurrent_p) (OSMesaContext ctx, void *buffer, GLenum type,
                                         GLsizei width, GLsizei height);
extern OSMesaContext (*OSMesaGetCurrentContext_p) (void);
extern OSMesaContext (*OSMesaCreateContext_p) (GLenum format, OSMesaContext sharelist);
extern OSMesaContext (*OSMesaCreateContextAttribs_p) (const int *attribList, OSMesaContext sharelist);
extern void (*OSMesaDestroyContext_p) (OSMesaContext ctx);
extern void (*OSMesaPixelStore_p) (GLint pname, GLint value);
extern GLubyte* (*glGetString_p) (GLenum name);
extern void (*glFinish_p) (void);
extern void (*glClearColor_p) (GLclampf red, GLclampf green, GLclampf blue, GLclampf alpha);
extern void (*glClear_p) (GLbitfield mask);
extern void (*glReadPixels_p) (GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, void *data);
extern void* (*OSMesaGetProcAddress_p)(const char* funcName);

/**
 * @brief Load OSMesa library and resolve function symbols
 * @return true on success, false on failure
 */
bool dlsym_OSMesa(void);

#endif // OSMESA_LOADER_H

