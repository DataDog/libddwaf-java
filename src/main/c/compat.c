/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#include "compat.h"

#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>

#ifdef _MSC_VER
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
#endif

#ifdef _MSC_VER
#include <windows.h>

long clock_gettime(clockid_t which_clock, struct timespec* tp)
{
    static double freq_per_ns = -1.0;
    if (freq_per_ns < 0.0) {
        LARGE_INTEGER freq_per_sec;
        QueryPerformanceFrequency(&freq_per_sec.QuadPart);
        freq_per_ns = freq_per_sec.QuadPart / 1000000000.0;
    }

    if (!tp) {
        return -1;
    }

    LARGE_INTEGER counter;
    QueryPerformanceCounter(&counter.QuadPart); //always succeeds

    int64_t counter_ns = (int64_t)(((double)counter.QuadPart) / freq_per_ns);

    tp->tv_sec = counter_ns / 1000000000UL;
    tp->tv_nsec = counter_ns % 1000000000UL;

    return 0;
}
#endif

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

    return (void *)(uintptr_t)(p + 1);
}
