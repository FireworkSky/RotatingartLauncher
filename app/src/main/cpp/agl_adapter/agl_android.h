//
// AGL (AmigaOS GL) 适配层头文件
//

#ifndef AGL_ANDROID_H
#define AGL_ANDROID_H

#ifdef __cplusplus
extern "C" {
#endif

struct TagItem {
    unsigned int ti_Tag;
    unsigned long ti_Data;
};

#define GL4ES_CCT_WINDOW        1
#define GL4ES_CCT_DEPTH         2
#define GL4ES_CCT_STENCIL       3
#define GL4ES_CCT_VSYNC         4
#define TAG_DONE                0

void* aglCreateContext2(unsigned long* errcode, const struct TagItem* tags);
void aglDestroyContext(void* context);
int aglMakeCurrent(void* context);
void aglSwapBuffers(void);

/**
 * 获取 OpenGL 函数指针
 * @param name 函数名（如 "glClear", "glDrawElements" 等）
 * @return 函数指针，如果未找到则返回 NULL
 */
void* aglGetProcAddress(const char* name);

/**
 * 初始化 AGL（可选）
 * @return 成功返回 1，失败返回 0
 */
int aglInit(void);

/**
 * 清理 AGL（可选）
 */
void aglQuit(void);

#ifdef __cplusplus
}
#endif

#endif // AGL_ANDROID_H

