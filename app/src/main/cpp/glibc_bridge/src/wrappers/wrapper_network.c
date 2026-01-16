/*
 * glibc-bridge - 网络/套接字函数包装
 * 
 * 包含 socket, getaddrinfo, inet_pton, setsockopt 等网络相关函数
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <fcntl.h>

#include "../include/wrappers.h"
#include "../include/private.h"
#include "../elf/log.h"
#include "wrapper_path.h"

/* ============================================================================
 * socket 包装
 * 
 * 处理 glibc 和 bionic 之间的 socket 类型常量差异
 * ============================================================================ */

/* glibc SOCK_* 标志 */
#define GLIBC_SOCK_CLOEXEC  02000000
#define GLIBC_SOCK_NONBLOCK 00004000

int socket_wrapper(int domain, int type, int protocol) {
    LOG_DEBUG("socket_wrapper: domain=%d, type=%d, protocol=%d", 
              domain, type, protocol);
    
    /* 从 type 中提取标志 */
    int flags = 0;
    int base_type = type & 0xFF;
    
    /* 转换 glibc 标志到 bionic 标志 */
    if (type & GLIBC_SOCK_CLOEXEC) {
        flags |= SOCK_CLOEXEC;
    }
    if (type & GLIBC_SOCK_NONBLOCK) {
        flags |= SOCK_NONBLOCK;
    }
    
    int new_type = base_type | flags;
    
    int fd = socket(domain, new_type, protocol);
    
    LOG_DEBUG("socket_wrapper: 返回 fd=%d", fd);
    return fd;
}

/* ============================================================================
 * socketpair 包装
 * ============================================================================ */

int socketpair_wrapper(int domain, int type, int protocol, int sv[2]) {
    int flags = 0;
    int base_type = type & 0xFF;
    
    if (type & GLIBC_SOCK_CLOEXEC) {
        flags |= SOCK_CLOEXEC;
    }
    if (type & GLIBC_SOCK_NONBLOCK) {
        flags |= SOCK_NONBLOCK;
    }
    
    return socketpair(domain, base_type | flags, protocol, sv);
}

/* ============================================================================
 * connect / bind / listen / accept 包装
 * ============================================================================ */

int connect_wrapper(int sockfd, const struct sockaddr *addr, socklen_t addrlen) {
    return connect(sockfd, addr, addrlen);
}

int bind_wrapper(int sockfd, const struct sockaddr *addr, socklen_t addrlen) {
    return bind(sockfd, addr, addrlen);
}

int listen_wrapper(int sockfd, int backlog) {
    return listen(sockfd, backlog);
}

int accept_wrapper(int sockfd, struct sockaddr *addr, socklen_t *addrlen) {
    return accept(sockfd, addr, addrlen);
}

int accept4_wrapper(int sockfd, struct sockaddr *addr, socklen_t *addrlen, int flags) {
    /* 转换标志 */
    int new_flags = 0;
    if (flags & GLIBC_SOCK_CLOEXEC) new_flags |= SOCK_CLOEXEC;
    if (flags & GLIBC_SOCK_NONBLOCK) new_flags |= SOCK_NONBLOCK;
    
    return accept4(sockfd, addr, addrlen, new_flags);
}

/* ============================================================================
 * send / recv 系列包装
 * ============================================================================ */

ssize_t send_wrapper(int sockfd, const void *buf, size_t len, int flags) {
    return send(sockfd, buf, len, flags);
}

ssize_t recv_wrapper(int sockfd, void *buf, size_t len, int flags) {
    return recv(sockfd, buf, len, flags);
}

ssize_t sendto_wrapper(int sockfd, const void *buf, size_t len, int flags,
                        const struct sockaddr *dest_addr, socklen_t addrlen) {
    return sendto(sockfd, buf, len, flags, dest_addr, addrlen);
}

ssize_t recvfrom_wrapper(int sockfd, void *buf, size_t len, int flags,
                          struct sockaddr *src_addr, socklen_t *addrlen) {
    return recvfrom(sockfd, buf, len, flags, src_addr, addrlen);
}

ssize_t sendmsg_wrapper(int sockfd, const struct msghdr *msg, int flags) {
    return sendmsg(sockfd, msg, flags);
}

ssize_t recvmsg_wrapper(int sockfd, struct msghdr *msg, int flags) {
    return recvmsg(sockfd, msg, flags);
}

/* ============================================================================
 * setsockopt / getsockopt 包装
 * 
 * 处理 Android 上某些选项不支持的情况
 * ============================================================================ */

int setsockopt_wrapper(int sockfd, int level, int optname,
                       const void *optval, socklen_t optlen) {
    LOG_DEBUG("setsockopt_wrapper: fd=%d level=%d optname=%d", 
              sockfd, level, optname);
    
    int result = setsockopt(sockfd, level, optname, optval, optlen);
    
    if (result < 0) {
        int saved_errno = errno;
        
        /* 某些选项在 Android 上不支持，静默忽略 */
        if (saved_errno == ENOPROTOOPT || saved_errno == EINVAL) {
            /* SO_REUSEPORT, TCP_FASTOPEN 等可能不支持 */
            if (level == SOL_SOCKET || level == IPPROTO_TCP) {
                LOG_DEBUG("setsockopt_wrapper: 选项不支持，忽略");
                return 0;
            }
        }
        
        errno = saved_errno;
    }
    
    return result;
}

int getsockopt_wrapper(int sockfd, int level, int optname,
                       void *optval, socklen_t *optlen) {
    LOG_DEBUG("getsockopt_wrapper: fd=%d level=%d optname=%d", 
              sockfd, level, optname);
    
    int result = getsockopt(sockfd, level, optname, optval, optlen);
    
    if (result < 0 && (errno == ENOPROTOOPT || errno == EINVAL)) {
        /* 某些选项在 Android 上不支持，返回默认值 */
        if (optval && optlen && *optlen >= sizeof(int)) {
            *(int*)optval = 0;
            *optlen = sizeof(int);
            return 0;
        }
    }
    
    return result;
}

/* ============================================================================
 * getaddrinfo / freeaddrinfo 包装
 * struct addrinfo 兼容
 * ============================================================================ */

int getaddrinfo_wrapper(const char *node, const char *service,
                        const struct addrinfo *hints, struct addrinfo **res) {
    return getaddrinfo(node, service, hints, res);
}

void freeaddrinfo_wrapper(struct addrinfo *res) {
    freeaddrinfo(res);
}

const char *gai_strerror_wrapper(int errcode) {
    return gai_strerror(errcode);
}

/* ============================================================================
 * getnameinfo 包装
 * ============================================================================ */

int getnameinfo_wrapper(const struct sockaddr *sa, socklen_t salen,
                        char *host, socklen_t hostlen,
                        char *serv, socklen_t servlen, int flags) {
    return getnameinfo(sa, salen, host, hostlen, serv, servlen, flags);
}

/* ============================================================================
 * inet_pton / inet_ntop 包装
 * ============================================================================ */

int inet_pton_wrapper(int af, const char *src, void *dst) {
    return inet_pton(af, src, dst);
}

const char *inet_ntop_wrapper(int af, const void *src,
                               char *dst, socklen_t size) {
    return inet_ntop(af, src, dst, size);
}

/* ============================================================================
 * inet_addr / inet_aton / inet_ntoa 包装
 * ============================================================================ */

in_addr_t inet_addr_wrapper(const char *cp) {
    return inet_addr(cp);
}

int inet_aton_wrapper(const char *cp, struct in_addr *inp) {
    return inet_aton(cp, inp);
}

char *inet_ntoa_wrapper(struct in_addr in) {
    return inet_ntoa(in);
}

/* ============================================================================
 * gethostbyname / gethostbyaddr 包装 (已废弃但仍需支持)
 * ============================================================================ */

struct hostent *gethostbyname_wrapper(const char *name) {
    return gethostbyname(name);
}

struct hostent *gethostbyaddr_wrapper(const void *addr, socklen_t len, int type) {
    return gethostbyaddr(addr, len, type);
}

/* ============================================================================
 * getpeername / getsockname 包装
 * ============================================================================ */

int getpeername_wrapper(int sockfd, struct sockaddr *addr, socklen_t *addrlen) {
    return getpeername(sockfd, addr, addrlen);
}

int getsockname_wrapper(int sockfd, struct sockaddr *addr, socklen_t *addrlen) {
    return getsockname(sockfd, addr, addrlen);
}

/* ============================================================================
 * shutdown 包装
 * ============================================================================ */

int shutdown_wrapper(int sockfd, int how) {
    return shutdown(sockfd, how);
}

/* ============================================================================
 * htons / htonl / ntohs / ntohl 包装
 * ============================================================================ */

uint16_t htons_wrapper(uint16_t hostshort) {
    return htons(hostshort);
}

uint32_t htonl_wrapper(uint32_t hostlong) {
    return htonl(hostlong);
}

uint16_t ntohs_wrapper(uint16_t netshort) {
    return ntohs(netshort);
}

uint32_t ntohl_wrapper(uint32_t netlong) {
    return ntohl(netlong);
}

/* ============================================================================
 * if_nametoindex / if_indextoname 包装
 * ============================================================================ */

#include <net/if.h>

unsigned int if_nametoindex_wrapper(const char *ifname) {
    return if_nametoindex(ifname);
}

char *if_indextoname_wrapper(unsigned int ifindex, char *ifname) {
    return if_indextoname(ifindex, ifname);
}
