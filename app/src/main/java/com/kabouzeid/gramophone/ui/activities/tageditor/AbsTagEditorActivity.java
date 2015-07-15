package com.kabouzeid.gramophone.ui.activities.tageditor;

import android.app.SearchManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.ThemeSingleton;
import com.afollestad.materialdialogs.util.DialogUtils;
import com.github.ksoichiro.android.observablescrollview.ObservableScrollView;
import com.kabouzeid.gramophone.R;
import com.kabouzeid.gramophone.misc.SmallObservableScrollViewCallbacks;
import com.kabouzeid.gramophone.ui.activities.base.AbsBaseActivity;
import com.kabouzeid.gramophone.util.MusicUtil;
import com.kabouzeid.gramophone.util.Util;
import com.kabouzeid.gramophone.util.ViewUtil;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.images.Artwork;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
public abstract class AbsTagEditorActivity extends AbsBaseActivity {

    private static final String TAG = AbsTagEditorActivity.class.getSimpleName();

    private static final int REQUEST_CODE_SELECT_IMAGE = 1337;

    public static final String EXTRA_ID = "extra_id";
    public static final String EXTRA_PALETTE = "extra_palette";

    private int id;
    private int headerVariableSpace;
    private int paletteColorPrimary;
    private boolean isInNoImageMode;

    @Bind(R.id.play_pause_fab)
    FloatingActionButton fab;
    @Bind(R.id.observableScrollView)
    ObservableScrollView observableScrollView;
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.image)
    ImageView image;
    @Bind(R.id.header)
    LinearLayout header;

    private List<String> songPaths;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentViewLayout());
        ButterKnife.bind(this);

        getIntentExtras();

        songPaths = getSongPaths();
        if (songPaths.isEmpty()) {
            finish();
            return;
        }

        headerVariableSpace = getResources().getDimensionPixelSize(R.dimen.tagEditorHeaderVariableSpace);

        setUpViews();

        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(getResources().getString(R.string.tag_editor));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void setUpViews() {
        resetColors();
        setUpScrollView();
        setUpFab();
        setUpImageView();
    }

    private void setUpScrollView() {
        observableScrollView.setScrollViewCallbacks(observableScrollViewCallbacks);
    }

    private final SmallObservableScrollViewCallbacks observableScrollViewCallbacks = new SmallObservableScrollViewCallbacks() {
        @Override
        public void onScrollChanged(int scrollY, boolean b, boolean b2) {
            float alpha;
            if (!isInNoImageMode) {
                alpha = 1 - (float) Math.max(0, headerVariableSpace - scrollY) / headerVariableSpace;
            } else {
                header.setTranslationY(scrollY);
                alpha = 1;
            }
            ViewUtil.setBackgroundAlpha(toolbar, alpha, paletteColorPrimary);
            image.setTranslationY(scrollY / 2);
        }
    };

    private void setUpImageView() {
        loadCurrentImage();
        final CharSequence[] items = new CharSequence[]{
                getString(R.string.download_from_last_fm),
                getString(R.string.pick_from_local_storage),
                getString(R.string.web_search),
                getString(R.string.remove_cover)
        };
        image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialDialog.Builder(AbsTagEditorActivity.this)
                        .title(R.string.update_image)
                        .items(items)
                        .itemsCallback(new MaterialDialog.ListCallback() {
                            @Override
                            public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                                switch (which) {
                                    case 0:
                                        getImageFromLastFM();
                                        break;
                                    case 1:
                                        startImagePicker();
                                        break;
                                    case 2:
                                        searchImageOnWeb();
                                        break;
                                    case 3:
                                        deleteImage();
                                        break;
                                }
                            }
                        }).show();
            }
        });
    }

    private void startImagePicker() {
        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, REQUEST_CODE_SELECT_IMAGE);
    }

    protected abstract void loadCurrentImage();

    protected abstract void getImageFromLastFM();

    protected abstract void searchImageOnWeb();

    protected abstract void deleteImage();

    private void setUpFab() {
        fab.setScaleX(0);
        fab.setScaleY(0);
        fab.setEnabled(false);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        fab.setRippleColor(ThemeSingleton.get().positiveColor);
    }

    protected abstract void save();


    private void resetColors() {
        paletteColorPrimary = getThemeColorPrimary();
        observableScrollViewCallbacks.onScrollChanged(observableScrollView.getCurrentScrollY(), false, false);
        setStatusBarColor(paletteColorPrimary);
        if (shouldColorNavigationBar())
            setNavigationBarColor(paletteColorPrimary);
        header.setBackgroundColor(paletteColorPrimary);
        notifyTaskColorChange(paletteColorPrimary);
    }

    private void getIntentExtras() {
        Bundle intentExtras = getIntent().getExtras();
        if (intentExtras != null) {
            id = intentExtras.getInt(EXTRA_ID);
        }
    }

    protected abstract int getContentViewLayout();

    @NonNull
    protected abstract List<String> getSongPaths();

    protected void searchWebFor(@NonNull List<String> strings) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String string : strings) {
            stringBuilder.append(string);
            stringBuilder.append(" ");
        }
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        intent.putExtra(SearchManager.QUERY, stringBuilder.toString());
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                super.onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    protected void setNoImageMode() {
        isInNoImageMode = true;
        image.setVisibility(View.GONE);
        image.setEnabled(false);
        observableScrollView.setPadding(0, Util.getActionBarSize(this), 0, 0);
        observableScrollViewCallbacks.onScrollChanged(observableScrollView.getCurrentScrollY(), false, false);

        paletteColorPrimary = getIntent().getIntExtra(EXTRA_PALETTE,
                getThemeColorPrimary());
        toolbar.setBackgroundColor(paletteColorPrimary);
        header.setBackgroundColor(paletteColorPrimary);

        if (shouldColorNavigationBar())
            setNavigationBarColor(paletteColorPrimary);
        setStatusBarColor(paletteColorPrimary);
    }

    protected void dataChanged() {
        showFab();
    }

    private void showFab() {
        fab.animate()
                .setDuration(500)
                .setInterpolator(new OvershootInterpolator())
                .scaleX(1)
                .scaleY(1)
                .start();
        fab.setEnabled(true);
    }

    protected void setImageRes(int resId) {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resId);
        setImageBitmap(bitmap);
    }

    protected void setImageBitmap(@Nullable final Bitmap bitmap) {
        if (bitmap != null) {
            image.setImageBitmap(bitmap);
            applyPalette(bitmap);
        } else {
            resetColors();
        }
    }

    private void applyPalette(@NonNull final Bitmap bitmap) {
        Palette.from(bitmap)
                .resizeBitmapSize(100)
                .generate(new Palette.PaletteAsyncListener() {
                    @Override
                    public void onGenerated(@NonNull Palette palette) {
                        final Palette.Swatch vibrantSwatch = palette.getVibrantSwatch();
                        if (vibrantSwatch != null) {
                            paletteColorPrimary = palette.getVibrantColor(DialogUtils.resolveColor(AbsTagEditorActivity.this, R.attr.default_bar_color));
                            observableScrollViewCallbacks.onScrollChanged(observableScrollView.getCurrentScrollY(), false, false);
                            if (shouldColorNavigationBar())
                                setNavigationBarColor(paletteColorPrimary);
                            setStatusBarColor(paletteColorPrimary);
                            header.setBackgroundColor(paletteColorPrimary);
                            notifyTaskColorChange(paletteColorPrimary);
                        } else {
                            resetColors();
                        }
                    }
                });
    }

    @Override
    protected boolean overridesTaskColor() {
        return true;
    }

    protected void writeValuesToFiles(@NonNull final Map<FieldKey, String> fieldKeyValueMap) {
        writeValuesToFiles(fieldKeyValueMap, null, false);
    }

    protected void writeValuesToFiles(@NonNull final Map<FieldKey, String> fieldKeyValueMap, @Nullable final Artwork artwork, final boolean deleteArtwork) {
        Util.hideSoftKeyboard(this);
        final String writingFileStr = getResources().getString(R.string.writing_file_number);
        final MaterialDialog progressDialog = new MaterialDialog.Builder(AbsTagEditorActivity.this)
                .title(R.string.saving_changes)
                .cancelable(false)
                .progress(true, 0)
                .build();
        if (Build.VERSION.SDK_INT >= 18)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        progressDialog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < songPaths.size(); i++) {
                    String songPath = songPaths.get(i);
                    final int finalI = i;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.setContent(writingFileStr + " " + ((finalI + 1) + "/" + songPaths.size()));
                        }
                    });
                    try {
                        AudioFile audioFile = AudioFileIO.read(new File(songPath));
                        Tag tag = audioFile.getTagOrCreateAndSetDefault();
                        for (Map.Entry<FieldKey, String> entry : fieldKeyValueMap.entrySet()) {
                            try {
                                tag.setField(entry.getKey(), entry.getValue());
                            } catch (NumberFormatException e) {
                                tag.deleteField(entry.getKey());
                            }
                        }
                        if (deleteArtwork) {
                            tag.deleteArtworkField();
                        } else if (artwork != null) {
                            tag.deleteArtworkField();
                            tag.setField(artwork);
                        }
                        audioFile.commit();
                    } catch (@NonNull CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException e) {
                        Log.e(TAG, "Error while reading audio file.", e);
                    } catch (CannotWriteException e) {
                        Log.e(TAG, "Error while writing audio file.", e);
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.setContent(getString(R.string.rescanning_media));
                        if (deleteArtwork) {
                            MusicUtil.deleteAlbumArt(AbsTagEditorActivity.this, getId());
                            ImageLoader.getInstance().clearMemoryCache();
                        } else if (artwork != null) {
                            ImageLoader.getInstance().clearMemoryCache();
                        }
                    }
                });
                rescanMediaAndQuitOnFinish(new OnScannedAllListener() {
                    @Override
                    public void onScannedAll() {
                        progressDialog.dismiss();
                        finish();
                    }
                });
            }
        }).start();
    }

    private void rescanMediaAndQuitOnFinish(@NonNull final OnScannedAllListener listener) {
        String[] toBeScanned = new String[songPaths.size()];
        toBeScanned = songPaths.toArray(toBeScanned);
        final int toBeScannedLength = toBeScanned.length;
        MediaScannerConnection.scanFile(this, toBeScanned, null, new MediaScannerConnection.OnScanCompletedListener() {
            int i = 0;

            @Override
            public void onScanCompleted(String s, Uri uri) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (i == 0 || i == toBeScannedLength - 1) {
                            if (i == toBeScannedLength - 1)
                                listener.onScannedAll();
                        }
                        i++;
                    }
                });
            }
        });
    }

    protected int getId() {
        return id;
    }

    protected void writeValuesToFiles(@NonNull final Map<FieldKey, String> fieldKeyValueMap, @Nullable final Artwork artwork) {
        if (artwork == null) {
            writeValuesToFiles(fieldKeyValueMap, null, true);
        } else {
            writeValuesToFiles(fieldKeyValueMap, artwork, false);
        }
    }

    protected void writeValuesToFiles(@NonNull final Map<FieldKey, String> fieldKeyValueMap, boolean deleteArtwork) {
        writeValuesToFiles(fieldKeyValueMap, null, deleteArtwork);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @NonNull Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);
        switch (requestCode) {
            case REQUEST_CODE_SELECT_IMAGE:
                if (resultCode == RESULT_OK) {
                    Uri selectedImage = imageReturnedIntent.getData();
                    loadImageFromFile(selectedImage);
                }
        }
    }

    protected abstract void loadImageFromFile(Uri selectedFile);

    @Nullable
    protected String getSongTitle() {
        try {
            return getAudioFile(songPaths.get(0)).getTagOrCreateAndSetDefault().getFirst(FieldKey.TITLE);
        } catch (NullPointerException e) {
            return null;
        }
    }

    private AudioFile getAudioFile(@NonNull String path) {
        try {
            return AudioFileIO.read(new File(path));
        } catch (@NonNull CannotReadException | ReadOnlyFileException | InvalidAudioFrameException | TagException | IOException e) {
            Log.e(TAG, "Error while trying to create the AudioFile from java.io.File", e);
        }
        return null;
    }

    @Nullable
    protected String getAlbumTitle() {
        try {
            return getAudioFile(songPaths.get(0)).getTagOrCreateAndSetDefault().getFirst(FieldKey.ALBUM);
        } catch (NullPointerException ignored) {
        }
        return null;
    }

    @Nullable
    protected String getArtistName() {
        try {
            return getAudioFile(songPaths.get(0)).getTagOrCreateAndSetDefault().getFirst(FieldKey.ARTIST);
        } catch (NullPointerException ignored) {
        }
        return null;
    }

    @Nullable
    protected String getAlbumArtistName() {
        try {
            return getAudioFile(songPaths.get(0)).getTagOrCreateAndSetDefault().getFirst(FieldKey.ALBUM_ARTIST);
        } catch (NullPointerException ignored) {
        }
        return null;
    }

    @Nullable
    protected String getGenreName() {
        try {
            return getAudioFile(songPaths.get(0)).getTagOrCreateAndSetDefault().getFirst(FieldKey.GENRE);
        } catch (NullPointerException ignored) {
        }
        return null;
    }

    @Nullable
    protected String getSongYear() {
        try {
            return getAudioFile(songPaths.get(0)).getTagOrCreateAndSetDefault().getFirst(FieldKey.YEAR);
        } catch (NullPointerException ignored) {
        }
        return null;
    }

    @Nullable
    protected String getTrackNumber() {
        try {
            return getAudioFile(songPaths.get(0)).getTagOrCreateAndSetDefault().getFirst(FieldKey.TRACK);
        } catch (NullPointerException ignored) {
        }
        return null;
    }

    @Nullable
    protected Bitmap getAlbumArt() {
        try {
            Artwork artworkTag = getAudioFile(songPaths.get(0)).getTagOrCreateAndSetDefault().getFirstArtwork();
            if (artworkTag != null) {
                byte[] artworkBinaryData = artworkTag.getBinaryData();
                return BitmapFactory.decodeByteArray(artworkBinaryData, 0, artworkBinaryData.length);
            }
        } catch (NullPointerException ignored) {
        }
        return null;
    }

    private interface OnScannedAllListener {
        void onScannedAll();
    }
}
