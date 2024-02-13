/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

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
        LINUX_x86_64_GLIBC,
        LINUX_x86_64_MUSL,
        LINUX_AARCH64_GLIBC,
        LINUX_AARCH64_MUSL,
        MAC_OS_x86_64,
        MAC_OS_AARCH64,
        WINDOWS_64
    }

    private static OsType getOsType() throws UnsupportedVMException {

        String arch = System.getProperty("os.arch");
        if (!"amd64".equals(arch) && !"x86_64".equals(arch) && !"aarch64".equals(arch)) {
            throw new UnsupportedVMException("Unsupported architecture: " + arch);
        }

        boolean aarch64 = "aarch64".equals(arch);

        String os = System.getProperty("os.name");
        if ("Linux".equals(os)) {
            File file = new File(LINUX_JVM_PROC_MAP);
            boolean isMusl = false;
            try (Scanner sc = new Scanner(file, "ISO-8859-1")) {
                while (sc.hasNextLine()) {
                    String module = sc.nextLine();
                    // in recent versions of Alpine, the name of the C library
                    // is /lib/ld-musl-<ARCH>.so.1. /lib/libc.musl-<ARCH>.so.1
                    // symlinks there
                    if (module.contains("libc.musl-") || module.contains("ld-musl-")) {
                        isMusl = true;
                        break;
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Unable to read jvm maps; assuming glibc", e);
            }

            if (isMusl) {
                LOGGER.debug("Musl detected");
                if (aarch64) {
                    return OsType.LINUX_AARCH64_MUSL;
                } else {
                    return OsType.LINUX_x86_64_MUSL;
                }
            } else {
                LOGGER.debug("Musl was not detected; assuming glibc");
                if (aarch64) {
                    return NativeLibLoader.OsType.LINUX_AARCH64_GLIBC;
                } else {
                    return NativeLibLoader.OsType.LINUX_x86_64_GLIBC;
                }
            }
        } else if ("Mac OS X".equals(os)) {
            if (aarch64) {
                return OsType.MAC_OS_AARCH64;
            } else {
                return OsType.MAC_OS_x86_64;
            }
        } else if (os != null && os.toLowerCase(Locale.ENGLISH).contains("windows")) {
            return OsType.WINDOWS_64;
        }
        throw new UnsupportedVMException("Unsupported OS: " + os);
    }

    private static List<String> getNativeLibs(OsType type) {
        switch(type) {
            case LINUX_x86_64_GLIBC:
                return Arrays.asList("linux/x86_64/glibc/libsqreen_jni.so",
                        "linux/x86_64/libddwaf.so");
            case LINUX_x86_64_MUSL:
                return Arrays.asList("linux/x86_64/musl/libsqreen_jni.so",
                        "linux/x86_64/libddwaf.so");
            case LINUX_AARCH64_GLIBC:
                return Arrays.asList("linux/aarch64/glibc/libsqreen_jni.so",
                        "linux/aarch64/libddwaf.so");
            case LINUX_AARCH64_MUSL:
                return Arrays.asList("linux/aarch64/musl/libsqreen_jni.so",
                        "linux/aarch64/libddwaf.so");
            case MAC_OS_x86_64:
                return Arrays.asList("macos/x86_64/libsqreen_jni.dylib",
                        "macos/x86_64/libddwaf.dylib");
            case MAC_OS_AARCH64:
                return Arrays.asList("macos/aarch64/libsqreen_jni.dylib",
                        "macos/aarch64/libddwaf.dylib");
            case WINDOWS_64:
                return Collections.singletonList("windows/x86_64/sqreen_jni.dll");
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
