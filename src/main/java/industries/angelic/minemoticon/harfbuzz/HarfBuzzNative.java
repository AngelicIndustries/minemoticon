package industries.angelic.minemoticon.harfbuzz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import industries.angelic.minemoticon.Minemoticon;
import industries.angelic.minemoticon.colorfont.AtlasBuilder;

// Extracts and loads the bundled HarfBuzz JNI library once per process.
public final class HarfBuzzNative {

    private static Boolean loaded;

    private HarfBuzzNative() {}

    public static synchronized boolean load() {
        if (loaded == null) {
            loaded = tryLoad();
        }
        return loaded;
    }

    private static boolean tryLoad() {
        String os = System.getProperty("os.name", "")
            .toLowerCase();
        String arch = System.getProperty("os.arch", "")
            .toLowerCase();

        String normalizedArch = normalizeArch(arch);
        if (normalizedArch == null) {
            Minemoticon.LOG.warn("[HarfBuzz] Unsupported architecture: {}", arch);
            return false;
        }

        String platform = detectPlatform(os, normalizedArch);
        if (platform == null) {
            Minemoticon.LOG.warn("[HarfBuzz] Unsupported operating system: {}", os);
            return false;
        }

        String libName = platform.startsWith("windows-") ? "harfbuzz-jni.dll"
            : platform.startsWith("macos-") ? "libharfbuzz-jni.dylib" : "libharfbuzz-jni.so";
        String resourcePath = "/natives/" + platform + "/" + libName;

        try (InputStream in = HarfBuzzNative.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                Minemoticon.LOG.warn("[HarfBuzz] Native lib not found in resources: {}", resourcePath);
                return false;
            }

            var bytes = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bytes.write(buf, 0, n);

            File tmpDir = new File(System.getProperty("java.io.tmpdir"), "minemoticon-natives");
            tmpDir.mkdirs();
            File libFile = extractNative(tmpDir, libName, bytes.toByteArray());

            System.load(libFile.getAbsolutePath());
            Minemoticon.LOG.info("[HarfBuzz] Loaded HarfBuzz {} ({})", HarfBuzz.version(), platform);
            return true;
        } catch (Throwable e) {
            Minemoticon.LOG.warn("[HarfBuzz] Failed to load native, using the Java font parsers: {}", e.toString());
            return false;
        }
    }

    /**
     * Never rewrites the native in place: truncating a library that another running game has mapped
     * drops that process's relocated GOT, and its next native call jumps to an unrelocated PLT
     * address. Files are named by content hash and only ever appear via atomic rename.
     */
    private static File extractNative(File dir, String libName, byte[] bytes) throws IOException {
        int dot = libName.lastIndexOf('.');
        String hash = AtlasBuilder.sha1(bytes)
            .substring(0, 16);
        File target = new File(dir, libName.substring(0, dot) + "-" + hash + libName.substring(dot));
        if (hasContent(target, bytes)) return target;

        Path tmp = Files.createTempFile(dir.toPath(), libName, ".tmp");
        try {
            Files.write(tmp, bytes);
            Files.move(tmp, target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Windows refuses to replace a DLL another instance already extracted and loaded
            if (!hasContent(target, bytes)) throw e;
        } finally {
            Files.deleteIfExists(tmp);
        }
        return target;
    }

    private static boolean hasContent(File file, byte[] bytes) {
        try {
            return file.length() == bytes.length && Arrays.equals(Files.readAllBytes(file.toPath()), bytes);
        } catch (IOException e) {
            return false;
        }
    }

    private static String detectPlatform(String os, String arch) {
        if (os.contains("mac")) {
            return "macos-" + arch;
        }
        if (os.contains("win")) {
            return "windows-" + arch;
        }
        if (os.contains("linux")) {
            return "linux-" + arch;
        }
        return null;
    }

    private static String normalizeArch(String arch) {
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "arm64";
        }
        if (arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64")) {
            return "x64";
        }
        return null;
    }
}
