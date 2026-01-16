/*
 * glibc-bridge - IPC (进程间通信) 函数包装
 * 
 * 包含 POSIX 消息队列 (mqueue), 异步 IO (aio), System V IPC (shm/sem/msg)
 * 
 * 注意: Android 的 seccomp 阻止了 System V IPC 系统调用，
 * 所以这里提供基于 mmap 和 mutex 的用户空间实现
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <pthread.h>
#include <signal.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/types.h>

#include "../include/wrappers.h"
#include "../include/private.h"
#include "../elf/log.h"
#include "wrapper_path.h"

/* ============================================================================
 * 类型定义 (Android NDK 可能缺失)
 * ============================================================================ */

/* mqueue 类型 */
#ifndef _MQUEUE_H
typedef int mqd_t;

struct mq_attr {
    long mq_flags;       /* 消息队列标志 */
    long mq_maxmsg;      /* 最大消息数 */
    long mq_msgsize;     /* 最大消息大小 */
    long mq_curmsgs;     /* 当前消息数 */
};
#endif

/* aio 类型 */
#ifndef _AIO_H
struct aiocb {
    int             aio_fildes;     /* 文件描述符 */
    off_t           aio_offset;     /* 文件偏移 */
    volatile void*  aio_buf;        /* 缓冲区 */
    size_t          aio_nbytes;     /* 字节数 */
    int             aio_reqprio;    /* 请求优先级 */
    struct sigevent aio_sigevent;   /* 通知方式 */
    int             aio_lio_opcode; /* lio_listio 操作码 */
    /* 内部字段 */
    int             __error_code;
    ssize_t         __return_value;
};
#endif

/* IPC 常量 */
#ifndef IPC_PRIVATE
#define IPC_PRIVATE 0
#endif
#ifndef IPC_CREAT
#define IPC_CREAT   01000
#define IPC_EXCL    02000
#define IPC_NOWAIT  04000
#define IPC_RMID    0
#define IPC_SET     1
#define IPC_STAT    2
#endif

/* AIO 常量 */
#ifndef AIO_ALLDONE
#define AIO_ALLDONE     0
#define AIO_CANCELED    1
#define AIO_NOTCANCELED 2
#define LIO_READ        0
#define LIO_WRITE       1
#define LIO_NOP         2
#define LIO_WAIT        0
#define LIO_NOWAIT      1
#endif

/* ============================================================================
 * POSIX 消息队列实现
 * 基于内存的简单实现 (因为 Android 不支持 mq_*)
 * ============================================================================ */

#define MQ_MAX_QUEUES    16
#define MQ_MAX_MESSAGES  64
#define MQ_MAX_MSGSIZE   4096
#define MQ_MAX_NAME      64

typedef struct {
    char mtext[MQ_MAX_MSGSIZE];
    size_t msize;
    unsigned int prio;
} mq_message_t;

typedef struct {
    char name[MQ_MAX_NAME];
    int in_use;
    long flags;
    long maxmsg;
    long msgsize;
    int msg_count;
    int head;
    int tail;
    mq_message_t messages[MQ_MAX_MESSAGES];
} mqueue_t;

static mqueue_t g_mqueues[MQ_MAX_QUEUES];
static int g_mq_initialized = 0;

static void mq_init_internal(void) {
    if (!g_mq_initialized) {
        memset(g_mqueues, 0, sizeof(g_mqueues));
        g_mq_initialized = 1;
    }
}

mqd_t mq_open_wrapper(const char* name, int oflag, ...) {
    mq_init_internal();
    
    mode_t mode = 0;
    struct mq_attr* attr = NULL;
    
    if (oflag & O_CREAT) {
        va_list ap;
        va_start(ap, oflag);
        mode = va_arg(ap, mode_t);
        attr = va_arg(ap, struct mq_attr*);
        va_end(ap);
    }
    (void)mode;
    
    LOG_DEBUG("mq_open_wrapper: name='%s', oflag=0x%x", name, oflag);
    
    /* 查找现有或空闲槽 */
    int found = -1;
    int free_slot = -1;
    for (int i = 0; i < MQ_MAX_QUEUES; i++) {
        if (g_mqueues[i].in_use && strcmp(g_mqueues[i].name, name) == 0) {
            found = i;
            break;
        }
        if (!g_mqueues[i].in_use && free_slot < 0) {
            free_slot = i;
        }
    }
    
    if (found >= 0) {
        if ((oflag & O_CREAT) && (oflag & O_EXCL)) {
            errno = EEXIST;
            return (mqd_t)-1;
        }
        return (mqd_t)found;
    }
    
    if (!(oflag & O_CREAT)) {
        errno = ENOENT;
        return (mqd_t)-1;
    }
    
    if (free_slot < 0) {
        errno = EMFILE;
        return (mqd_t)-1;
    }
    
    /* 创建新队列 */
    strncpy(g_mqueues[free_slot].name, name, sizeof(g_mqueues[free_slot].name) - 1);
    g_mqueues[free_slot].in_use = 1;
    g_mqueues[free_slot].flags = 0;
    g_mqueues[free_slot].maxmsg = attr ? attr->mq_maxmsg : MQ_MAX_MESSAGES;
    g_mqueues[free_slot].msgsize = attr ? attr->mq_msgsize : MQ_MAX_MSGSIZE;
    g_mqueues[free_slot].msg_count = 0;
    g_mqueues[free_slot].head = 0;
    g_mqueues[free_slot].tail = 0;
    
    return (mqd_t)free_slot;
}

int mq_close_wrapper(mqd_t mqdes) {
    LOG_DEBUG("mq_close_wrapper: mqdes=%d", (int)mqdes);
    if (mqdes < 0 || mqdes >= MQ_MAX_QUEUES || !g_mqueues[mqdes].in_use) {
        errno = EBADF;
        return -1;
    }
    return 0;
}

int mq_unlink_wrapper(const char* name) {
    LOG_DEBUG("mq_unlink_wrapper: name='%s'", name);
    mq_init_internal();
    
    for (int i = 0; i < MQ_MAX_QUEUES; i++) {
        if (g_mqueues[i].in_use && strcmp(g_mqueues[i].name, name) == 0) {
            g_mqueues[i].in_use = 0;
            return 0;
        }
    }
    errno = ENOENT;
    return -1;
}

int mq_send_wrapper(mqd_t mqdes, const char* msg_ptr, size_t msg_len, unsigned int msg_prio) {
    LOG_DEBUG("mq_send_wrapper: mqdes=%d, msg_len=%zu, prio=%u", (int)mqdes, msg_len, msg_prio);
    
    if (mqdes < 0 || mqdes >= MQ_MAX_QUEUES || !g_mqueues[mqdes].in_use) {
        errno = EBADF;
        return -1;
    }
    
    mqueue_t* mq = &g_mqueues[mqdes];
    
    if (msg_len > (size_t)mq->msgsize) {
        errno = EMSGSIZE;
        return -1;
    }
    
    if (mq->msg_count >= mq->maxmsg) {
        errno = EAGAIN;
        return -1;
    }
    
    /* 添加消息 */
    memcpy(mq->messages[mq->tail].mtext, msg_ptr, msg_len);
    mq->messages[mq->tail].msize = msg_len;
    mq->messages[mq->tail].prio = msg_prio;
    mq->tail = (mq->tail + 1) % MQ_MAX_MESSAGES;
    mq->msg_count++;
    
    return 0;
}

ssize_t mq_receive_wrapper(mqd_t mqdes, char* msg_ptr, size_t msg_len, unsigned int* msg_prio) {
    LOG_DEBUG("mq_receive_wrapper: mqdes=%d, msg_len=%zu", (int)mqdes, msg_len);
    
    if (mqdes < 0 || mqdes >= MQ_MAX_QUEUES || !g_mqueues[mqdes].in_use) {
        errno = EBADF;
        return -1;
    }
    
    mqueue_t* mq = &g_mqueues[mqdes];
    
    if (msg_len < (size_t)mq->msgsize) {
        errno = EMSGSIZE;
        return -1;
    }
    
    if (mq->msg_count == 0) {
        errno = EAGAIN;
        return -1;
    }
    
    /* 获取消息 */
    size_t size = mq->messages[mq->head].msize;
    memcpy(msg_ptr, mq->messages[mq->head].mtext, size);
    if (msg_prio) {
        *msg_prio = mq->messages[mq->head].prio;
    }
    mq->head = (mq->head + 1) % MQ_MAX_MESSAGES;
    mq->msg_count--;
    
    return (ssize_t)size;
}

int mq_getattr_wrapper(mqd_t mqdes, struct mq_attr* attr) {
    LOG_DEBUG("mq_getattr_wrapper: mqdes=%d", (int)mqdes);
    
    if (mqdes < 0 || mqdes >= MQ_MAX_QUEUES || !g_mqueues[mqdes].in_use) {
        errno = EBADF;
        return -1;
    }
    
    mqueue_t* mq = &g_mqueues[mqdes];
    attr->mq_flags = mq->flags;
    attr->mq_maxmsg = mq->maxmsg;
    attr->mq_msgsize = mq->msgsize;
    attr->mq_curmsgs = mq->msg_count;
    
    return 0;
}

int mq_setattr_wrapper(mqd_t mqdes, const struct mq_attr* newattr, struct mq_attr* oldattr) {
    LOG_DEBUG("mq_setattr_wrapper: mqdes=%d", (int)mqdes);
    
    if (mqdes < 0 || mqdes >= MQ_MAX_QUEUES || !g_mqueues[mqdes].in_use) {
        errno = EBADF;
        return -1;
    }
    
    mqueue_t* mq = &g_mqueues[mqdes];
    
    if (oldattr) {
        oldattr->mq_flags = mq->flags;
        oldattr->mq_maxmsg = mq->maxmsg;
        oldattr->mq_msgsize = mq->msgsize;
        oldattr->mq_curmsgs = mq->msg_count;
    }
    
    if (newattr) {
        mq->flags = newattr->mq_flags;
    }
    
    return 0;
}

/* ============================================================================
 * POSIX AIO (异步 I/O) 实现
 * Android bionic 不支持 aio，所以提供同步存根实现
 * ============================================================================ */

int aio_read_wrapper(struct aiocb* aiocbp) {
    if (!aiocbp) {
        errno = EINVAL;
        return -1;
    }
    
    LOG_DEBUG("aio_read_wrapper: fd=%d, offset=%ld, nbytes=%zu",
              aiocbp->aio_fildes, (long)aiocbp->aio_offset, aiocbp->aio_nbytes);
    
    /* 执行同步读取 */
    ssize_t result = pread(aiocbp->aio_fildes, (void*)aiocbp->aio_buf,
                           aiocbp->aio_nbytes, aiocbp->aio_offset);
    
    if (result < 0) {
        aiocbp->__error_code = errno;
        aiocbp->__return_value = -1;
    } else {
        aiocbp->__error_code = 0;
        aiocbp->__return_value = result;
    }
    
    return 0;
}

int aio_write_wrapper(struct aiocb* aiocbp) {
    if (!aiocbp) {
        errno = EINVAL;
        return -1;
    }
    
    LOG_DEBUG("aio_write_wrapper: fd=%d, offset=%ld, nbytes=%zu",
              aiocbp->aio_fildes, (long)aiocbp->aio_offset, aiocbp->aio_nbytes);
    
    /* 执行同步写入 */
    ssize_t result = pwrite(aiocbp->aio_fildes, (const void*)aiocbp->aio_buf,
                            aiocbp->aio_nbytes, aiocbp->aio_offset);
    
    if (result < 0) {
        aiocbp->__error_code = errno;
        aiocbp->__return_value = -1;
    } else {
        aiocbp->__error_code = 0;
        aiocbp->__return_value = result;
    }
    
    return 0;
}

int aio_error_wrapper(const struct aiocb* aiocbp) {
    if (!aiocbp) {
        errno = EINVAL;
        return -1;
    }
    return aiocbp->__error_code;
}

ssize_t aio_return_wrapper(struct aiocb* aiocbp) {
    if (!aiocbp) {
        errno = EINVAL;
        return -1;
    }
    return aiocbp->__return_value;
}

int aio_suspend_wrapper(const struct aiocb* const list[], int nent, const struct timespec* timeout) {
    (void)list;
    (void)nent;
    (void)timeout;
    /* 同步实现中，所有操作都已完成 */
    return 0;
}

int aio_cancel_wrapper(int fd, struct aiocb* aiocbp) {
    (void)fd;
    (void)aiocbp;
    /* 同步实现中，操作要么完成要么不存在 */
    return AIO_ALLDONE;
}

int aio_fsync_wrapper(int op, struct aiocb* aiocbp) {
    (void)op;
    if (!aiocbp) {
        errno = EINVAL;
        return -1;
    }
    
    int result = fsync(aiocbp->aio_fildes);
    aiocbp->__error_code = (result < 0) ? errno : 0;
    aiocbp->__return_value = result;
    
    return 0;
}

int lio_listio_wrapper(int mode, struct aiocb* const list[], int nent, struct sigevent* sig) {
    (void)sig;
    
    LOG_DEBUG("lio_listio_wrapper: mode=%d, nent=%d", mode, nent);
    
    for (int i = 0; i < nent; i++) {
        if (!list[i]) continue;
        
        switch (list[i]->aio_lio_opcode) {
            case LIO_READ:
                aio_read_wrapper(list[i]);
                break;
            case LIO_WRITE:
                aio_write_wrapper(list[i]);
                break;
            case LIO_NOP:
            default:
                break;
        }
    }
    
    return 0;
}

/* ============================================================================
 * System V 共享内存实现
 * 使用 mmap + anonymous 映射模拟
 * ============================================================================ */

#define SHM_MAX_SEGMENTS 64

typedef struct {
    key_t key;
    int in_use;
    size_t size;
    void* addr;
    int nattach;
} shm_segment_t;

static shm_segment_t g_shm_segments[SHM_MAX_SEGMENTS];
static pthread_mutex_t g_shm_mutex = PTHREAD_MUTEX_INITIALIZER;
static int g_shm_initialized = 0;

static void shm_init(void) {
    if (!g_shm_initialized) {
        memset(g_shm_segments, 0, sizeof(g_shm_segments));
        g_shm_initialized = 1;
    }
}

int shmget_wrapper(key_t key, size_t size, int shmflg) {
    pthread_mutex_lock(&g_shm_mutex);
    shm_init();
    
    LOG_DEBUG("shmget_wrapper: key=0x%x, size=%zu, flags=0x%x", key, size, shmflg);
    
    int found = -1;
    int free_slot = -1;
    
    for (int i = 0; i < SHM_MAX_SEGMENTS; i++) {
        if (g_shm_segments[i].in_use && g_shm_segments[i].key == key && key != IPC_PRIVATE) {
            found = i;
            break;
        }
        if (!g_shm_segments[i].in_use && free_slot < 0) {
            free_slot = i;
        }
    }
    
    if (found >= 0) {
        if ((shmflg & IPC_CREAT) && (shmflg & IPC_EXCL)) {
            pthread_mutex_unlock(&g_shm_mutex);
            errno = EEXIST;
            return -1;
        }
        pthread_mutex_unlock(&g_shm_mutex);
        return found;
    }
    
    if (!(shmflg & IPC_CREAT)) {
        pthread_mutex_unlock(&g_shm_mutex);
        errno = ENOENT;
        return -1;
    }
    
    if (free_slot < 0) {
        pthread_mutex_unlock(&g_shm_mutex);
        errno = ENOSPC;
        return -1;
    }
    
    void* addr = mmap(NULL, size, PROT_READ | PROT_WRITE,
                      MAP_SHARED | MAP_ANONYMOUS, -1, 0);
    if (addr == MAP_FAILED) {
        pthread_mutex_unlock(&g_shm_mutex);
        return -1;
    }
    
    g_shm_segments[free_slot].key = key;
    g_shm_segments[free_slot].in_use = 1;
    g_shm_segments[free_slot].size = size;
    g_shm_segments[free_slot].addr = addr;
    g_shm_segments[free_slot].nattach = 0;
    
    pthread_mutex_unlock(&g_shm_mutex);
    return free_slot;
}

void* shmat_wrapper(int shmid, const void* shmaddr, int shmflg) {
    (void)shmaddr;
    (void)shmflg;
    
    pthread_mutex_lock(&g_shm_mutex);
    
    if (shmid < 0 || shmid >= SHM_MAX_SEGMENTS || !g_shm_segments[shmid].in_use) {
        pthread_mutex_unlock(&g_shm_mutex);
        errno = EINVAL;
        return (void*)-1;
    }
    
    g_shm_segments[shmid].nattach++;
    void* addr = g_shm_segments[shmid].addr;
    
    pthread_mutex_unlock(&g_shm_mutex);
    return addr;
}

int shmdt_wrapper(const void* shmaddr) {
    pthread_mutex_lock(&g_shm_mutex);
    
    for (int i = 0; i < SHM_MAX_SEGMENTS; i++) {
        if (g_shm_segments[i].in_use && g_shm_segments[i].addr == shmaddr) {
            g_shm_segments[i].nattach--;
            pthread_mutex_unlock(&g_shm_mutex);
            return 0;
        }
    }
    
    pthread_mutex_unlock(&g_shm_mutex);
    errno = EINVAL;
    return -1;
}

int shmctl_wrapper(int shmid, int cmd, void* buf) {
    (void)buf;
    
    pthread_mutex_lock(&g_shm_mutex);
    
    if (shmid < 0 || shmid >= SHM_MAX_SEGMENTS || !g_shm_segments[shmid].in_use) {
        pthread_mutex_unlock(&g_shm_mutex);
        errno = EINVAL;
        return -1;
    }
    
    if (cmd == IPC_RMID) {
        munmap(g_shm_segments[shmid].addr, g_shm_segments[shmid].size);
        g_shm_segments[shmid].in_use = 0;
        g_shm_segments[shmid].addr = NULL;
    }
    
    pthread_mutex_unlock(&g_shm_mutex);
    return 0;
}

/* ============================================================================
 * System V 信号量实现
 * 使用 pthread mutex/cond 模拟
 * ============================================================================ */

#define SEM_MAX_SETS 64
#define SEM_MAX_PER_SET 64

typedef struct {
    key_t key;
    int in_use;
    int nsems;
    int values[SEM_MAX_PER_SET];
    pthread_mutex_t mutex;
    pthread_cond_t cond;
} sem_set_t;

static sem_set_t g_sem_sets[SEM_MAX_SETS];
static pthread_mutex_t g_sem_mutex = PTHREAD_MUTEX_INITIALIZER;
static int g_sem_initialized = 0;

static void sem_init_internal(void) {
    if (!g_sem_initialized) {
        memset(g_sem_sets, 0, sizeof(g_sem_sets));
        for (int i = 0; i < SEM_MAX_SETS; i++) {
            pthread_mutex_init(&g_sem_sets[i].mutex, NULL);
            pthread_cond_init(&g_sem_sets[i].cond, NULL);
        }
        g_sem_initialized = 1;
    }
}

int semget_wrapper(key_t key, int nsems, int semflg) {
    pthread_mutex_lock(&g_sem_mutex);
    sem_init_internal();
    
    LOG_DEBUG("semget_wrapper: key=0x%x, nsems=%d, flags=0x%x", key, nsems, semflg);
    
    if (nsems < 0 || nsems > SEM_MAX_PER_SET) {
        pthread_mutex_unlock(&g_sem_mutex);
        errno = EINVAL;
        return -1;
    }
    
    int found = -1;
    int free_slot = -1;
    
    for (int i = 0; i < SEM_MAX_SETS; i++) {
        if (g_sem_sets[i].in_use && g_sem_sets[i].key == key && key != IPC_PRIVATE) {
            found = i;
            break;
        }
        if (!g_sem_sets[i].in_use && free_slot < 0) {
            free_slot = i;
        }
    }
    
    if (found >= 0) {
        if ((semflg & IPC_CREAT) && (semflg & IPC_EXCL)) {
            pthread_mutex_unlock(&g_sem_mutex);
            errno = EEXIST;
            return -1;
        }
        pthread_mutex_unlock(&g_sem_mutex);
        return found;
    }
    
    if (!(semflg & IPC_CREAT)) {
        pthread_mutex_unlock(&g_sem_mutex);
        errno = ENOENT;
        return -1;
    }
    
    if (free_slot < 0) {
        pthread_mutex_unlock(&g_sem_mutex);
        errno = ENOSPC;
        return -1;
    }
    
    g_sem_sets[free_slot].key = key;
    g_sem_sets[free_slot].in_use = 1;
    g_sem_sets[free_slot].nsems = nsems;
    memset(g_sem_sets[free_slot].values, 0, sizeof(g_sem_sets[free_slot].values));
    
    pthread_mutex_unlock(&g_sem_mutex);
    return free_slot;
}

struct sembuf_compat {
    unsigned short sem_num;
    short sem_op;
    short sem_flg;
};

int semop_wrapper(int semid, void* sops, size_t nsops) {
    struct sembuf_compat* ops = (struct sembuf_compat*)sops;
    
    if (semid < 0 || semid >= SEM_MAX_SETS || !g_sem_sets[semid].in_use) {
        errno = EINVAL;
        return -1;
    }
    
    sem_set_t* set = &g_sem_sets[semid];
    pthread_mutex_lock(&set->mutex);
    
    for (size_t i = 0; i < nsops; i++) {
        if (ops[i].sem_num >= (unsigned)set->nsems) {
            pthread_mutex_unlock(&set->mutex);
            errno = EFBIG;
            return -1;
        }
        
        if (ops[i].sem_op > 0) {
            set->values[ops[i].sem_num] += ops[i].sem_op;
            pthread_cond_broadcast(&set->cond);
        } else if (ops[i].sem_op < 0) {
            while (set->values[ops[i].sem_num] + ops[i].sem_op < 0) {
                if (ops[i].sem_flg & IPC_NOWAIT) {
                    pthread_mutex_unlock(&set->mutex);
                    errno = EAGAIN;
                    return -1;
                }
                pthread_cond_wait(&set->cond, &set->mutex);
            }
            set->values[ops[i].sem_num] += ops[i].sem_op;
        }
    }
    
    pthread_mutex_unlock(&set->mutex);
    return 0;
}

int semctl_wrapper(int semid, int semnum, int cmd, ...) {
    if (semid < 0 || semid >= SEM_MAX_SETS || !g_sem_sets[semid].in_use) {
        errno = EINVAL;
        return -1;
    }
    
    sem_set_t* set = &g_sem_sets[semid];
    
    switch (cmd) {
        case IPC_RMID:
            pthread_mutex_lock(&g_sem_mutex);
            set->in_use = 0;
            pthread_mutex_unlock(&g_sem_mutex);
            return 0;
            
        case 12: /* GETVAL */
            if (semnum < 0 || semnum >= set->nsems) {
                errno = EINVAL;
                return -1;
            }
            return set->values[semnum];
            
        case 16: { /* SETVAL */
            if (semnum < 0 || semnum >= set->nsems) {
                errno = EINVAL;
                return -1;
            }
            va_list ap;
            va_start(ap, cmd);
            int val = va_arg(ap, int);
            va_end(ap);
            set->values[semnum] = val;
            return 0;
        }
        
        default:
            return 0;
    }
}

/* ============================================================================
 * System V 消息队列实现
 * ============================================================================ */

#define MSGQ_MAX_QUEUES   16
#define MSGQ_MAX_MESSAGES 64
#define MSGQ_MAX_MSGSIZE  4096

typedef struct {
    long mtype;
    char mtext[MSGQ_MAX_MSGSIZE];
    size_t msize;
} msgq_message_t;

typedef struct {
    key_t key;
    int in_use;
    int msg_count;
    int head;
    int tail;
    msgq_message_t messages[MSGQ_MAX_MESSAGES];
    pthread_mutex_t mutex;
    pthread_cond_t cond;
} msgq_t;

static msgq_t g_msgqs[MSGQ_MAX_QUEUES];
static pthread_mutex_t g_msgq_mutex = PTHREAD_MUTEX_INITIALIZER;
static int g_msgq_initialized = 0;

static void msgq_init(void) {
    if (!g_msgq_initialized) {
        memset(g_msgqs, 0, sizeof(g_msgqs));
        for (int i = 0; i < MSGQ_MAX_QUEUES; i++) {
            pthread_mutex_init(&g_msgqs[i].mutex, NULL);
            pthread_cond_init(&g_msgqs[i].cond, NULL);
        }
        g_msgq_initialized = 1;
    }
}

int msgget_wrapper(key_t key, int msgflg) {
    pthread_mutex_lock(&g_msgq_mutex);
    msgq_init();
    
    LOG_DEBUG("msgget_wrapper: key=0x%x, flags=0x%x", key, msgflg);
    
    int found = -1;
    int free_slot = -1;
    
    for (int i = 0; i < MSGQ_MAX_QUEUES; i++) {
        if (g_msgqs[i].in_use && g_msgqs[i].key == key && key != IPC_PRIVATE) {
            found = i;
            break;
        }
        if (!g_msgqs[i].in_use && free_slot < 0) {
            free_slot = i;
        }
    }
    
    if (found >= 0) {
        if ((msgflg & IPC_CREAT) && (msgflg & IPC_EXCL)) {
            pthread_mutex_unlock(&g_msgq_mutex);
            errno = EEXIST;
            return -1;
        }
        pthread_mutex_unlock(&g_msgq_mutex);
        return found;
    }
    
    if (!(msgflg & IPC_CREAT)) {
        pthread_mutex_unlock(&g_msgq_mutex);
        errno = ENOENT;
        return -1;
    }
    
    if (free_slot < 0) {
        pthread_mutex_unlock(&g_msgq_mutex);
        errno = ENOSPC;
        return -1;
    }
    
    g_msgqs[free_slot].key = key;
    g_msgqs[free_slot].in_use = 1;
    g_msgqs[free_slot].msg_count = 0;
    g_msgqs[free_slot].head = 0;
    g_msgqs[free_slot].tail = 0;
    
    pthread_mutex_unlock(&g_msgq_mutex);
    return free_slot;
}

int msgsnd_wrapper(int msqid, const void* msgp, size_t msgsz, int msgflg) {
    if (msqid < 0 || msqid >= MSGQ_MAX_QUEUES || !g_msgqs[msqid].in_use) {
        errno = EINVAL;
        return -1;
    }
    
    if (msgsz > MSGQ_MAX_MSGSIZE) {
        errno = EINVAL;
        return -1;
    }
    
    const long* mtype = (const long*)msgp;
    const char* mtext = (const char*)msgp + sizeof(long);
    
    msgq_t* q = &g_msgqs[msqid];
    pthread_mutex_lock(&q->mutex);
    
    while (q->msg_count >= MSGQ_MAX_MESSAGES) {
        if (msgflg & IPC_NOWAIT) {
            pthread_mutex_unlock(&q->mutex);
            errno = EAGAIN;
            return -1;
        }
        pthread_cond_wait(&q->cond, &q->mutex);
    }
    
    q->messages[q->tail].mtype = *mtype;
    memcpy(q->messages[q->tail].mtext, mtext, msgsz);
    q->messages[q->tail].msize = msgsz;
    q->tail = (q->tail + 1) % MSGQ_MAX_MESSAGES;
    q->msg_count++;
    
    pthread_cond_broadcast(&q->cond);
    pthread_mutex_unlock(&q->mutex);
    return 0;
}

ssize_t msgrcv_wrapper(int msqid, void* msgp, size_t msgsz, long msgtyp, int msgflg) {
    if (msqid < 0 || msqid >= MSGQ_MAX_QUEUES || !g_msgqs[msqid].in_use) {
        errno = EINVAL;
        return -1;
    }
    
    msgq_t* q = &g_msgqs[msqid];
    pthread_mutex_lock(&q->mutex);
    
    while (1) {
        for (int i = 0; i < q->msg_count; i++) {
            int idx = (q->head + i) % MSGQ_MAX_MESSAGES;
            int match = 0;
            
            if (msgtyp == 0) {
                match = 1;
            } else if (msgtyp > 0) {
                match = (q->messages[idx].mtype == msgtyp);
            } else {
                match = (q->messages[idx].mtype <= -msgtyp);
            }
            
            if (match) {
                long* mtype_out = (long*)msgp;
                char* mtext_out = (char*)msgp + sizeof(long);
                
                *mtype_out = q->messages[idx].mtype;
                size_t copy_size = (q->messages[idx].msize < msgsz) ? q->messages[idx].msize : msgsz;
                memcpy(mtext_out, q->messages[idx].mtext, copy_size);
                
                /* 移除消息 */
                for (int j = i; j < q->msg_count - 1; j++) {
                    int from = (q->head + j + 1) % MSGQ_MAX_MESSAGES;
                    int to = (q->head + j) % MSGQ_MAX_MESSAGES;
                    q->messages[to] = q->messages[from];
                }
                q->msg_count--;
                
                pthread_cond_broadcast(&q->cond);
                pthread_mutex_unlock(&q->mutex);
                return (ssize_t)copy_size;
            }
        }
        
        if (msgflg & IPC_NOWAIT) {
            pthread_mutex_unlock(&q->mutex);
            errno = ENOMSG;
            return -1;
        }
        
        pthread_cond_wait(&q->cond, &q->mutex);
    }
}

int msgctl_wrapper(int msqid, int cmd, void* buf) {
    (void)buf;
    
    if (msqid < 0 || msqid >= MSGQ_MAX_QUEUES || !g_msgqs[msqid].in_use) {
        errno = EINVAL;
        return -1;
    }
    
    if (cmd == IPC_RMID) {
        pthread_mutex_lock(&g_msgq_mutex);
        g_msgqs[msqid].in_use = 0;
        pthread_mutex_unlock(&g_msgq_mutex);
    }
    
    return 0;
}
