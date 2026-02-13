/*
 * Thin C wrapper around Google RE2 (C++ API).
 * Compiled as a shared library and called via Panama FFI from JVM
 *
 * Build:  g++ -shared -fPIC -O2 -o lib/libre2_c.so lib/re2_c.cpp -lre2
 */

#include <re2/re2.h>
#include <cstring>

extern "C" {

/* Compile a pattern. Returns opaque pointer to RE2 object (or nullptr). */
void *re2c_compile(const char *pattern) {
    RE2::Options opts;
    opts.set_log_errors(false);
    return new RE2(pattern, opts);
}

/* Free a compiled RE2 object. */
void re2c_free(void *re) {
    delete static_cast<RE2 *>(re);
}

/* 1 if the pattern compiled successfully, 0 otherwise. */
int re2c_ok(void *re) {
    return static_cast<RE2 *>(re)->ok() ? 1 : 0;
}

/* Error message string (valid while the RE2 object lives). */
const char *re2c_error(void *re) {
    return static_cast<RE2 *>(re)->error().c_str();
}

/* Number of capturing groups in the pattern. */
int re2c_num_groups(void *re) {
    return static_cast<RE2 *>(re)->NumberOfCapturingGroups();
}

/*
 * Find a match.
 *
 *   re        – compiled RE2*
 *   text      – subject string (UTF-8 bytes, not necessarily NUL-terminated)
 *   text_len  – byte length of text
 *   start     – byte offset to start searching from
 *   anchor    – 0 = UNANCHORED, 2 = ANCHOR_BOTH
 *   positions – output array of int pairs: [start0, end0, start1, end1, …]
 *   max_pairs – capacity of positions array (in pairs, i.e. positions has 2*max_pairs ints)
 *
 * Returns the number of groups matched (including group 0), or 0 on no match.
 * positions[2*i] / positions[2*i+1] are byte start/end for group i.
 * Unmatched optional groups get (-1, -1).
 */
int re2c_find(void *re, const char *text, int text_len,
              int start, int anchor,
              int *positions, int max_pairs) {
    RE2 *r = static_cast<RE2 *>(re);
    int ngroups = r->NumberOfCapturingGroups() + 1;  /* +1 for group 0 */
    if (ngroups > max_pairs) ngroups = max_pairs;

    re2::StringPiece input(text, text_len);
    re2::StringPiece submatch[ngroups];

    RE2::Anchor a;
    switch (anchor) {
        case 2:  a = RE2::ANCHOR_BOTH; break;
        default: a = RE2::UNANCHORED;  break;
    }

    bool matched = r->Match(input, start, text_len, a, submatch, ngroups);
    if (!matched) return 0;

    for (int i = 0; i < ngroups; i++) {
        if (submatch[i].data() == nullptr) {
            positions[2 * i]     = -1;
            positions[2 * i + 1] = -1;
        } else {
            positions[2 * i]     = static_cast<int>(submatch[i].data() - text);
            positions[2 * i + 1] = static_cast<int>(submatch[i].data() - text + submatch[i].size());
        }
    }
    return ngroups;
}

}  /* extern "C" */
