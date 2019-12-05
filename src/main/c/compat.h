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
