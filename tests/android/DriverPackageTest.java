// SPDX-License-Identifier: GPL-3.0-or-later
package org.scarletnx;

import java.io.*;
import java.nio.file.Files;
import java.util.zip.*;

public class DriverPackageTest {
    private static byte[] zip(String name, byte[] content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(name)); zip.write(content); zip.closeEntry();
        }
        return out.toByteArray();
    }
    private static void rejected(byte[] zip, File folder) throws Exception {
        try { DriverPackage.extract(new ByteArrayInputStream(zip), folder); }
        catch (IOException expected) { return; }
        throw new AssertionError("Unsafe ZIP accepted");
    }
    public static void main(String[] args) throws Exception {
        File folder = Files.createTempDirectory("snx-driver-test").toFile();
        try {
            byte[] elf = new byte[64];
            elf[0] = 0x7f; elf[1] = 'E'; elf[2] = 'L'; elf[3] = 'F';
            elf[4] = 2; elf[5] = 1; elf[16] = 3; elf[18] = (byte)183;
            DriverPackage.extract(new ByteArrayInputStream(zip("libvulkan_freedreno.so", elf)), folder);
            DriverPackage.validateLibrary(folder, "libvulkan_freedreno.so");
            for (String path : new String[] {"../escape", "/escape", "sub/../../escape", "sub\\escape"})
                rejected(zip(path, new byte[1]), folder);
            rejected(new byte[0], folder);
            try { DriverPackage.validateLibrary(folder, "../libvulkan_freedreno.so");
                throw new AssertionError("Unsafe libraryName accepted"); } catch (IOException expected) {}
            elf[18] = 62; // x86_64 must not be accepted as an arm64 driver
            Files.write(new File(folder, "wrong.so").toPath(), elf);
            try { DriverPackage.validateLibrary(folder, "wrong.so");
                throw new AssertionError("Wrong ELF architecture accepted"); } catch (IOException expected) {}
            ByteArrayOutputStream many = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(many)) {
                for (int i = 0; i < 129; ++i) { zip.putNextEntry(new ZipEntry("file" + i)); zip.closeEntry(); }
            }
            rejected(many.toByteArray(), folder);
            // Compressible input must be bounded by expanded bytes, not the ZIP's reported sizes.
            rejected(zip("large", new byte[129 * 1024 * 1024]), folder);
            System.out.println("DriverPackage: valid arm64 driver and 9 rejection cases passed");
        } finally { DriverPackage.delete(folder); }
    }
}
