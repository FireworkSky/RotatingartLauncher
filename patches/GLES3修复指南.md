# Android GLES3 glGetTexImage 修复指南

## 📋 需要修改的文件
`D:\Rotating-art-Launcher\app\src\main\cpp\FNA3D\src\FNA3D_Driver_OpenGL.c`

---

## 🔧 修改 1: 注释掉断言 (第 4159 行)

### 查找
```c
SDL_assert(renderer->supports_NonES3);
```

### 替换为
```c
/* GLES3 support: Comment out NonES3 assertion */
/* SDL_assert(renderer->supports_NonES3); */
```

---

## 🔧 修改 2: 完整纹理读取 (第 4206-4213 行)

### 查找
```c
		/* Just throw the whole texture into the user array. */
		renderer->glGetTexImage(
			GL_TEXTURE_2D,
			level,
			glFormat,
			XNAToGL_TextureDataType[glTexture->format],
			data
		);
```

### 替换为
```c
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
```

---

## 🔧 修改 3: 部分纹理读取 (第 4226-4232 行)

### 查找
```c
		renderer->glGetTexImage(
			GL_TEXTURE_2D,
			level,
			glFormat,
			XNAToGL_TextureDataType[glTexture->format],
			texData
		);
```

### 替换为
```c
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
```

---

## ✅ 修改后编译

```bash
cd D:/Rotating-art-Launcher
./gradlew clean assembleDebug
```

---

## 🧪 验证

修改成功后，应该：
- ✅ 不再出现 `Assertion Failed` 错误
- ✅ 游戏正常启动
- ✅ UI 和纹理正常显示

---

## 📝 技术说明

### 为什么需要这个修复？

1. **glGetTexImage 不存在于 GLES3**
   - 这是桌面 OpenGL 的函数
   - Android 使用 OpenGL ES 3.0，没有这个函数

2. **GLES3 的标准做法**
   - 创建临时 FBO (Framebuffer Object)
   - 将纹理绑定到 FBO
   - 使用 glReadPixels 读取像素数据
   - 清理临时 FBO

3. **性能影响**
   - 创建/销毁 FBO 有轻微开销
   - 读取性能与 glGetTexImage 相当
   - 游戏很少需要读取纹理数据，影响很小

---

## 🔍 相关函数

同样的修复可能也需要应用到：
- `OPENGL_GetTextureData3D` (如果存在)
- `OPENGL_GetTextureDataCube` (如果存在)

搜索文件中所有的 `glGetTexImage` 调用，用相同的模式替换。

---

**作者**: Claude Code
**日期**: 2025-11-20
**优先级**: 🔴 高（修复崩溃）
