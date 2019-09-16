package io.sqreen.powerwaf;

import com.google.common.base.Joiner;
import io.sqreen.logging.Logger;
import io.sqreen.logging.LoggerFactory;
import io.sqreen.powerwaf.exception.UnsupportedVMException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.google.common.io.ByteStreams.copy;
import static com.google.common.io.Files.createTempDir;

public class NativeLibLoader {

    private final static Logger LOGGER = LoggerFactory.get(NativeLibLoader.class);

    public static void load() throws IOException, UnsupportedVMException {
        LOGGER.info("Will load native library");
        File jniLib = extractLib();

        System.load(jniLib.getAbsolutePath());
    }

    private static File extractLib() throws UnsupportedVMException, IOException {
        ClassLoader cl = NativeLibLoader.class.getClassLoader();
        List<String> nativeLibs = getNativeLibs();
        LOGGER.debug("Native libs to copy: %s", Joiner.on(", ").join(nativeLibs));

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

    private static List<String> getNativeLibs() throws UnsupportedVMException {
        String os = System.getProperty("os.name");

        String osPart;
        String jniLibName;
        String powerwafName;
        if ("Linux".equals(os)) {
            osPart = "linux";
            jniLibName = "libpowerwaf_jni.so";
            powerwafName = "libSqreen.so";
        } else if ("Mac OS X".equals(os)) {
            osPart = "osx";
            jniLibName = "libpowerwaf_jni.dylib";
            powerwafName = "libSqreen.dylib";
        } else if ("SunOS".equals(os)) {
            osPart = "solaris";
            jniLibName = "libpowerwaf_jni.so";
            powerwafName = "libSqreen.so";
        } else if (os != null && os.toLowerCase(Locale.ENGLISH).contains("windows")) {
            osPart = "windows";
            jniLibName = "powerwaf_jni.dll";
            powerwafName = null;
        } else {
            throw new UnsupportedVMException("Unsupported OS: " + os);
        }

        String arch = System.getProperty("os.arch");
        if (!"amd64".equals(arch) && !"x86_64".equals(arch)) {
            throw new UnsupportedVMException("Unsupported architecture: " + arch);
        }

        String parent = osPart + "_64";

        List<String> ret = new ArrayList<String>();
        ret.add(parent + "/" + jniLibName);
        if (powerwafName != null) {
            ret.add(parent + "/" + powerwafName);
        }
        return ret;
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
