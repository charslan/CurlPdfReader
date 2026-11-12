package fi.harism.curl;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class CurlActivity extends Activity {

    private static final String TAG = "CurlActivity";
    private static final String PDF_ASSET_NAME = "book.pdf";   // put it in app/src/main/assets/
    private static final String APP_TITLE = "Flipbook";         // shown in the top bar

    /** true = curl goes left to right (page turns from the left edge). */
    private static final boolean LEFT_TO_RIGHT = true;

    private static final String PREFS = "reader";
    private static final String KEY_PAGE = "last_page_" + PDF_ASSET_NAME;

    // Colours
    private static final int COLOR_BG = 0xFF202830;
    private static final int COLOR_BAR = 0xFF161C23;
    private static final int COLOR_DIVIDER = 0xFF2B3440;
    private static final int COLOR_PILL = 0xFF2B3440;
    private static final int COLOR_TEXT = 0xFFF1F3F5;
    private static final int COLOR_TEXT_DIM = 0xFF9AA4B2;

    private CurlView mCurlView;
    private PdfPageProvider mPageProvider;
    private View mRoot;
    private EditText mPageInput;
    private TextView mPageTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        // Status (notification) bar hidden.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Keyboard must not resize the curl view.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        // ---------------- layout: top bar + curl view ----------------
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setFocusableInTouchMode(true);
        mRoot = root;

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        View divider = new View(this);
        divider.setBackgroundColor(COLOR_DIVIDER);
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        mCurlView = new CurlView(this);
        root.addView(mCurlView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        root.requestFocus();   // keep the page box from grabbing focus at start

        // ---------------- curl view setup ----------------
        mCurlView.setMirror(LEFT_TO_RIGHT);
        mCurlView.setBackgroundColor(COLOR_BG);
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
                updatePageIndicator(index);
            }
        });

        mPageProvider = new PdfPageProvider();
        mCurlView.setPageProvider(mPageProvider);

        // Restore the last page read.
        int saved = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_PAGE, 0);
        saved = Math.max(0, Math.min(saved, mPageProvider.getPageCount() - 1));
        mCurlView.setCurrentIndex(saved);
        updatePageIndicator(saved);
    }

    // ------------------------------------------------------------------
    // Top bar
    // ------------------------------------------------------------------
    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(COLOR_BAR);
        bar.setPadding(dp(16), 0, dp(16), 0);

        TextView title = new TextView(this);
        title.setText(APP_TITLE);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setSingleLine(true);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Rounded "pill" holding  [ 7 ] / 11
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(COLOR_PILL);
        pillBg.setCornerRadius(dp(18));
        pill.setBackground(pillBg);

        mPageInput = new EditText(this);
        mPageInput.setBackground(null);
        mPageInput.setTextColor(COLOR_TEXT);
        mPageInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        mPageInput.setGravity(Gravity.CENTER);
        mPageInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        mPageInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        mPageInput.setSingleLine(true);
        mPageInput.setSelectAllOnFocus(true);
        mPageInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        mPageInput.setPadding(0, 0, 0, 0);
        mPageInput.setMinWidth(dp(28));
        pill.addView(mPageInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mPageTotal = new TextView(this);
        mPageTotal.setTextColor(COLOR_TEXT_DIM);
        mPageTotal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        mPageTotal.setPadding(dp(4), 0, 0, 0);
        pill.addView(mPageTotal, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        bar.addView(pill, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)));

        mPageInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                    jumpFromInput();
                    return true;
                }
                return false;
            }
        });
        mPageInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus && mCurlView != null && mPageProvider != null) {
                    // Typing abandoned: show the real page number again.
                    updatePageIndicator(mCurlView.getCurrentIndex());
                }
            }
        });
        return bar;
    }

    private void jumpFromInput() {
        String text = mPageInput.getText().toString().trim();
        if (text.length() > 0) {
            try {
                int count = mPageProvider.getPageCount();
                int page = Math.max(1, Math.min(Integer.parseInt(text), count));
                mCurlView.setCurrentIndex(page - 1);
                saveIndex(page - 1);
            } catch (NumberFormatException e) {
                // ignore bad input
            }
        }
        clearInputFocus();
        updatePageIndicator(mCurlView.getCurrentIndex());
    }

    private void clearInputFocus() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mPageInput.getWindowToken(), 0);
        }
        mPageInput.clearFocus();
        mRoot.requestFocus();
    }

    // Tapping anywhere outside the page box closes the keyboard.
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && mPageInput != null
                && mPageInput.hasFocus()) {
            Rect r = new Rect();
            mPageInput.getGlobalVisibleRect(r);
            if (!r.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                clearInputFocus();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void updatePageIndicator(int index) {
        int count = mPageProvider.getPageCount();
        int shown = Math.max(1, Math.min(index + 1, count));
        mPageTotal.setText("/ " + count);
        if (!mPageInput.hasFocus()) {
            mPageInput.setText(String.valueOf(shown));
        }
    }

    private void saveIndex(int index) {
        int last = Math.max(0, mPageProvider.getPageCount() - 1);
        int clamped = Math.max(0, Math.min(index, last));
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_PAGE, clamped).apply();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------
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

    // ------------------------------------------------------------------
    // PDF page provider
    // ------------------------------------------------------------------
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

                float pw = pdfPage.getWidth();
                float ph = pdfPage.getHeight();
                float scale = Math.min(width / pw, height / ph);
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                m.postTranslate((width - pw * scale) / 2f, (height - ph * scale) / 2f);
                pdfPage.render(bitmap, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

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
