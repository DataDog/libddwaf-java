#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "logging.h"
#include "common.h"

static const char *_remove_path(const char *path);

void _java_wrap_exc_relay(JNIEnv *env,
        const char *format,
        const char *file, const char *function, int line, ...)
{
    char *epilog = NULL;
    char *user_msg = NULL;
    char *final_msg = NULL;
    jstring message_obj = NULL;

    if (!rte_constr_cause || !jcls_rte) {
        return;
    }

    jthrowable prev_throwable = JNI(ExceptionOccurred);
    jthrowable new_throwable = NULL;
    if (!prev_throwable) {
        return;
    }

    int size_epilog = asprintf(
                &epilog, "during native function %s, file %s, line %d",
                function, _remove_path(file), line);
    if (size_epilog < 0) {
        goto error;
    }

    va_list ap;
    va_start(ap, line);
    int size_user_msg = vasprintf(&user_msg, format, ap);
    va_end(ap);
    if (size_user_msg < 0) {
        goto error;
    }

    final_msg = malloc((size_t)size_epilog + (size_t)size_user_msg + 2);
    if (!final_msg) {
        goto error;
    }

    char *msg_write = final_msg;
    memcpy(msg_write, user_msg, (size_t)size_user_msg);
    msg_write += size_user_msg;
    *(msg_write++) = ' ';
    memcpy(msg_write, epilog, (size_t)size_epilog);
    msg_write += size_epilog;
    *msg_write = '\0';

    JNI(ExceptionClear);

    message_obj = JNI(NewStringUTF, final_msg);
    if (JNI(ExceptionOccurred)) {
        JNI(ExceptionClear);
        JNI(Throw, prev_throwable);
        goto error;
    }

    new_throwable = JNI(NewObject, jcls_rte, rte_constr_cause,
                        message_obj, prev_throwable);
    if (JNI(ExceptionOccurred)) {
        JNI(ExceptionClear);
        JNI(Throw, prev_throwable);
        goto error;
    }

    JNI(Throw, new_throwable);

error:
    if (prev_throwable) {
        JNI(DeleteLocalRef, prev_throwable);
    }
    if (message_obj) {
        JNI(DeleteLocalRef, message_obj);
    }
    if (new_throwable) {
        JNI(DeleteLocalRef, new_throwable);
    }
    free(epilog);
    free(user_msg);
    free(final_msg);
}

static const char *_remove_path(const char *path)
{
    const char *res = path;
    while (*path) {
        if (*path ==  '/') {
            res = path + 1;
        }
        path++;
    }
    return res;
}
