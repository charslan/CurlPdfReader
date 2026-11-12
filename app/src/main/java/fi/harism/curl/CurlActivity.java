package fi.harism.curl;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class CurlActivity extends Activity {

    private static final String TAG = "CurlActivity";
    private static final String PDF_ASSET_NAME = "book.pdf";   // put it in app/src/main/assets/

    /** true = curl goes left to right (page turns from the left edge). */
    private static final boolean LEFT_TO_RIGHT = true;

    private static final String PREFS = "reader";
    private static final String KEY_PAGE = "last_page_" + PDF_ASSET_NAME;

    private CurlView mCurlView;
    private Button mPageButton;
    private PdfPageProvider mPageProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mCurlView = new CurlView(this);

        FrameLayout root = new FrameLayout(this);
        root.addView(mCurlView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mPageButton = new Button(this);
        mPageButton.setAllCaps(false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = (int) (16 * getResources().getDisplayMetrics().density);
        root.addView(mPageButton, lp);
        mPageButton.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                showJumpDialog();
            }
        });

        setContentView(root);

        mCurlView.setMirror(LEFT_TO_RIGHT);
        mCurlView.setBackgroundColor(0xFF202830);
        mCurlView.setAllowLastPageCurl(true);

        mCurlView.setSizeChangedObserver(new CurlView.SizeChangedObserver() {
            @Override
            public void onSizeChanged(int w, int h) {
                if (w > h) {
                    mCurlView.setViewMode(CurlView.SHOW_TWO_PAGES);
                } else {
                    mCurlView.setViewMode(CurlView.SHOW_ONE_PAGE);
                }
                mCurlView.setMargins(0.05f, 0.05f, 0.05f, 0.05f);
            }
        });

        mCurlView.setOnPageChangeListener(new CurlView.OnPageChangeListener() {
            @Override
            public void pageChanged(int index) {
                saveIndex(index);
                updatePageButton(index);
            }
        });

        mPageProvider = new PdfPageProvider();
        mCurlView.setPageProvider(mPageProvider);

        // Restore the last page read.
        int saved = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_PAGE, 0);
        saved = Math.max(0, Math.min(saved, mPageProvider.getPageCount() - 1));
        mCurlView.setCurrentIndex(saved);
        updatePageButton(saved);
    }

    private void saveIndex(int index) {
        int last = Math.max(0, mPageProvider.getPageCount() - 1);
        int clamped = Math.max(0, Math.min(index, last));
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_PAGE, clamped).apply();
    }

    private void updatePageButton(int index) {
        int count = mPageProvider.getPageCount();
        int shown = Math.max(1, Math.min(index + 1, count));
        mPageButton.setText(shown + " / " + count);
    }

    private void showJumpDialog() {
        final int count = mPageProvider.getPageCount();
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("1 - " + count);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Go to page")
                .setView(input)
                .setPositiveButton("Go", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String text = input.getText().toString().trim();
                        if (text.length() == 0) {
                            return;
                        }
                        try {
                            int page = Integer.parseInt(text);
                            page = Math.max(1, Math.min(page, count));
                            mCurlView.setCurrentIndex(page - 1);
                            saveIndex(page - 1);
                            updatePageButton(page - 1);
                        } catch (NumberFormatException e) {
                            // ignore bad input
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveIndex(mCurlView.getCurrentIndex());
        mCurlView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCurlView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPageProvider != null) {
            mPageProvider.close();
        }
    }

    private class PdfPageProvider implements CurlView.PageProvider {

        private PdfRenderer pdfRenderer;
        private ParcelFileDescriptor fileDescriptor;
        private int pageCount = 0;

        PdfPageProvider() {
            try {
                File cacheFile = new File(getCacheDir(), PDF_ASSET_NAME);
                if (!cacheFile.exists() || cacheFile.length() == 0) {
                    try (InputStream in = getAssets().open(PDF_ASSET_NAME);
                         FileOutputStream out = new FileOutputStream(cacheFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                }
                fileDescriptor = ParcelFileDescriptor.open(cacheFile,
                        ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(fileDescriptor);
                pageCount = pdfRenderer.getPageCount();
                Log.i(TAG, "PDF loaded. Pages: " + pageCount);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load PDF from assets", e);
                pageCount = 0;
            }
        }

        @Override
        public int getPageCount() {
            return pageCount > 0 ? pageCount : 1;
        }

        // synchronized: PdfRenderer allows only ONE open page at a time and
        // updatePage() is called from both the UI thread and the GL thread.
        @Override
        public synchronized void updatePage(CurlPage page, int width, int height, int index) {
            if (width <= 0 || height <= 0) {
                return;
            }
            if (pdfRenderer == null || index < 0 || index >= pageCount) {
                Bitmap blank = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                blank.eraseColor(Color.WHITE);
                page.setTexture(blank, CurlPage.SIDE_BOTH);
                return;
            }

            PdfRenderer.Page pdfPage = null;
            try {
                pdfPage = pdfRenderer.openPage(index);

                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);

                // render() with a null matrix draws the page at 72 dpi in the
                // top-left corner. Scale it to fit and centre it instead.
                float pw = pdfPage.getWidth();
                float ph = pdfPage.getHeight();
                float scale = Math.min(width / pw, height / ph);
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                m.postTranslate((width - pw * scale) / 2f, (height - ph * scale) / 2f);
                pdfPage.render(bitmap, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                // The whole scene is mirrored for left-to-right curl, so pre-flip
                // the bitmap; the two flips cancel and text reads normally.
                if (LEFT_TO_RIGHT) {
                    Matrix flip = new Matrix();
                    flip.preScale(-1f, 1f);
                    Bitmap flipped = Bitmap.createBitmap(bitmap, 0, 0, width, height, flip, true);
                    bitmap.recycle();
                    bitmap = flipped;
                }

                page.setTexture(bitmap, CurlPage.SIDE_FRONT);
                page.setColor(0xFFE8E8E8, CurlPage.SIDE_BACK);
            } catch (Exception e) {
                Log.e(TAG, "Error rendering page " + index, e);
                page.setColor(Color.LTGRAY, CurlPage.SIDE_BOTH);
            } finally {
                if (pdfPage != null) {
                    pdfPage.close();
                }
            }
        }

        synchronized void close() {
            try {
                if (pdfRenderer != null) {
                    pdfRenderer.close();
                    pdfRenderer = null;
                }
                if (fileDescriptor != null) {
                    fileDescriptor.close();
                    fileDescriptor = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing PDF", e);
            }
        }
    }
}
