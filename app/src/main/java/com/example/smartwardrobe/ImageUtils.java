package com.example.smartwardrobe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

// Saving, shrinking and encoding clothing photos.
public class ImageUtils {

    public static final int FULL_SIZE = 1024;   // stored photo, also sent to the AI
    public static final int THUMB_SIZE = 256;   // used in lists
    private static final int JPEG_QUALITY = 85;
    private static final String IMAGE_FOLDER = "images";

    public static File newImageFile(Context context, String prefix) {
        File folder = new File(context.getFilesDir(), IMAGE_FOLDER);
        folder.mkdirs();
        return new File(folder, prefix + System.currentTimeMillis() + ".jpg");
    }

    // Copies the image at "source" into "dest", rotated upright and no bigger than maxSide pixels.
    public static boolean saveScaledCopy(Context context, Uri source, File dest, int maxSide) {
        try {
            // 1. Read only the size, so a 12MP photo is never fully loaded into memory
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream in = context.getContentResolver().openInputStream(source);
            BitmapFactory.decodeStream(in, null, bounds);
            in.close();
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return false;
            }

            // 2. Decode a smaller version
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) {
                sample = sample * 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            in = context.getContentResolver().openInputStream(source);
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
            in.close();
            if (bitmap == null) {
                return false;
            }

            // 3. Phone cameras store photos sideways and record the rotation in EXIF
            in = context.getContentResolver().openInputStream(source);
            int rotation = readRotation(in);
            in.close();

            // 4. Scale to the exact size, rotate, and save as JPEG
            float scale = Math.min(1f, maxSide / (float) Math.max(bitmap.getWidth(), bitmap.getHeight()));
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            matrix.postRotate(rotation);
            Bitmap result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

            FileOutputStream out = new FileOutputStream(dest);
            result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            out.close();
            return true;
        } catch (IOException | SecurityException | OutOfMemoryError e) {
            return false;
        }
    }

    private static int readRotation(InputStream in) {
        try {
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                return 90;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                return 180;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                return 270;
            }
        } catch (IOException e) {
            // No EXIF data: treat the photo as already upright
        }
        return 0;
    }

    public static String toBase64(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            bytes.write(buffer, 0, read);
        }
        in.close();
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
    }

    public static void deleteQuietly(String path) {
        if (path != null) {
            new File(path).delete();
        }
    }
}
