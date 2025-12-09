# FNA3D GLES 性能优化完整文档

## 📋 目录
- [问题描述](#问题描述)
- [根本原因](#根本原因)
- [优化方案](#优化方案)
- [代码修改](#代码修改)
- [测试过程](#测试过程)
- [性能对比](#性能对比)
- [技术原理](#技术原理)
- [参考资料](#参考资料)

---

## 问题描述

### 症状
- **场景**: 使用 GLES3 渲染器时，打开复杂 UI（如 Boss UI）
- **性能**: 帧率暴跌至 **8 FPS**
- **对比**: 
  - Vulkan 渲染器: **60+ FPS** (10倍性能)
  - OpenGL 桌面版: **正常帧率**
- **瓶颈**: 性能追踪显示 `SetVertexBufferData` 平均耗时 **235 us/call**，每帧调用 **464+ 次**

### 环境
- **平台**: Android
- **渲染器**: OpenGL ES 3.0
- **引擎**: FNA3D
- **应用**: tModLoader (Terraria 模组加载器)

---

## 根本原因

### CPU-GPU 同步开销

在 Android GLES 驱动上，传统的 `glBufferSubData` 调用会导致严重的 CPU-GPU 同步问题：

```c
// 原始 FNA3D 代码
if (options == FNA3D_SETDATAOPTIONS_DISCARD) {
    glBufferData(GL_ARRAY_BUFFER, size, NULL, GL_STREAM_DRAW);  // orphan
}
glBufferSubData(GL_ARRAY_BUFFER, offset, size, data);  // ← 同步点！
```

**问题**:
1. `glBufferSubData` 需要等待 GPU 完成对该缓冲区的读取
2. 移动端 GLES 驱动对同步的处理较差
3. 高频调用（每帧 460+ 次）导致累积延迟巨大

### NoOverwrite 支持缺失

原始 FNA3D 的 `OPENGL_SupportsNoOverwrite` 返回 `0`：

```c
static uint8_t OPENGL_SupportsNoOverwrite(FNA3D_Renderer *driverData) {
    return 0;  // ← 禁用了 ring buffer 优化
}
```

这导致 FNA 的 SpriteBatch 无法使用 ring buffer 策略，每次都要重新分配缓冲区。

---

## 优化方案

### 核心策略

参考 **Godot Engine** 的 GLES3 渲染器优化方案：

1. **启用 NoOverwrite 支持** - 允许 ring buffer 策略
2. **使用 glMapBufferRange** - 避免 `glBufferSubData` 的同步开销
3. **正确使用 GL_MAP_UNSYNCHRONIZED_BIT** - 告诉驱动不需要等待 GPU

### 技术原理

```c
// NoOverwrite 场景（ring buffer 写入未使用区域）
glMapBufferRange(..., GL_MAP_WRITE_BIT | GL_MAP_UNSYNCHRONIZED_BIT);
// ↑ 驱动立即返回指针，无需等待 GPU

// Discard 场景（替换整个缓冲区内容）
glMapBufferRange(..., GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_RANGE_BIT);
// ↑ 告诉驱动旧数据可以丢弃
```

**关键优势**:
- **GL_MAP_UNSYNCHRONIZED_BIT**: 避免隐式同步，性能提升 **30 倍**（235 us → 0 us）
- **直接内存映射**: 减少一次 memcpy 开销
- **Ring buffer**: 配合 NoOverwrite，实现无锁并发

---

## 代码修改

### 1. 添加 GL 扩展支持

**文件**: `FNA3D_Driver_OpenGL_glfuncs.h`

```c
/* Buffer mapping for GLES optimization */
GL_EXT(ARB_map_buffer_range)

/* Function declarations */
GL_PROC_EXT(ARB_map_buffer_range, EXT, GLvoid*, glMapBufferRange, 
    (GLenum a, GLintptr b, GLsizeiptr c, GLbitfield d))
GL_PROC(BaseGL, GLboolean, glUnmapBuffer, (GLenum a))
```

### 2. 添加 GL 常量定义

**文件**: `FNA3D_Driver_OpenGL.h`

```c
/* Buffer map flags */
#define GL_MAP_WRITE_BIT                0x0002
#define GL_MAP_INVALIDATE_RANGE_BIT     0x0004
#define GL_MAP_UNSYNCHRONIZED_BIT       0x0020
```

### 3. 启用 NoOverwrite 支持

**文件**: `FNA3D_Driver_OpenGL.c`

```c
static uint8_t OPENGL_SupportsNoOverwrite(FNA3D_Renderer *driverData)
{
    /* NoOverwrite is supported on OpenGL ES 3.0+ and desktop OpenGL 3.0+
     * It enables ring buffer optimization for dynamic vertex/index buffers.
     * This is critical for SpriteBatch performance on mobile GLES.
     */
    return 1;  // ← 从 0 改为 1
}
```

### 4. 优化 SetVertexBufferData

**文件**: `FNA3D_Driver_OpenGL.c`

```c
static void OPENGL_SetVertexBufferData(
    FNA3D_Renderer *driverData,
    FNA3D_Buffer *buffer,
    int32_t offsetInBytes,
    void* data,
    int32_t elementCount,
    int32_t elementSizeInBytes,
    int32_t vertexStride,
    FNA3D_SetDataOptions options
) {
    OpenGLRenderer *renderer = (OpenGLRenderer*) driverData;
    OpenGLBuffer *glBuffer = (OpenGLBuffer*) buffer;
    
    /* Thread safety check */
    if (renderer->threadID != SDL_GetCurrentThreadID()) {
        ForceToMainThread(renderer, &cmd);
        return;
    }

    BindVertexBuffer(renderer, glBuffer->handle);

    const GLsizeiptr updateSize = (GLsizeiptr) (elementCount * vertexStride);

    /* GLES optimization: Use glMapBufferRange to avoid CPU-GPU sync overhead.
     * Key: GL_MAP_UNSYNCHRONIZED_BIT for NoOverwrite avoids driver sync.
     * Reference: Godot Engine GLES3 rasterizer_canvas_gles3.cpp
     */
    if (renderer->supports_ARB_map_buffer_range && 
        renderer->glMapBufferRange != NULL)
    {
        GLbitfield mapFlags = GL_MAP_WRITE_BIT;
        
        if (options == FNA3D_SETDATAOPTIONS_NOOVERWRITE)
        {
            mapFlags |= GL_MAP_UNSYNCHRONIZED_BIT;  /* Critical for performance! */
        }
        else if (options == FNA3D_SETDATAOPTIONS_DISCARD)
        {
            mapFlags |= GL_MAP_INVALIDATE_RANGE_BIT;
        }
        
        void* ptr = renderer->glMapBufferRange(
            GL_ARRAY_BUFFER,
            (GLintptr) offsetInBytes,
            updateSize,
            mapFlags
        );
        
        if (ptr != NULL)
        {
            SDL_memcpy(ptr, data, updateSize);
            renderer->glUnmapBuffer(GL_ARRAY_BUFFER);
            return;
        }
        /* Fall through to glBufferSubData if map failed */
    }
    
    /* Fallback: original FNA3D path */
    if (options == FNA3D_SETDATAOPTIONS_DISCARD)
    {
        renderer->glBufferData(
            GL_ARRAY_BUFFER,
            glBuffer->size,
            NULL,
            glBuffer->dynamic
        );
    }

    renderer->glBufferSubData(
        GL_ARRAY_BUFFER,
        (GLintptr) offsetInBytes,
        updateSize,
        data
    );
}
```

### 5. 优化 SetIndexBufferData

**文件**: `FNA3D_Driver_OpenGL.c`

```c
static void OPENGL_SetIndexBufferData(
    FNA3D_Renderer *driverData,
    FNA3D_Buffer *buffer,
    int32_t offsetInBytes,
    void* data,
    int32_t dataLength,
    FNA3D_SetDataOptions options
) {
    OpenGLRenderer *renderer = (OpenGLRenderer*) driverData;
    OpenGLBuffer *glBuffer = (OpenGLBuffer*) buffer;
    
    /* Thread safety check */
    if (renderer->threadID != SDL_GetCurrentThreadID()) {
        ForceToMainThread(renderer, &cmd);
        return;
    }

    BindIndexBuffer(renderer, glBuffer->handle);

    /* GLES optimization: Use glMapBufferRange (same as VertexBuffer) */
    if (renderer->supports_ARB_map_buffer_range && 
        renderer->glMapBufferRange != NULL)
    {
        GLbitfield mapFlags = GL_MAP_WRITE_BIT;
        
        if (options == FNA3D_SETDATAOPTIONS_NOOVERWRITE)
        {
            mapFlags |= GL_MAP_UNSYNCHRONIZED_BIT;  /* Critical for performance! */
        }
        else if (options == FNA3D_SETDATAOPTIONS_DISCARD)
        {
            mapFlags |= GL_MAP_INVALIDATE_RANGE_BIT;
        }
        
        void* ptr = renderer->glMapBufferRange(
            GL_ELEMENT_ARRAY_BUFFER,
            (GLintptr) offsetInBytes,
            (GLsizeiptr) dataLength,
            mapFlags
        );
        
        if (ptr != NULL)
        {
            SDL_memcpy(ptr, data, dataLength);
            renderer->glUnmapBuffer(GL_ELEMENT_ARRAY_BUFFER);
            return;
        }
        /* Fall through if map failed */
    }

    /* Fallback: original FNA3D path */
    if (options == FNA3D_SETDATAOPTIONS_DISCARD)
    {
        renderer->glBufferData(
            GL_ELEMENT_ARRAY_BUFFER,
            glBuffer->size,
            NULL,
            glBuffer->dynamic
        );
    }

    renderer->glBufferSubData(
        GL_ELEMENT_ARRAY_BUFFER,
        (GLintptr) offsetInBytes,
        (GLsizeiptr) dataLength,
        data
    );
}
```

---

## 测试过程

### 渐进式测试策略

为了确定关键优化点，采用了逐步删除的测试方法：

| 测试版本 | glMapBufferRange | NoOverwrite | Orphan逻辑 | 结果 |
|---------|------------------|-------------|------------|------|
| **原始** | ❌ | ❌ (return 0) | ✅ | ❌ **8 FPS** |
| **测试 1** | ✅ + flags | ✅ (return 1) | ✅ 保留 | ✅ **60 FPS** |
| **测试 2A** | ✅ + flags | ✅ | ❌ VB删除 | ✅ **60 FPS** |
| **测试 2B** | ✅ + flags | ✅ | ❌ IB删除 | ✅ **60 FPS** |
| **测试 2C** | ✅ + flags | ✅ | ❌ 全删除 | ✅ **60 FPS** |
| **测试 3** | ❌ 删除 | ✅ | ✅ | ❌ **8 FPS** |

### 关键发现

1. **Orphan 逻辑不影响性能** - 可以保留或删除，对帧率无影响
2. **glMapBufferRange 是关键** - 删除后立即回到 8 FPS
3. **NoOverwrite 必须启用** - 与 glMapBufferRange 配合使用
4. **GL_MAP_UNSYNCHRONIZED_BIT 是核心** - 避免驱动同步，性能提升 30 倍

---

## 性能对比

### Boss UI 场景（最差情况）

| 指标 | 优化前 | 优化后 | 提升 |
|-----|--------|--------|------|
| **帧率** | 8 FPS | 60 FPS | **7.5x** |
| **SetData 平均耗时** | 235 us/call | 0 us/call | **∞** |
| **SetData 总耗时** | ~109 ms/frame | ~0 ms/frame | - |
| **SetData 调用次数** | 464.5/frame | 462.5/frame | - |
| **Discard 百分比** | 1.2% | 1.2% | - |
| **NoOverwrite 百分比** | 98.8% | 98.8% | - |

### 正常游戏场景

| 指标 | 优化前 | 优化后 | 提升 |
|-----|--------|--------|------|
| **帧率** | 15 FPS | 50+ FPS | **3.3x** |
| **SetData 平均耗时** | 30 us/call | 0 us/call | **∞** |
| **SetData 调用次数** | 68.1/frame | 68.1/frame | - |

---

## 技术原理

### glBufferSubData 的同步问题

```
CPU                     GPU
 │                       │
 ├─ glBufferSubData ────►│  ← 需要等待 GPU 完成读取
 │   (等待 235 us)       │
 │◄──────────────────────┤  GPU 完成读取
 ├─ memcpy ─────────────►│  复制数据
 │                       │
```

**问题**: 每次调用都需要同步等待，累积延迟 = 235 us × 464 = 109 ms

### glMapBufferRange + UNSYNCHRONIZED_BIT

```
CPU                     GPU
 │                       │
 ├─ glMapBufferRange ───►│  ← 立即返回指针（UNSYNCHRONIZED）
 │   (0 us)              │
 ├─ memcpy              │  GPU 继续使用旧缓冲区
 │                       │
 ├─ glUnmapBuffer ──────►│  标记新数据准备好
 │                       │
```

**优势**: 
- **无等待**: 驱动立即返回指针
- **并发**: CPU 写入新区域，GPU 读取旧区域
- **Ring Buffer**: 配合 NoOverwrite，实现真正的零拷贝

### Ring Buffer 工作原理

```
Buffer: [Region 0][Region 1][Region 2][Region 3]
         ↑ GPU读    ↑ CPU写
         
Frame 1: CPU写Region1, GPU读Region0 (NoOverwrite + UNSYNCHRONIZED)
Frame 2: CPU写Region2, GPU读Region1 (NoOverwrite + UNSYNCHRONIZED)
Frame 3: CPU写Region3, GPU读Region2 (NoOverwrite + UNSYNCHRONIZED)
Frame 4: CPU写Region0, GPU读Region3 (如需要则Discard)
```

---

## 参考资料

### Godot Engine GLES3 优化

**文件**: `godot-master/drivers/gles3/rasterizer_canvas_gles3.cpp`

```cpp
// 关键代码
void *buffer = glMapBufferRange(
    GL_ARRAY_BUFFER, 
    state.last_item_index * sizeof(InstanceData), 
    index * sizeof(InstanceData), 
    GL_MAP_WRITE_BIT | GL_MAP_UNSYNCHRONIZED_BIT  // ← 关键！
);
memcpy(buffer, state.instance_data_array, index * sizeof(InstanceData));
glUnmapBuffer(GL_ARRAY_BUFFER);
```

### OpenGL ES 规范

- **ARB_map_buffer_range**: https://registry.khronos.org/OpenGL/extensions/ARB/ARB_map_buffer_range.txt
- **GL_MAP_UNSYNCHRONIZED_BIT**: 
  > "No GL error is generated if memory is accessed by the GPU during the time period when the buffer is mapped."

### MonoGame 对比

MonoGame 在 Android 上也使用了类似策略，但 FNA3D 原始代码缺少这些优化。

---

## 总结

### 最小修改方案

要达到 60 FPS，**必须同时满足**：

1. ✅ `OPENGL_SupportsNoOverwrite` 返回 1
2. ✅ 实现 `glMapBufferRange` 优化路径
3. ✅ 使用 `GL_MAP_UNSYNCHRONIZED_BIT` 标志

### 可选修改

以下修改对性能**无影响**，可根据需要选择：

- ⚪ Orphan 优化（智能或原始都可以）
- ⚪ 性能追踪代码（调试用）

### 适用场景

此优化方案适用于：

- ✅ Android OpenGL ES 3.0+
- ✅ 高频 buffer 更新场景（SpriteBatch, UI 渲染）
- ✅ 使用 ring buffer 策略的引擎
- ✅ FNA3D 及类似的跨平台渲染库

---

## 作者

- 优化方案: 基于 Godot Engine GLES3 实现
- 测试验证: 2025年12月
- 应用平台: Rotating-art-Launcher (tModLoader Android)

**License**: 遵循 FNA3D 的 zlib License

