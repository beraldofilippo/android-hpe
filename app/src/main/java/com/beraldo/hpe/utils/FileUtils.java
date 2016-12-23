/*
 * Copyright 2016 Tzutalin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.beraldo.hpe.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.RawRes;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileUtils {
    public static String TAG = "FileUtils";

    public static final String PREFS_NAME = "SelfearPrefsFile";
    public static final String MAIN_DIR_PREFS_NAME = "SelfearPrefsFile_Main";
    public static final String DATA_DIR_PREFS_NAME = "SelfearPrefsFile_Data";
    public static final String DETECTIONS_DIR_PREFS_NAME = "SelfearPrefsFile_Detections";
    public static final String PARAMS_DIR_PREFS_NAME = "SelfearPrefsFile_Params";

    public static String PREDICTOR_FILE_NAME = "shape_predictor_68_face_landmarks.dat";

    @NonNull
    public static final void copyFileFromRawToOthers(@NonNull final Context context, @RawRes int id, @NonNull final String targetPath) {
        InputStream in = context.getResources().openRawResource(id);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(targetPath);
            byte[] buff = new byte[1024];
            int read = 0;
            while ((read = in.read(buff)) > 0) {
                out.write(buff, 0, read);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to copy file with copyFileFromRawToOthers(...)\n" + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Unable to copy file with copyFileFromRawToOthers(...)\n" + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void savePreference(Context ctx, String key, String value) {
        // We need an Editor object to make preference changes.
        // All objects are from android.context.Context
        SharedPreferences settings = ctx.getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        if(!value.isEmpty()) editor.putString(key, value);
        else editor.putString(key, Environment.getExternalStorageDirectory().getAbsolutePath()); // Default

        // Commit the edits!
        editor.apply();
    }

    public static String getPreference(Context ctx, String key) {
        // Restore preferences
        SharedPreferences settings = ctx.getSharedPreferences(PREFS_NAME, 0);
        return settings.getString(key, Environment.getExternalStorageDirectory().getAbsolutePath());
    }
}
