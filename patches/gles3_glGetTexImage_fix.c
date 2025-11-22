/* GLES3 Fix for glGetTexImage - Complete Patch
 * Replace the two glGetTexImage calls in OPENGL_GetTextureData2D
 * with GLES3-compatible glReadPixels implementation
 */

// ============================================
// Fix 1: Lines 4201-4213 (Full texture read)
// ============================================

// BEFORE:
/*
	else if (	x == 0 &&
			y == 0 &&
			w == textureWidth &&
			h == textureHeight	)
	{
		renderer->glGetTexImage(
			GL_TEXTURE_2D,
			level,
			glFormat,
			XNAToGL_TextureDataType[glTexture->format],
			data
		);
	}
*/

// AFTER:
	else if (	x == 0 &&
			y == 0 &&
			w == textureWidth &&
			h == textureHeight	)
	{
		/* GLES3/Android: Use glReadPixels instead of glGetTexImage */
		if (!renderer->supports_NonES3)
		{
			GLuint tempFBO;
			GLint prevFBO;
			renderer->glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFBO);
			renderer->glGenFramebuffers(1, &tempFBO);
			renderer->glBindFramebuffer(GL_FRAMEBUFFER, tempFBO);
			renderer->glFramebufferTexture2D(
				GL_FRAMEBUFFER,
				GL_COLOR_ATTACHMENT0,
				GL_TEXTURE_2D,
				glTexture->handle,
				level
			);
			renderer->glReadPixels(
				0, 0,
				textureWidth, textureHeight,
				glFormat,
				XNAToGL_TextureDataType[glTexture->format],
				data
			);
			renderer->glBindFramebuffer(GL_FRAMEBUFFER, prevFBO);
			renderer->glDeleteFramebuffers(1, &tempFBO);
		}
		else
		{
			/* Desktop OpenGL: Use glGetTexImage */
			renderer->glGetTexImage(
				GL_TEXTURE_2D,
				level,
				glFormat,
				XNAToGL_TextureDataType[glTexture->format],
				data
			);
		}
	}


// ============================================
// Fix 2: Lines 4226-4232 (Partial texture read)
// ============================================

// BEFORE:
/*
		renderer->glGetTexImage(
			GL_TEXTURE_2D,
			level,
			glFormat,
			XNAToGL_TextureDataType[glTexture->format],
			texData
		);
*/

// AFTER:
		/* GLES3/Android: Use glReadPixels instead of glGetTexImage */
		if (!renderer->supports_NonES3)
		{
			GLuint tempFBO;
			GLint prevFBO;
			renderer->glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFBO);
			renderer->glGenFramebuffers(1, &tempFBO);
			renderer->glBindFramebuffer(GL_FRAMEBUFFER, tempFBO);
			renderer->glFramebufferTexture2D(
				GL_FRAMEBUFFER,
				GL_COLOR_ATTACHMENT0,
				GL_TEXTURE_2D,
				glTexture->handle,
				level
			);
			renderer->glReadPixels(
				0, 0,
				textureWidth, textureHeight,
				glFormat,
				XNAToGL_TextureDataType[glTexture->format],
				texData
			);
			renderer->glBindFramebuffer(GL_FRAMEBUFFER, prevFBO);
			renderer->glDeleteFramebuffers(1, &tempFBO);
		}
		else
		{
			/* Desktop OpenGL: Use glGetTexImage */
			renderer->glGetTexImage(
				GL_TEXTURE_2D,
				level,
				glFormat,
				XNAToGL_TextureDataType[glTexture->format],
				texData
			);
		}


// ============================================
// Fix 3: Line 4159 (Remove assertion)
// ============================================

// BEFORE:
/*
	SDL_assert(renderer->supports_NonES3);
*/

// AFTER:
	/* GLES3 support: Comment out NonES3 assertion */
	/* SDL_assert(renderer->supports_NonES3); */


// ============================================
// Summary
// ============================================

/*
 * This patch adds full GLES3 support for texture reading in FNA3D.
 *
 * Changes:
 * 1. Replace glGetTexImage (desktop OpenGL only) with glReadPixels (GLES3 compatible)
 * 2. Use temporary FBO to read texture data
 * 3. Restore previous FBO binding after reading
 *
 * Why this works:
 * - glGetTexImage doesn't exist in GLES3
 * - glReadPixels is the standard way to read pixels in GLES3
 * - FBO allows us to read from textures using glReadPixels
 *
 * Performance:
 * - Creates temporary FBO (slight overhead)
 * - Reading performance is equivalent to glGetTexImage
 * - Only used when game needs to read texture data (rare)
 */
