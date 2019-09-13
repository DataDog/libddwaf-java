#include "windows_compat.h"

#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>

int vasprintf(char **strp, const char *fmt, va_list ap)
{
    if (!strp) {
        return -1;
    }

    va_list ap_copy;
    va_copy(ap_copy, ap);
    int len = _vscprintf(fmt, ap);
    va_end(ap_copy);
    if (len == -1) {
        return -1;
    }

    size_t size = (size_t)len + 1;
    char *str = malloc(size);
    if (!str) {
        return -1;
    }

    int r = vsprintf_s(str, size, fmt, ap);
    if (r == -1) {
        free(str);
        return -1;
    }
    *strp = str;
    return r;
}

int asprintf(char **strp, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int r = vasprintf(strp, fmt, ap);
    va_end(ap);
    return r;
}

void *memrchr(const void *buf, int c, size_t n)
{
    const char *p = buf;

    while (1) {
        if (n-- == 0) {
            return NULL;
        }

        if (*p-- == (char)c) {
            break;
        }

    }

    return (void *)(p + 1);
}
