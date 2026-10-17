// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright 2026 the ScarletNX contributors
package org.scarletnx;

import java.io.*;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Bounded extraction shared by the Android importer and host-side tests. */
final class DriverPackage {
    private static final long MAX_BYTES = 128L * 1024 * 1024;
    private DriverPackage() {}

    static void extract(InputStream input, File directory) throws IOException {
        String root = directory.getCanonicalPath() + File.separator;
        HashSet<String> seen = new HashSet<>();
        long total = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            byte[] buffer = new byte[16384];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 128) throw new IOException("Too many files in driver ZIP");
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("\\") || name.indexOf('\0') >= 0)
                    throw new IOException("Invalid path in driver ZIP");
                File file = new File(directory, name);
                String path = file.getCanonicalPath();
                if (!path.startsWith(root) || !seen.add(path))
                    throw new IOException("Invalid or duplicate path in driver ZIP");
                if (entry.isDirectory()) {
                    if (!file.isDirectory() && !file.mkdirs()) throw new IOException("Cannot create driver folder");
                    continue;
                }
                File parent = file.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create driver folder");
                try (OutputStream out = new FileOutputStream(file)) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_BYTES) throw new IOException("Driver ZIP expands beyond 128 MiB");
                        out.write(buffer, 0, count);
                    }
                }
            }
        }
        if (entries == 0) throw new IOException("Empty or invalid driver ZIP");
    }

    static void validateLibrary(File directory, String name) throws IOException {
        if (!name.matches("[A-Za-z0-9_.+-]+\\.so") || name.contains(".."))
            throw new IOException("libraryName must be a .so filename without a path");
        byte[] header = new byte[64];
        try (DataInputStream in = new DataInputStream(new FileInputStream(new File(directory, name)))) {
            in.readFully(header);
        }
        // ELF64, little endian, ET_DYN, EM_AARCH64. The Android dynamic linker checks the remaining ABI.
        if (header[0] != 0x7f || header[1] != 'E' || header[2] != 'L' || header[3] != 'F' ||
            header[4] != 2 || header[5] != 1 || header[16] != 3 || header[17] != 0 ||
            (header[18] & 255) != 183 || header[19] != 0)
            throw new IOException("Driver library is not an arm64 Android shared library");
    }

    static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
}
