#if !defined(WINDOWS_COMPAT_H) && defined(_MSC_VER)
#define WINDOWS_COMPAT_H

#include <stdarg.h>

int vasprintf(char **strp, const char *fmt, va_list ap);
int asprintf(char **strp, const char *fmt, ...);
void *memrchr(const void *s, int c, size_t n);

#endif
