package io.sqreen.powerwaf;

import io.sqreen.logging.Logger;
import io.sqreen.logging.LoggerFactory;
import io.sqreen.powerwaf.exception.UnsupportedVMException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.google.common.io.ByteStreams.copy;
import static com.google.common.io.Files.createTempDir;

public class NativeLibLoader {

    private static final Logger LOGGER = LoggerFactory.get(NativeLibLoader.class);
    private static final String LINUX_JVM_PROC_MAP = "/proc/self/maps";

    public static void load() throws IOException, UnsupportedVMException {
        LOGGER.info("Will load native library");
        File jniLib = extractLib();

        System.load(jniLib.getAbsolutePath());
    }

    private static File extractLib() throws UnsupportedVMException, IOException {
        ClassLoader cl = NativeLibLoader.class.getClassLoader();
        List<String> nativeLibs = getNativeLibs(getOsType());
        LOGGER.debug("Native libs to copy: %s",  String.join(", ", nativeLibs));

        File tempDir = createTempDir();
        LOGGER.debug("Created temporary directory %s", tempDir);

        File jniLib = null;
        for (String p: nativeLibs) {
            String clPath = "native_libs/" + p;
            InputStream input = cl.getResourceAsStream(clPath);
            if (input == null) {
                throw new UnsupportedVMException("Not found: " + clPath);
            }

            File dest = new File(tempDir, new File(clPath).getName());
            if (dest.getName().contains("_jni")) {
                jniLib = dest;
            }
            LOGGER.debug("Copying resource %s to %s", clPath, dest);

            try {
                copyToFile(input, dest);
            } finally {
                input.close();
            }
            dest.deleteOnExit();
        }

        if (jniLib == null) {
            // should not happen
            throw new RuntimeException("Could not find jni lib");
        }
        return jniLib;
    }

    private enum OsType {
        LINUX_64_GLIBC,
        LINUX_64_MUSL,
        MAC_OS_64,
        SUN_OS_64,
        WINDOWS_64
    }

    private static OsType getOsType() throws UnsupportedVMException {

        String arch = System.getProperty("os.arch");
        if (!"amd64".equals(arch) && !"x86_64".equals(arch)) {
            throw new UnsupportedVMException("Unsupported architecture: " + arch);
        }

        String os = System.getProperty("os.name");
        if ("Linux".equals(os)) {
            File file = new File(LINUX_JVM_PROC_MAP);
            Scanner sc = null;
            try {
                sc = new Scanner(file, StandardCharsets.ISO_8859_1.name());
                while (sc.hasNextLine()){
                    String module = sc.nextLine();
                    if (module.contains("libc.musl-") || module.contains("ld-musl-")) {
                        return NativeLibLoader.OsType.LINUX_64_MUSL;
                    } else if (module.contains("-linux-gnu") || module.contains("libc-")) {
                        return NativeLibLoader.OsType.LINUX_64_GLIBC;
                    }
                }
            }
            catch (IOException e) {
                LOGGER.debug("Unable to read jvm maps", e);
            } finally {
                if (sc != null) {
                    sc.close();
                }
            }
        } else if ("Mac OS X".equals(os)) {
            return OsType.MAC_OS_64;
        } else if ("SunOS".equals(os)) {
            return OsType.SUN_OS_64;
        } else if (os != null && os.toLowerCase(Locale.ENGLISH).contains("windows")) {
            return OsType.WINDOWS_64;
        }
        throw new UnsupportedVMException("Unsupported OS: " + os);
    }

    private static List<String> getNativeLibs(OsType type) {
        switch(type) {
            case LINUX_64_GLIBC:
                return Arrays.asList("linux_64_glibc/libpowerwaf_jni.so",
                                     "linux_64_glibc/libSqreen.so");
            case LINUX_64_MUSL:
                return Arrays.asList("linux_64_musl/libpowerwaf_jni.so",
                                     "linux_64_musl/libSqreen.so");
            case MAC_OS_64:
                return Arrays.asList("osx_64/libpowerwaf_jni.dylib",
                                     "osx_64/libSqreen.dylib");
            case SUN_OS_64:
                return Arrays.asList("solaris_64/libpowerwaf_jni.so",
                                     "solaris_64/libSqreen.so");
            case WINDOWS_64:
                return Collections.singletonList("windows_64/powerwaf_jni.dll");
            default:
                return Collections.emptyList();
        }
    }

    private static void copyToFile(InputStream input, File dest) throws IOException {
        OutputStream os = null;
        try {
            os = new FileOutputStream(dest);
            copy(input, os);
            os.flush();
        } finally {
            if (os != null) {
                os.close();
            }
        }
    }
}
