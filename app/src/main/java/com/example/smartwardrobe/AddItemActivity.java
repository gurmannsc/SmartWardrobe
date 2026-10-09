package com.example.smartwardrobe;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

// Take or pick a photo, then let the AI identify it before opening the editor.
public class AddItemActivity extends AppCompatActivity {

    private static final String STATE_IMAGE = "image";
    private static final String STATE_THUMB = "thumb";
    private static final String STATE_CAMERA = "camera";
    private static final String CAMERA_FOLDER = "camera";
    private static final String CAMERA_FILE = "capture.jpg";
    private static final String IMAGE_PREFIX = "item_";
    private static final String THUMB_PREFIX = "thumb_";
    private static final String IMAGE_MIME = "image/*";

    ImageView ivPreview;
    Button btnTakePhoto, btnGallery, btnAnalyse, btnTagManually;
    ProgressBar pbAnalyse;
    TextView tvStatus;

    File imageFile, thumbFile;   // the current photo, not saved to the wardrobe yet
    Uri cameraUri;

    ActivityResultLauncher<Uri> takePictureLauncher;
    ActivityResultLauncher<String> pickImageLauncher;
    ActivityResultLauncher<Intent> editorLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_item);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ivPreview = findViewById(R.id.ivPreview);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnGallery = findViewById(R.id.btnGallery);
        btnAnalyse = findViewById(R.id.btnAnalyse);
        btnTagManually = findViewById(R.id.btnTagManually);
        pbAnalyse = findViewById(R.id.pbAnalyse);
        tvStatus = findViewById(R.id.tvStatus);

        // Camera app (implicit intent) saves the photo to cameraUri
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(),
                new ActivityResultCallback<Boolean>() {
                    @Override
                    public void onActivityResult(Boolean success) {
                        if (Boolean.TRUE.equals(success)) {
                            processImage(cameraUri);
                        }
                    }
                });

        // Gallery / file picker (implicit intent)
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri != null) {
                            processImage(uri);
                        }
                    }
                });

        // When the editor saves the item, get ready for the next one
        editorLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK) {
                            imageFile = null;
                            thumbFile = null;
                            resetScreen();
                            Toast.makeText(AddItemActivity.this, R.string.toast_saved_next, Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        // Restore the photo if the screen was rotated or the app was in the background
        if (savedInstanceState != null) {
            String image = savedInstanceState.getString(STATE_IMAGE);
            String thumb = savedInstanceState.getString(STATE_THUMB);
            String camera = savedInstanceState.getString(STATE_CAMERA);
            if (camera != null) {
                cameraUri = Uri.parse(camera);
            }
            if (image != null && thumb != null) {
                imageFile = new File(image);
                thumbFile = new File(thumb);
                showPhoto();
            }
        }

        btnTakePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File folder = new File(getCacheDir(), CAMERA_FOLDER);
                folder.mkdirs();
                File capture = new File(folder, CAMERA_FILE);
                cameraUri = FileProvider.getUriForFile(AddItemActivity.this,
                        getPackageName() + getString(R.string.file_provider_suffix), capture);
                try {
                    takePictureLauncher.launch(cameraUri);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(AddItemActivity.this, R.string.err_no_camera, Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImageLauncher.launch(IMAGE_MIME);
            }
        });

        btnAnalyse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                analyse();
            }
        });

        btnTagManually.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (imageFile == null) {
                    Toast.makeText(AddItemActivity.this, R.string.err_pick_image_first, Toast.LENGTH_SHORT).show();
                    return;
                }
                openEditor(null);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (imageFile != null && thumbFile != null) {
            outState.putString(STATE_IMAGE, imageFile.getAbsolutePath());
            outState.putString(STATE_THUMB, thumbFile.getAbsolutePath());
        }
        if (cameraUri != null) {
            outState.putString(STATE_CAMERA, cameraUri.toString());
        }
    }

    // Leaving the screen without saving: delete the unused photo files
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            deleteUnsavedPhoto();
        }
    }

    // Shrinks the chosen photo and stores a full copy plus a thumbnail in app storage
    private void processImage(final Uri source) {
        setBusy(true, R.string.status_processing);
        new Thread(new Runnable() {
            @Override
            public void run() {
                deleteUnsavedPhoto();
                final File newImage = ImageUtils.newImageFile(AddItemActivity.this, IMAGE_PREFIX);
                final File newThumb = ImageUtils.newImageFile(AddItemActivity.this, THUMB_PREFIX);
                final boolean ok = ImageUtils.saveScaledCopy(AddItemActivity.this, source, newImage, ImageUtils.FULL_SIZE)
                        && ImageUtils.saveScaledCopy(AddItemActivity.this, Uri.fromFile(newImage), newThumb, ImageUtils.THUMB_SIZE);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setBusy(false, 0);
                        if (ok) {
                            imageFile = newImage;
                            thumbFile = newThumb;
                            showPhoto();
                        } else {
                            newImage.delete();
                            newThumb.delete();
                            imageFile = null;
                            thumbFile = null;
                            resetScreen();
                            Toast.makeText(AddItemActivity.this, R.string.err_image_load, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void analyse() {
        if (imageFile == null) {
            Toast.makeText(this, R.string.err_pick_image_first, Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true, R.string.status_analysing);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONObject result = new GroqClient(AddItemActivity.this).identifyItem(imageFile);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(false, 0);
                            if (!result.optBoolean("is_clothing", true)) {
                                tvStatus.setText(R.string.err_not_clothing);
                                return;
                            }
                            tvStatus.setText(R.string.status_photo_ready);
                            openEditor(result);
                        }
                    });
                } catch (final GroqClient.AiException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(false, 0);
                            tvStatus.setText(e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    // ai == null means the user tags the item by hand
    private void openEditor(JSONObject ai) {
        Intent intent = new Intent(AddItemActivity.this, ItemEditorActivity.class);
        intent.putExtra(ItemEditorActivity.EXTRA_IMAGE_PATH, imageFile.getAbsolutePath());
        intent.putExtra(ItemEditorActivity.EXTRA_THUMB_PATH, thumbFile.getAbsolutePath());
        if (ai != null) {
            intent.putExtra(ItemEditorActivity.EXTRA_FROM_AI, true);
            intent.putExtra(ItemEditorActivity.EXTRA_CATEGORY, ai.optString("category"));
            intent.putExtra(ItemEditorActivity.EXTRA_TYPE, ai.optString("sub_type"));
            intent.putExtra(ItemEditorActivity.EXTRA_COLOR, ai.optString("color"));
            intent.putExtra(ItemEditorActivity.EXTRA_COLOR2, ai.optString("secondary_color"));
            intent.putExtra(ItemEditorActivity.EXTRA_PATTERN, ai.optString("pattern"));
            intent.putExtra(ItemEditorActivity.EXTRA_FORMALITY, ai.optString("formality"));
            ArrayList<String> seasons = new ArrayList<>();
            JSONArray array = ai.optJSONArray("seasons");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    seasons.add(array.optString(i));
                }
            }
            intent.putStringArrayListExtra(ItemEditorActivity.EXTRA_SEASONS, seasons);
        }
        editorLauncher.launch(intent);
    }

    private void showPhoto() {
        ivPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivPreview.setImageURI(Uri.fromFile(imageFile));
        btnAnalyse.setEnabled(true);
        tvStatus.setText(R.string.status_photo_ready);
    }

    private void resetScreen() {
        ivPreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        ivPreview.setImageResource(R.drawable.ic_hanger);
        btnAnalyse.setEnabled(false);
        tvStatus.setText("");
    }

    private void setBusy(boolean busy, int statusRes) {
        pbAnalyse.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnAnalyse.setEnabled(!busy && imageFile != null);
        btnTakePhoto.setEnabled(!busy);
        btnGallery.setEnabled(!busy);
        btnTagManually.setEnabled(!busy);
        if (busy) {
            tvStatus.setText(statusRes);
        }
    }

    private void deleteUnsavedPhoto() {
        if (imageFile != null) {
            imageFile.delete();
        }
        if (thumbFile != null) {
            thumbFile.delete();
        }
    }
}
