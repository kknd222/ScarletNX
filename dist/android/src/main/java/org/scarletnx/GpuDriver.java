// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright 2026 the ScarletNX contributors
package org.scarletnx;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.AtomicFile;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONObject;

/** One immutable installation per import. A running game can keep using its previous driver. */
final class GpuDriver {
    static final int PICK_DRIVER = 0x10003;
    private static AtomicFile selection(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "gpu-driver.json"));
    }

    private static boolean supported() {
        return Build.VERSION.SDK_INT >= 28 && android.os.Process.is64Bit() &&
               Build.SUPPORTED_ABIS.length > 0 && Build.SUPPORTED_ABIS[0].equals("arm64-v8a");
    }

    static void show(Activity activity) {
        activity.runOnUiThread(() -> {
            String selected = "System driver";
            try { selected = readSelection(activity).getString("name"); } catch (Exception ignored) {}
            AlertDialog.Builder dialog = new AlertDialog.Builder(activity).setTitle("GPU driver")
                .setMessage("Selected: " + selected + "\nChanges apply to the next Switch game. " +
                            "Custom drivers need Android 9+, arm64 and a compatible Adreno GPU.")
                .setNegativeButton("Close", null)
                .setNeutralButton("Use system driver", (d, which) -> selection(activity).delete());
            if (supported()) dialog.setPositiveButton("Import driver ZIP", (d, which) -> {
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                    .addCategory(Intent.CATEGORY_OPENABLE);
                activity.startActivityForResult(pick, PICK_DRIVER);
            });
            dialog.show();
        });
    }

    static void install(Activity activity, Uri uri) {
        new Thread(() -> {
            File folder = new File(new File(activity.getFilesDir(), "gpu_drivers"), UUID.randomUUID().toString());
            String message;
            boolean committed = false;
            try {
                if (!supported()) throw new IOException("Custom GPU drivers are not supported on this device");
                if (!folder.mkdirs()) throw new IOException("Cannot create driver directory");
                InputStream input = activity.getContentResolver().openInputStream(uri);
                if (input == null) throw new IOException("Cannot open driver ZIP");
                DriverPackage.extract(input, folder);
                File metadata = new File(folder, "meta.json");
                JSONObject meta = readJson(metadata);
                String name = meta.getString("name");
                String library = meta.getString("libraryName");
                if (meta.getInt("minApi") > Build.VERSION.SDK_INT)
                    throw new IOException("This driver requires a newer Android version");
                DriverPackage.validateLibrary(folder, library);
                JSONObject config = new JSONObject().put("name", name).put("libraryName", library)
                    .put("directory", folder.getName());
                AtomicFile file = selection(activity);
                FileOutputStream out = null;
                try {
                    out = file.startWrite();
                    out.write(config.toString().getBytes(StandardCharsets.UTF_8));
                    file.finishWrite(out);
                } catch (Exception error) {
                    if (out != null) file.failWrite(out);
                    throw error;
                }
                committed = true;
                message = "Selected " + name + ". It will be used by the next Switch game. " +
                          "GPU compatibility is checked when the game starts.";
            } catch (Exception error) {
                message = "Driver import failed: " + error.getMessage();
            } finally {
                if (!committed) DriverPackage.delete(folder);
            }
            final String result = message;
            activity.runOnUiThread(() -> new AlertDialog.Builder(activity).setTitle("GPU driver")
                .setMessage(result).setPositiveButton("OK", null).show());
        }, "GPU driver import").start();
    }

    private static JSONObject readJson(File file) throws Exception {
        try (InputStream input = new FileInputStream(file)) { return readJson(input); }
    }

    private static JSONObject readJson(InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > 65536) throw new IOException("Driver metadata exceeds 64 KiB");
            bytes.write(buffer, 0, count);
        }
        return new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    }

    private static JSONObject readSelection(Context context) throws Exception {
        try (InputStream input = selection(context).openRead()) { return readJson(input); }
    }

    /** Called from SDL's native thread in the new game process, not a cached SharedPreferences instance. */
    static String[] config(Context context) {
        if (!supported()) return null;
        try {
            JSONObject selected = readSelection(context);
            String id = selected.getString("directory");
            UUID.fromString(id);
            File root = new File(context.getFilesDir(), "gpu_drivers");
            File folder = new File(root, id);
            if (!folder.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()))
                throw new IOException("Invalid driver directory");
            String library = selected.getString("libraryName");
            DriverPackage.validateLibrary(folder, library);
            // libadrenotools concatenates directory + libraryName, so the trailing slash is required.
            return new String[] {context.getApplicationInfo().nativeLibraryDir,
                context.getCacheDir().getAbsolutePath() + "/", folder.getAbsolutePath() + "/", library};
        } catch (FileNotFoundException ignored) {
            return null; // system driver is selected by default
        } catch (Exception error) {
            Log.e("ScarletNX", "Invalid GPU driver selection; using system driver", error);
            return null;
        }
    }
}
