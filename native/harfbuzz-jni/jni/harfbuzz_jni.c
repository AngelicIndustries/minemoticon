#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <hb.h>
#include <hb-ot.h>
#include <hb-raster.h>

#include "industries_angelic_minemoticon_harfbuzz_HarfBuzz.h"

#define GLYPH_COLOR_PAINT 1
#define GLYPH_PNG 2
#define GLYPH_SVG 4
#define GLYPH_INK 8

static hb_font_t *as_font(jlong handle) {
    return (hb_font_t *) (intptr_t) handle;
}

static int has_ink(hb_font_t *font, hb_codepoint_t glyph, hb_glyph_extents_t *extents) {
    return hb_font_get_glyph_extents(font, glyph, extents) && extents->width != 0 && extents->height != 0;
}

JNIEXPORT jstring JNICALL Java_industries_angelic_minemoticon_harfbuzz_HarfBuzz_version(JNIEnv *env, jclass cls) {
    return (*env)->NewStringUTF(env, hb_version_string());
}

JNIEXPORT jlong JNICALL Java_industries_angelic_minemoticon_harfbuzz_HarfBuzz_createFont(JNIEnv *env, jclass cls,
                                                                                         jbyteArray data) {
    jsize length = (*env)->GetArrayLength(env, data);
    if (length <= 0) return 0;

    char *copy = malloc((size_t) length);
    if (!copy) return 0;
    (*env)->GetByteArrayRegion(env, data, 0, length, (jbyte *) copy);

    hb_blob_t *blob = hb_blob_create_or_fail(copy, (unsigned int) length, HB_MEMORY_MODE_WRITABLE, copy, free);
    if (!blob) {
        free(copy);
        return 0;
    }
    hb_face_t *face = hb_face_create(blob, 0);
    hb_blob_destroy(blob);
    if (hb_face_get_glyph_count(face) == 0) {
        hb_face_destroy(face);
        return 0;
    }

    hb_font_t *font = hb_font_create(face);
    hb_face_destroy(face);
    hb_font_make_immutable(font);
    return (jlong) (intptr_t) font;
}

JNIEXPORT jlong JNICALL Java_industries_angelic_minemoticon_harfbuzz_HarfBuzz_createVariationFont(
    JNIEnv *env, jclass cls, jlong handle, jintArray axisTags, jfloatArray axisValues) {
    jsize count = (*env)->GetArrayLength(env, axisTags);
    if (count != (*env)->GetArrayLength(env, axisValues)) return 0;

    hb_variation_t *variations = malloc(sizeof(hb_variation_t) * (size_t) (count > 0 ? count : 1));
    if (!variations) return 0;
    jint *tags = (*env)->GetIntArrayElements(env, axisTags, NULL);
    jfloat *values = (*env)->GetFloatArrayElements(env, axisValues, NULL);
    for (jsize i = 0; i < count; i++) {
        variations[i].tag = (hb_tag_t) tags[i];
        variations[i].value = values[i];
    }
    (*env)->ReleaseIntArrayElements(env, axisTags, tags, JNI_ABORT);
    (*env)->ReleaseFloatArrayElements(env, axisValues, values, JNI_ABORT);

    // A sibling on the same face rather than a sub font, whose funcs would read the parent's coordinates
    hb_font_t *font = hb_font_create(hb_font_get_face(as_font(handle)));
    hb_font_set_variations(font, variations, (unsigned int) count);
    hb_font_make_immutable(font);
    free(variations);
    return (jlong) (intptr_t) font;
}

JNIEXPORT void JNICALL Java_industries_angelic_minemoticon_harfbuzz_HarfBuzz_destroyFont(JNIEnv *env, jclass cls,
                                                                                         jlong handle) {
    hb_font_destroy(as_font(handle));
}

JNIEXPORT jintArray JNICALL Java_industries_angelic_minemoticon_harfbuzz_HarfBuzz_fontMetrics(JNIEnv *env,
                                                                                              jclass cls,
                                                                                              jlong handle) {
    hb_font_t *font = as_font(handle);
    hb_font_extents_t extents;
    memset(&extents, 0, sizeof extents);
    hb_font_get_h_extents(font, &extents);

    jint metrics[3] = {(jint) hb_face_get_upem(hb_font_get_face(font)), (jint) extents.ascender,
                       (jint) extents.descender};
    jintArray result = (*env)->NewIntArray(env, 3);
    if (result) (*env)->SetIntArrayRegion(env, result, 0, 3, metrics);
    return result;
}

JNIEXPORT jint JNICALL Java_industries_angelic_minemoticon_harfbuzz_HarfBuzz_nominalGlyph(JNIEnv *env, jclass cls,
                                                                                          jlong handle,
                                                                                          jint codepoint) {
    hb_codepoint_t glyph = 0;
    return hb_font_get_nominal_glyph(as_font(handle), (hb_codepoint_t) codepoint, &glyph) ? (jint) glyph : 0;
}

JNIEXPORT jint JNICALL Java_industries_angelic_minemoticon_harfbuzz_HarfBuzz_glyphFlags(JNIEnv *env, jclass cls,
                                                                                        jlong handle, jint glyph) {
    hb_font_t *font = as_font(handle);
    hb_face_t *face = hb_font_get_face(font);
    jint flags = 0;

    if (hb_ot_color_glyph_has_paint(face, (hb_codepoint_t) glyph)) flags |= GLYPH_COLOR_PAINT;

    hb_blob_t *png = hb_ot_color_glyph_reference_png(font, (hb_codepoint_t) glyph);
    if (hb_blob_get_length(png) > 0) flags |= GLYPH_PNG;
    hb_blob_destroy(png);

    hb_blob_t *svg = hb_ot_color_glyph_reference_svg(face, (hb_codepoint_t) glyph);
    if (hb_blob_get_length(svg) > 0) flags |= GLYPH_SVG;
    hb_blob_destroy(svg);

    hb_glyph_extents_t extents;
    if (has_ink(font, (hb_codepoint_t) glyph, &extents)) flags |= GLYPH_INK;
    return flags;
}

JNIEXPORT jintArray JNICALL Java_industries_angelic_minemoticon_harfbuzz_HarfBuzz_glyphExtents(JNIEnv *env,
                                                                                               jclass cls,
                                                                                               jlong handle,
                                                                                               jint glyph) {
    hb_glyph_extents_t extents;
    if (!has_ink(as_font(handle), (hb_codepoint_t) glyph, &extents)) return NULL;

    jint values[4] = {extents.x_bearing, extents.y_bearing, extents.width, extents.height};
    jintArray result = (*env)->NewIntArray(env, 4);
    if (result) (*env)->SetIntArrayRegion(env, result, 0, 4, values);
    return result;
}

JNIEXPORT jint JNICALL Java_industries_angelic_minemoticon_harfbuzz_HarfBuzz_glyphAdvance(JNIEnv *env, jclass cls,
                                                                                          jlong handle, jint glyph) {
    return (jint) hb_font_get_glyph_h_advance(as_font(handle), (hb_codepoint_t) glyph);
}

JNIEXPORT jintArray JNICALL Java_industries_angelic_minemoticon_harfbuzz_HarfBuzz_shape(JNIEnv *env, jclass cls,
                                                                                        jlong handle,
                                                                                        jintArray codepoints) {
    jsize length = (*env)->GetArrayLength(env, codepoints);
    hb_buffer_t *buffer = hb_buffer_create();
    jint *chars = (*env)->GetIntArrayElements(env, codepoints, NULL);
    hb_buffer_add_codepoints(buffer, (const hb_codepoint_t *) chars, length, 0, length);
    (*env)->ReleaseIntArrayElements(env, codepoints, chars, JNI_ABORT);

    hb_buffer_set_flags(buffer, HB_BUFFER_FLAG_REMOVE_DEFAULT_IGNORABLES);
    hb_buffer_guess_segment_properties(buffer);
    hb_shape(as_font(handle), buffer, NULL, 0);

    unsigned int count = 0;
    hb_glyph_info_t *infos = hb_buffer_get_glyph_infos(buffer, &count);
    hb_glyph_position_t *positions = hb_buffer_get_glyph_positions(buffer, NULL);

    jintArray result = (*env)->NewIntArray(env, (jsize) (count * 5));
    jint *out = result ? malloc(sizeof(jint) * (size_t) (count * 5 > 0 ? count * 5 : 1)) : NULL;
    if (out) {
        for (unsigned int i = 0; i < count; i++) {
            out[i * 5] = (jint) infos[i].codepoint;
            out[i * 5 + 1] = (jint) infos[i].cluster;
            out[i * 5 + 2] = positions[i].x_advance;
            out[i * 5 + 3] = positions[i].x_offset;
            out[i * 5 + 4] = positions[i].y_offset;
        }
        (*env)->SetIntArrayRegion(env, result, 0, (jsize) (count * 5), out);
        free(out);
    }
    hb_buffer_destroy(buffer);
    return result;
}

JNIEXPORT jintArray JNICALL Java_industries_angelic_minemoticon_harfbuzz_HarfBuzz_paintGlyph(
    JNIEnv *env, jclass cls, jlong handle, jint glyph, jfloat scale, jint foregroundArgb) {
    hb_font_t *font = as_font(handle);
    hb_glyph_extents_t glyph_extents;
    if (!has_ink(font, (hb_codepoint_t) glyph, &glyph_extents)) return NULL;

    hb_raster_paint_t *paint = hb_raster_paint_create_or_fail();
    if (!paint) return NULL;

    uint32_t argb = (uint32_t) foregroundArgb;
    hb_raster_paint_set_transform(paint, scale, 0.0f, 0.0f, scale, 0.0f, 0.0f);
    hb_raster_paint_set_foreground(paint, HB_COLOR(argb & 0xFF, (argb >> 8) & 0xFF, (argb >> 16) & 0xFF, argb >> 24));

    jintArray result = NULL;
    if (hb_raster_paint_set_glyph_extents(paint, &glyph_extents)) {
        // Unlike the _or_fail variant, this fills glyphs without color data with the foreground
        hb_raster_paint_glyph(paint, font, (hb_codepoint_t) glyph);
        hb_raster_image_t *image = hb_raster_paint_render(paint);
        if (image) {
            hb_raster_extents_t extents;
            hb_raster_image_get_extents(image, &extents);
            const uint8_t *pixels = hb_raster_image_get_buffer(image);
            jsize width = (jsize) extents.width;
            jsize height = (jsize) extents.height;
            if (pixels && width > 0 && height > 0) {
                result = (*env)->NewIntArray(env, 4 + width * height);
                if (result) {
                    jint header[4] = {width, height, extents.x_origin, extents.y_origin + height};
                    (*env)->SetIntArrayRegion(env, result, 0, 4, header);
                    // Rows are stored bottom-up; BGRA bytes read as a little-endian uint32 are ARGB
                    for (jsize row = 0; row < height; row++) {
                        const uint8_t *source = pixels + (size_t) (height - 1 - row) * extents.stride;
                        (*env)->SetIntArrayRegion(env, result, 4 + row * width, width, (const jint *) source);
                    }
                }
            }
            hb_raster_image_destroy(image);
        }
    }
    hb_raster_paint_destroy(paint);
    return result;
}
