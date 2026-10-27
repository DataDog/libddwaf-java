/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2026 Datadog, Inc.
 */

/*
 * LD_AUDIT module used by LazyBindingGotClobberTest (APPSEC-69052).
 *
 * la_objopen() runs after the dynamic linker has mapped a new object but
 * before it processes the object's relocations. For the object whose name
 * ends with GOT_CLOBBER_TARGET, this module writes an absolute function
 * pointer into the GOT slot of GOT_CLOBBER_SYMBOL, simulating an in-process
 * GOT patcher that races dlopen().
 *
 * With lazy binding, glibc then applies JUMP_SLOT relocations as
 * "*slot += l_addr", turning the absolute pointer into garbage and crashing on
 * the first call through the PLT. With BIND_NOW, glibc overwrites the slot
 * with the resolved symbol and the premature write is harmless.
 */

#define _GNU_SOURCE
#include <elf.h>
#include <link.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(__aarch64__)
#define JUMP_SLOT_TYPE R_AARCH64_JUMP_SLOT
#elif defined(__x86_64__)
#define JUMP_SLOT_TYPE R_X86_64_JUMP_SLOT
#else
#error "unsupported architecture"
#endif

unsigned int la_version(unsigned int version)
{
    (void) version;
    return LAV_CURRENT;
}

static int ends_with(const char *str, const char *suffix)
{
    size_t str_len = strlen(str);
    size_t suffix_len = strlen(suffix);
    return str_len >= suffix_len &&
           strcmp(str + str_len - suffix_len, suffix) == 0;
}

/* Depending on the glibc version, d_ptr may or may not be relocated yet. */
static uintptr_t dyn_ptr(const struct link_map *map, ElfW(Addr) d_ptr)
{
    return d_ptr < map->l_addr ? d_ptr + map->l_addr : d_ptr;
}

unsigned int la_objopen(struct link_map *map, Lmid_t lmid, uintptr_t *cookie)
{
    (void) lmid;
    (void) cookie;

    const char *target = getenv("GOT_CLOBBER_TARGET");
    const char *symbol = getenv("GOT_CLOBBER_SYMBOL");
    if (!target || !symbol || !map->l_name || !ends_with(map->l_name, target)) {
        return 0;
    }

    const ElfW(Rela) *jmprel = NULL;
    size_t pltrelsz = 0;
    const ElfW(Sym) *symtab = NULL;
    const char *strtab = NULL;
    for (const ElfW(Dyn) *dyn = map->l_ld; dyn->d_tag != DT_NULL; dyn++) {
        switch (dyn->d_tag) {
        case DT_JMPREL:
            jmprel = (const ElfW(Rela) *) dyn_ptr(map, dyn->d_un.d_ptr);
            break;
        case DT_PLTRELSZ:
            pltrelsz = dyn->d_un.d_val;
            break;
        case DT_SYMTAB:
            symtab = (const ElfW(Sym) *) dyn_ptr(map, dyn->d_un.d_ptr);
            break;
        case DT_STRTAB:
            strtab = (const char *) dyn_ptr(map, dyn->d_un.d_ptr);
            break;
        }
    }
    if (!jmprel || !symtab || !strtab) {
        fprintf(stderr, "GOT_CLOBBER: no PLT relocations in %s\n", map->l_name);
        return 0;
    }

    for (size_t i = 0; i < pltrelsz / sizeof(ElfW(Rela)); i++) {
        const ElfW(Rela) *rela = &jmprel[i];
        if (ELF64_R_TYPE(rela->r_info) != JUMP_SLOT_TYPE) {
            continue;
        }
        const ElfW(Sym) *sym = &symtab[ELF64_R_SYM(rela->r_info)];
        if (strcmp(strtab + sym->st_name, symbol) != 0) {
            continue;
        }
        uintptr_t *slot = (uintptr_t *) (map->l_addr + rela->r_offset);
        uintptr_t before = *slot;
        /* any valid absolute function pointer works: use malloc itself */
        *slot = (uintptr_t) &malloc;
        fprintf(stderr,
                "GOT_CLOBBER: patched %s in %s (slot %p: %#lx -> %#lx, l_addr "
                "%#lx)\n",
                symbol, map->l_name, (void *) slot, (unsigned long) before,
                (unsigned long) *slot, (unsigned long) map->l_addr);
        return 0;
    }

    fprintf(stderr, "GOT_CLOBBER: symbol %s not found in %s\n", symbol,
            map->l_name);
    return 0;
}
