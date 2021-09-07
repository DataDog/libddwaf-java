package io.sqreen.powerwaf;

import io.sqreen.powerwaf.exception.UnsupportedVMException;
import io.sqreen.powerwaf.util.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class NativeLibLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(NativeLibLoader.class);
    private static final String LINUX_JVM_PROC_MAP = "/proc/self/maps";

    public static void load() throws IOException, UnsupportedVMException {
        LOGGER.info("Will load native library");
        File jniLib = extractLib();

        System.load(jniLib.getAbsolutePath());
    }

    private static File extractLib() throws UnsupportedVMException, IOException {
        ClassLoader cl = NativeLibLoader.class.getClassLoader();
        List<String> nativeLibs = getNativeLibs(getOsType());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Native libs to copy: {}", Joiner.on(", ").join(nativeLibs));
        }

        Path tempDir = Files.createTempDirectory("pwaf");
        LOGGER.debug("Created temporary directory {}", tempDir);

        File jniLib = null;
        for (String p: nativeLibs) {
            String clPath = "native_libs/" + p;
            InputStream input = cl.getResourceAsStream(clPath);
            if (input == null) {
                throw new UnsupportedVMException("Not found: " + clPath);
            }

            File dest = new File(tempDir.toFile(), new File(clPath).getName());
            if (dest.getName().contains("_jni")) {
                jniLib = dest;
            }
            LOGGER.debug("Copying resource {} to {}", clPath, dest);

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
                sc = new Scanner(file, "ISO-8859-1");
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
                return Arrays.asList("linux_64_glibc/libsqreen_jni.so",
                        "linux_64/libddwaf.so");
            case LINUX_64_MUSL:
                return Arrays.asList("linux_64_musl/libsqreen_jni.so",
                        "linux_64/libddwaf.so");
            case MAC_OS_64:
                return Arrays.asList("osx_64/libsqreen_jni.dylib",
                        "osx_64/libddwaf.dylib");
            case SUN_OS_64:
                return Arrays.asList("solaris_64/libsqreen_jni.so",
                        "solaris_64/libddwaf.so");
            case WINDOWS_64:
                return Collections.singletonList("windows_64/sqreen_jni.dll");
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

    private static long copy(InputStream from, OutputStream to) throws IOException {
        byte[] buf = new byte[8192];
        long total = 0;
        while (true) {
            int r = from.read(buf);
            if (r == -1) {
                break;
            }
            to.write(buf, 0, r);
            total += r;
        }
        return total;
    }
}
