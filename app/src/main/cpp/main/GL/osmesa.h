/*
 * Mesa 3-D graphics library
 * 
 * Copyright (C) 1999-2005  Brian Paul   All Rights Reserved.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */


/*
 * Mesa Off-Screen rendering interface.
 *
 * This is an operating system and window system independent interface to
 * Mesa which allows one to render images into a client-supplied buffer in
 * main memory.  Such images may manipulated or saved in whatever way the
 * client wants.
 */

#ifndef OSMESA_H
#define OSMESA_H

#ifdef __cplusplus
extern "C" {
#endif

#include <GL/gl.h>

/* Ensure GLAPI and GLAPIENTRY are defined */
#ifndef GLAPI
#define GLAPI extern
#endif

#ifndef GLAPIENTRY
#define GLAPIENTRY
#endif

#define OSMESA_MAJOR_VERSION 11
#define OSMESA_MINOR_VERSION 2
#define OSMESA_PATCH_VERSION 0

/*
 * Values for the format parameter of OSMesaCreateContext()
 */
#define OSMESA_COLOR_INDEX	GL_COLOR_INDEX
#define OSMESA_RGBA		GL_RGBA
#define OSMESA_BGRA		0x1
#define OSMESA_ARGB		0x2
#define OSMESA_RGB		GL_RGB
#define OSMESA_BGR		0x4
#define OSMESA_RGB_565		0x5

/*
 * OSMesaPixelStore() parameters:
 */
#define OSMESA_ROW_LENGTH	0x10
#define OSMESA_Y_UP		0x11

/*
 * Accepted by OSMesaGetIntegerv:
 */
#define OSMESA_WIDTH		0x20
#define OSMESA_HEIGHT		0x21
#define OSMESA_FORMAT		0x22
#define OSMESA_TYPE		0x23
#define OSMESA_MAX_WIDTH	0x24
#define OSMESA_MAX_HEIGHT	0x25

/*
 * Accepted in OSMesaCreateContextAttrib's attribute list.
 */
#define OSMESA_DEPTH_BITS            0x30
#define OSMESA_STENCIL_BITS          0x31
#define OSMESA_ACCUM_BITS            0x32
#define OSMESA_PROFILE               0x33
#define OSMESA_CORE_PROFILE          0x34
#define OSMESA_COMPAT_PROFILE        0x35
#define OSMESA_CONTEXT_MAJOR_VERSION 0x36
#define OSMESA_CONTEXT_MINOR_VERSION 0x37

typedef struct osmesa_context *OSMesaContext;

/*
 * Create an Off-Screen Mesa rendering context.
 */
GLAPI OSMesaContext GLAPIENTRY
OSMesaCreateContext( GLenum format, OSMesaContext sharelist );

/*
 * Create an Off-Screen Mesa rendering context with extended parameters.
 */
GLAPI OSMesaContext GLAPIENTRY
OSMesaCreateContextExt( GLenum format, GLint depthBits, GLint stencilBits,
                        GLint accumBits, OSMesaContext sharelist);

/*
 * Create an Off-Screen Mesa rendering context with attribute list.
 */
GLAPI OSMesaContext GLAPIENTRY
OSMesaCreateContextAttribs( const int *attribList, OSMesaContext sharelist );

/*
 * Destroy an Off-Screen Mesa rendering context.
 */
GLAPI void GLAPIENTRY
OSMesaDestroyContext( OSMesaContext ctx );

/*
 * Bind an OSMesaContext to an image buffer.
 */
GLAPI GLboolean GLAPIENTRY
OSMesaMakeCurrent( OSMesaContext ctx, void *buffer, GLenum type,
                   GLsizei width, GLsizei height );

/*
 * Return the current Off-Screen Mesa rendering context handle.
 */
GLAPI OSMesaContext GLAPIENTRY
OSMesaGetCurrentContext( void );

/*
 * Set pixel store/packing parameters for the current context.
 */
GLAPI void GLAPIENTRY
OSMesaPixelStore( GLint pname, GLint value );

/*
 * Return an integer value like glGetIntegerv.
 */
GLAPI void GLAPIENTRY
OSMesaGetIntegerv( GLint pname, GLint *value );

/*
 * Return the depth buffer associated with an OSMesa context.
 */
GLAPI GLboolean GLAPIENTRY
OSMesaGetDepthBuffer( OSMesaContext c, GLint *width, GLint *height,
                      GLint *bytesPerValue, void **buffer );

/*
 * Return the color buffer associated with an OSMesa context.
 */
GLAPI GLboolean GLAPIENTRY
OSMesaGetColorBuffer( OSMesaContext c, GLint *width, GLint *height,
                      GLint *format, void **buffer );

/**
 * This typedef is new in Mesa 6.3.
 */
typedef void (*OSMESAproc)();

/*
 * Return pointer to the named function.
 */
GLAPI OSMESAproc GLAPIENTRY
OSMesaGetProcAddress( const char *funcName );

/**
 * Enable/disable color clamping, off by default.
 */
GLAPI void GLAPIENTRY
OSMesaColorClamp(GLboolean enable);

/**
 * Enable/disable Gallium post-process filters.
 */
GLAPI void GLAPIENTRY
OSMesaPostprocess(OSMesaContext osmesa, const char *filter,
                  unsigned enable_value);

#ifdef __cplusplus
}
#endif

#endif

