#include <stdlib.h>

struct base64_state {
    int eof;
    int bytes;
    unsigned char carry;
};

void base64_encode(const char *src, size_t srclen, char *out, size_t *outlen);
void base64_stream_encode_plain(struct base64_state *state, const char *src,
                                size_t srclen, char *out, size_t *outlen);
void base64_stream_encode_final(struct base64_state *state, char *out,
                                size_t *outlen);
