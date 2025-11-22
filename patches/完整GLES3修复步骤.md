# Android GLES3 完整修复步骤

## 第一步：添加缺失的 GL 常量定义

### 文件：`FNA3D_Driver_OpenGL.h`

在第 236 行后添加：

```c
/* Render targets */
#define GL_FRAMEBUFFER  				0x8D40
#define GL_FRAMEBUFFER_BINDING				0x8CA6  // ← 添加这一行
#define GL_READ_FRAMEBUFFER				0x8CA8
```

---

## 第二步：修改 `FNA3D_Driver_OpenGL.c`

### 修改 1: 注释掉断言 (约第 4159 行)

查找：
```c
SDL_assert(renderer->supports_NonES3);
```

替换为：
```c
/* GLES3: Disable NonES3 assertion */
/* SDL_assert(renderer->supports_NonES3); */
```

---

### 修改 2: 第一个 glGetTexImage (约第 4207 行)

查找：
```c
renderer->glGetTexImage(
    GL_TEXTURE_2D,
    level,
    glFormat,
    XNAToGL_TextureDataType[glTexture->format],
    data
);
```

替换为：
```c
/* GLES3: Use glReadPixels instead of glGetTexImage */
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

### 修改 3: 第二个 glGetTexImage (约第 4226 行)

查找：
```c
renderer->glGetTexImage(
    GL_TEXTURE_2D,
    level,
    glFormat,
    XNAToGL_TextureDataType[glTexture->format],
    texData
);
```

替换为：
```c
/* GLES3: Use glReadPixels instead of glGetTexImage */
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

## 检查其他 glGetTexImage 调用

搜索文件中是否还有其他 `glGetTexImage` 调用：

```bash
cd D:/Rotating-art-Launcher/app/src/main/cpp/FNA3D/src
grep -n "glGetTexImage" FNA3D_Driver_OpenGL.c
```

如果有 `GetTextureData3D` 或 `GetTextureDataCube`，用相同方法修复。

---

## 编译测试

```bash
cd D:/Rotating-art-Launcher
./gradlew clean assembleDebug
```

---

## 快速验证 GL 常量值

这些是标准 OpenGL ES 3.0 常量：
- `GL_FRAMEBUFFER` = `0x8D40`
- `GL_FRAMEBUFFER_BINDING` = `0x8CA6`
- `GL_COLOR_ATTACHMENT0` = `0x8CE0`

可以在官方文档验证：
https://registry.khronos.org/OpenGL-Refpages/es3.0/

---

## 完成后应该

- ✅ 没有编译错误
- ✅ 游戏启动不崩溃
- ✅ 纹理读取功能正常工作
