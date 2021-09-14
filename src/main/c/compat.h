/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

#if !defined(SQREEN_COMPAT_H) && (defined(_MSC_VER) || defined(__APPLE__))
#define SQREEN_COMPAT_H

#include <stddef.h>
#include <stdarg.h>

#ifdef _MSC_VER
int vasprintf(char **strp, const char *fmt, va_list ap);
int asprintf(char **strp, const char *fmt, ...);
#define CLOCK_MONOTONIC 0
typedef int clockid_t;
#include <time.h>
long clock_gettime(clockid_t which_clock, struct timespec *tp);
#endif
void *memrchr(const void *s, int c, size_t n);
#endif
