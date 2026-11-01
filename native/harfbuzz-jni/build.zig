const std = @import("std");

const harfbuzz_sources = [_][]const u8{
    "src/harfbuzz.cc",
    "src/hb-raster-draw.cc",
    "src/hb-raster-image.cc",
    "src/hb-raster-paint.cc",
};

const libpng_sources = [_][]const u8{
    "png.c",
    "pngerror.c",
    "pngget.c",
    "pngmem.c",
    "pngpread.c",
    "pngread.c",
    "pngrio.c",
    "pngrtran.c",
    "pngrutil.c",
    "pngset.c",
    "pngtrans.c",
    "pngwio.c",
    "pngwrite.c",
    "pngwtran.c",
    "pngwutil.c",
};

const zlib_sources = [_][]const u8{
    "adler32.c",
    "compress.c",
    "crc32.c",
    "deflate.c",
    "infback.c",
    "inffast.c",
    "inflate.c",
    "inftrees.c",
    "trees.c",
    "uncompr.c",
    "zutil.c",
};

// Only the JNI entry points are exported, so these copies never clash with another
// HarfBuzz, libpng or zlib in the same JVM (LWJGL's FreeType build exports HarfBuzz symbols).
const hidden = "-fvisibility=hidden";

fn requiredOption(b: *std.Build, name: []const u8, description: []const u8) []const u8 {
    return b.option([]const u8, name, description) orelse @panic("missing required build option");
}

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});
    const harfbuzz_src = requiredOption(b, "harfbuzz-src", "Path to the HarfBuzz source root");
    const libpng_src = requiredOption(b, "libpng-src", "Path to the libpng source root");
    const zlib_src = requiredOption(b, "zlib-src", "Path to the zlib source root");
    const java_include = requiredOption(b, "java-include", "Path to JAVA_HOME/include");
    const jni_md_include = requiredOption(b, "jni-md-include", "Path to the target-specific jni_md.h directory");
    const generated_headers = requiredOption(b, "generated-headers", "Path to the javac -h output directory");

    const zlib = b.addStaticLibrary(.{
        .name = "z",
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });
    zlib.addCSourceFiles(.{
        .root = .{ .cwd_relative = zlib_src },
        .files = &zlib_sources,
        .flags = &.{hidden},
    });

    const png_config = b.addWriteFiles();
    _ = png_config.addCopyFile(
        .{ .cwd_relative = b.pathJoin(&.{ libpng_src, "scripts", "pnglibconf.h.prebuilt" }) },
        "pnglibconf.h",
    );

    const libpng = b.addStaticLibrary(.{
        .name = "png",
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });
    libpng.addIncludePath(png_config.getDirectory());
    libpng.addIncludePath(.{ .cwd_relative = zlib_src });
    libpng.addCSourceFiles(.{
        .root = .{ .cwd_relative = libpng_src },
        .files = &libpng_sources,
        // Portable C only: the SIMD paths need extra per-architecture sources
        .flags = &.{
            hidden,
            "-DPNG_ARM_NEON_OPT=0",
            "-DPNG_INTEL_SSE_OPT=0",
            "-DPNG_POWERPC_VSX_OPT=0",
            "-DPNG_MIPS_MSA_OPT=0",
            "-DPNG_MIPS_MMI_OPT=0",
            "-DPNG_LOONGARCH_LSX_OPT=0",
            "-DPNG_RISCV_RVV_OPT=0",
        },
    });

    const harfbuzz = b.addStaticLibrary(.{
        .name = "harfbuzz",
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });
    harfbuzz.linkLibCpp();
    harfbuzz.addIncludePath(.{ .cwd_relative = b.pathJoin(&.{ harfbuzz_src, "src" }) });
    harfbuzz.addIncludePath(png_config.getDirectory());
    harfbuzz.addIncludePath(.{ .cwd_relative = libpng_src });
    harfbuzz.addIncludePath(.{ .cwd_relative = zlib_src });
    harfbuzz.addCSourceFiles(.{
        .root = .{ .cwd_relative = harfbuzz_src },
        .files = &harfbuzz_sources,
        .flags = &.{
            "-std=c++11",
            "-fno-exceptions",
            "-fno-rtti",
            "-fno-threadsafe-statics",
            hidden,
            "-DHAVE_PNG",
        },
    });

    const jni = b.addSharedLibrary(.{
        .name = "harfbuzz-jni",
        .target = target,
        .optimize = optimize,
        .link_libc = true,
        .strip = true,
    });
    jni.linkLibCpp();
    jni.addIncludePath(.{ .cwd_relative = generated_headers });
    jni.addIncludePath(.{ .cwd_relative = java_include });
    jni.addIncludePath(.{ .cwd_relative = jni_md_include });
    jni.addIncludePath(.{ .cwd_relative = b.pathJoin(&.{ harfbuzz_src, "src" }) });
    jni.addCSourceFiles(.{
        .files = &.{"jni/harfbuzz_jni.c"},
        .flags = &.{ "-std=c99", hidden },
    });
    jni.linkLibrary(harfbuzz);
    jni.linkLibrary(libpng);
    jni.linkLibrary(zlib);

    b.installArtifact(jni);
}
