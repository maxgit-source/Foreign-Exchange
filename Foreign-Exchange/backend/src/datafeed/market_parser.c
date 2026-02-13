#include "datafeed/market_parser.h"
#include "datafeed/normalizer.h"

#include <ctype.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>

static const char* find_key(const char* data, size_t len, const char* key) {
    size_t key_len = strlen(key);
    for (size_t i = 0; i + key_len + 2 < len; ++i) {
        if (data[i] != '"') continue;
        if (memcmp(&data[i + 1], key, key_len) == 0 && data[i + 1 + key_len] == '"') {
            return &data[i + 1 + key_len + 1];
        }
    }
    return NULL;
}

static const char* skip_ws(const char* p, const char* end) {
    while (p < end && (*p == ' ' || *p == '\t' || *p == '\n' || *p == '\r')) p++;
    return p;
}

static ArgentumStatus parse_string(const char* p, const char* end, char* out, size_t out_len) {
    p = skip_ws(p, end);
    if (p >= end || *p != '"') return ARGENTUM_ERR_PARSE;
    p++;
    size_t i = 0;
    while (p < end && *p != '"') {
        if (i + 1 >= out_len) return ARGENTUM_ERR_RANGE;
        out[i++] = *p++;
    }
    if (p >= end || *p != '"') return ARGENTUM_ERR_PARSE;
    out[i] = '\0';
    return ARGENTUM_OK;
}

static ArgentumStatus parse_number(const char* p, const char* end, double* out) {
    p = skip_ws(p, end);
    if (p >= end) return ARGENTUM_ERR_PARSE;

    char tmp[64];
    size_t i = 0;
    while (p < end && (isdigit((unsigned char)*p) || *p == '-' || *p == '+' || *p == '.' || *p == 'e' || *p == 'E')) {
        if (i + 1 >= sizeof(tmp)) return ARGENTUM_ERR_RANGE;
        tmp[i++] = *p++;
    }
    tmp[i] = '\0';
    if (i == 0) return ARGENTUM_ERR_PARSE;
    *out = strtod(tmp, NULL);
    return ARGENTUM_OK;
}

static ArgentumStatus parse_u64(const char* p, const char* end, uint64_t* out) {
    p = skip_ws(p, end);
    if (p >= end) return ARGENTUM_ERR_PARSE;

    char tmp[32];
    size_t i = 0;
    while (p < end && isdigit((unsigned char)*p)) {
        if (i + 1 >= sizeof(tmp)) return ARGENTUM_ERR_RANGE;
        tmp[i++] = *p++;
    }
    tmp[i] = '\0';
    if (i == 0) return ARGENTUM_ERR_PARSE;
    *out = (uint64_t)strtoull(tmp, NULL, 10);
    return ARGENTUM_OK;
}

static ArgentumStatus parse_json(const char* data, size_t len, MarketTick* out) {
    if (!data || !out) return ARGENTUM_ERR_INVALID;
    const char* end = data + len;

    memset(out, 0, sizeof(*out));

    const char* k_ts = find_key(data, len, "timestamp_ns");
    if (!k_ts) k_ts = find_key(data, len, "ts");
    const char* k_price = find_key(data, len, "price");
    const char* k_qty = find_key(data, len, "quantity");
    if (!k_qty) k_qty = find_key(data, len, "volume");
    const char* k_symbol = find_key(data, len, "symbol");
    const char* k_side = find_key(data, len, "side");
    const char* k_source = find_key(data, len, "source");

    if (!k_price || !k_qty || !k_symbol || !k_side) return ARGENTUM_ERR_PARSE;

    if (k_ts) {
        if (parse_u64(k_ts + 1, end, &out->timestamp_ns) != ARGENTUM_OK) return ARGENTUM_ERR_PARSE;
    }

    if (parse_number(k_price + 1, end, &out->price) != ARGENTUM_OK) return ARGENTUM_ERR_PARSE;
    if (parse_number(k_qty + 1, end, &out->quantity) != ARGENTUM_OK) return ARGENTUM_ERR_PARSE;

    char raw_symbol[SYMBOL_LEN * 2] = {0};
    if (parse_string(k_symbol + 1, end, raw_symbol, sizeof(raw_symbol)) != ARGENTUM_OK) return ARGENTUM_ERR_PARSE;
    if (normalize_symbol(raw_symbol, out->symbol, sizeof(out->symbol)) != ARGENTUM_OK) return ARGENTUM_ERR_INVALID;

    char side_str[8] = {0};
    if (parse_string(k_side + 1, end, side_str, sizeof(side_str)) != ARGENTUM_OK) return ARGENTUM_ERR_PARSE;

    if (side_str[0] == 'B' || side_str[0] == 'b' || strcmp(side_str, "BUY") == 0 || strcmp(side_str, "buy") == 0) {
        out->side = SIDE_BUY;
    } else if (side_str[0] == 'S' || side_str[0] == 's' || strcmp(side_str, "SELL") == 0 || strcmp(side_str, "sell") == 0) {
        out->side = SIDE_SELL;
    } else if (side_str[0] == '1') {
        out->side = SIDE_BUY;
    } else if (side_str[0] == '2') {
        out->side = SIDE_SELL;
    } else {
        return ARGENTUM_ERR_PARSE;
    }

    if (k_source) {
        char source[SOURCE_LEN] = {0};
        if (parse_string(k_source + 1, end, source, sizeof(source)) == ARGENTUM_OK) {
            strncpy(out->source, source, sizeof(out->source) - 1);
        }
    }

    return ARGENTUM_OK;
}

ArgentumStatus parse_market_message(FeedFormat format, const char* data, size_t len, MarketTick* out) {
    switch (format) {
        case FEED_FORMAT_JSON:
            return parse_json(data, len, out);
        case FEED_FORMAT_FIX:
        case FEED_FORMAT_SBE:
        default:
            return ARGENTUM_ERR_INVALID;
    }
}
