package fi.harism.curl;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CurlActivity extends Activity {

    private static final String TAG = "CurlActivity";
    private static final String PDF_ASSET_NAME = "book.pdf";   // app/src/main/assets/book.pdf
    private static final String TOC_ASSET_NAME = "toc.txt";    // app/src/main/assets/toc.txt (optional)
    /** Nastaleeq font used for the title, subtitle and side menu (numbers keep the normal font). */
    private static final String NASTALIQ_FONT_ASSET = "fonts/nastaliq.ttf";   // app/src/main/assets/fonts/nastaliq.ttf

    // Text sizes in sp. Make a number smaller or larger to taste.
    private static final float SIZE_TITLE = 22;          // app title (top bar)
    private static final float SIZE_SUBTITLE = 14;       // current heading (top bar)
    private static final float SIZE_MENU_HEADER = 20;    // "مینو"
    private static final float SIZE_SECTION = 18;        // "فہرست", "سیٹنگ"
    private static final float SIZE_TOC_1 = 16;          // headings in the table of contents
    private static final float SIZE_TOC_2 = 14;          // sub headings
    private static final float SIZE_SETTING = 15;        // setting names

    // ---------------- things you will likely change ----------------
    private static final String APP_TITLE = "فلپ بک";
    private static final String APP_SUBTITLE = "";   // shown only when the page has no heading in toc.txt

    /** true = whole interface runs right to left (Urdu / Arabic). */
    private static final boolean RTL_UI = true;
    /** true = curl goes left to right (page turns from the left edge). */
    private static final boolean LEFT_TO_RIGHT = true;
    /** Side menu width: this many dp, but never more than 68% of the screen. */
    private static final int DRAWER_WIDTH_DP = 250;

    // Menu texts (replace with Urdu / Arabic if you like)
    private static final String STR_MENU = "مینو";
    private static final String STR_TOC = "فہرست";
    private static final String STR_NO_TOC = "فہرست دستیاب نہیں\nایپ کے assets میں toc.txt شامل کریں";
    private static final String STR_SETTINGS = "سیٹنگ";
    private static final String STR_HIDE_SUBTITLE = "ایپ کا ذیلی عنوان چھپائیں";
    // ----------------------------------------------------------------

    private static final String PREFS = "reader";
    private static final String KEY_PAGE = "last_page_" + PDF_ASSET_NAME;
    private static final String KEY_HIDE_SUBTITLE = "hide_subtitle";

    // Colours
    private static final int COLOR_BG = 0xFF10151C;
    private static final int COLOR_BAR_TOP = 0xFF222C3A;
    private static final int COLOR_BAR_BOTTOM = 0xFF151B24;
    private static final int COLOR_DRAWER = 0xFF1A222D;
    private static final int COLOR_DIVIDER = 0xFF2A3544;
    private static final int COLOR_ACCENT = 0xFF4FD1C5;          // teal
    private static final int COLOR_ACCENT_FADE = 0x004FD1C5;     // transparent teal
    private static final int COLOR_PILL = 0x1FFFFFFF;
    private static final int COLOR_PILL_STROKE = 0x33FFFFFF;
    private static final int COLOR_TEXT = 0xFFF1F3F5;
    private static final int COLOR_TEXT_SUB = 0xFFC3CCD8;
    private static final int COLOR_TEXT_DIM = 0xFF8D99AB;

    private static class TocEntry {
        final int level;
        final int page;      // 1-based PDF page, same number as the page box
        final String title;

        TocEntry(int level, int page, String title) {
            this.level = level;
            this.page = page;
            this.title = title;
        }
    }

    private CurlView mCurlView;
    private PdfPageProvider mPageProvider;
    private View mRoot;
    private EditText mPageInput;
    private TextView mPageTotal;
    private NastaliqLabel mSubtitleView;
    private int mTopBarHeight;   // computed from the font, so title + subtitle always fit
    private Typeface mNastaliq;   // null = font file missing, normal font is used

    private FrameLayout mFrame;
    private View mDrawer;
    private View mScrim;
    private int mDrawerWidth;
    private boolean mDrawerOpen = false;

    private boolean mHideSubtitle = false;
    private String mBookTitle = APP_TITLE;   // can be set with an  @title=  line in toc.txt
    private final List<TocEntry> mToc = new ArrayList<TocEntry>();
    private final List<NastaliqLabel> mTocTitleViews = new ArrayList<NastaliqLabel>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        // Status (notification) bar hidden.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Keyboard must not resize the curl view.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        try {
            mNastaliq = Typeface.createFromAsset(getAssets(), NASTALIQ_FONT_ASSET);
        } catch (Exception e) {
            Log.w(TAG, "Nastaliq font not found in assets: " + NASTALIQ_FONT_ASSET);
            mNastaliq = null;
        }

        mHideSubtitle = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_HIDE_SUBTITLE, false);
        loadToc();

        // ---------------- layout: top bar, curl view, bottom bar ----------------
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setFocusableInTouchMode(true);
        mRoot = root;

        View topBar = buildTopBar();
        root.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, mTopBarHeight));

        View accentLine = new View(this);
        accentLine.setBackground(new GradientDrawable(
                RTL_UI ? GradientDrawable.Orientation.RIGHT_LEFT : GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{COLOR_ACCENT, COLOR_ACCENT_FADE}));
        root.addView(accentLine, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));

        mCurlView = new CurlView(this);
        root.addView(mCurlView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        View divider2 = new View(this);
        divider2.setBackgroundColor(COLOR_DIVIDER);
        root.addView(divider2, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        root.addView(buildBottomBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        // ---------------- side menu (drawer) on top of everything ----------------
        mFrame = new FrameLayout(this);
        // Mirroring for Urdu/Arabic is done by hand (below), so it works even when the
        // manifest lacks android:supportsRtl and cannot be double-flipped by the system.
        mFrame.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        mFrame.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mScrim = new View(this);
        mScrim.setBackgroundColor(0x99000000);
        mScrim.setVisibility(View.GONE);
        mScrim.setClickable(true);
        mScrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
            }
        });
        mFrame.addView(mScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int screenW = getResources().getDisplayMetrics().widthPixels;
        mDrawerWidth = Math.min(dp(DRAWER_WIDTH_DP), (int) (screenW * 0.68f));
        mDrawer = buildDrawer();
        mDrawer.setVisibility(View.GONE);
        mDrawer.setTranslationX(drawerHiddenX());
        mFrame.addView(mDrawer, new FrameLayout.LayoutParams(mDrawerWidth,
                ViewGroup.LayoutParams.MATCH_PARENT, RTL_UI ? Gravity.RIGHT : Gravity.LEFT));

        setContentView(mFrame);
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
    // Table of contents (assets/toc.txt)
    // ------------------------------------------------------------------
    // File format, one entry per line, UTF-8:   level|page|title
    //   optional first-line setting:  @title=Book name   (shown as the app title)
    //   level 1 = heading, 2 = sub heading, 3 = sub-sub heading
    //   page   = PDF page number (the number shown in the page box)
    //   lines starting with # are ignored
    private void loadToc() {
        mToc.clear();
        mBookTitle = APP_TITLE;
        BufferedReader reader = null;
        try {
            InputStream in = getAssets().open(TOC_ASSET_NAME);
            reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false;
                    if (line.length() > 0 && line.charAt(0) == '\uFEFF') {
                        line = line.substring(1);   // strip BOM added by some editors
                    }
                }
                line = line.trim();
                if (line.length() == 0 || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("@title=")) {
                    String t = line.substring(7).trim();
                    if (t.length() > 0) {
                        mBookTitle = t;
                    }
                    continue;
                }
                String[] p = line.split("\\|", 3);
                if (p.length < 3) {
                    continue;
                }
                try {
                    int level = Integer.parseInt(p[0].trim());
                    int page = Integer.parseInt(p[1].trim());
                    mToc.add(new TocEntry(Math.max(1, Math.min(level, 3)),
                            Math.max(1, page), p[2].trim()));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Bad toc line: " + line);
                }
            }
        } catch (Exception e) {
            Log.i(TAG, "No toc.txt loaded: " + e.getMessage());
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignored) {
            }
        }
        Collections.sort(mToc, new Comparator<TocEntry>() {
            @Override
            public int compare(TocEntry a, TocEntry b) {
                return a.page < b.page ? -1 : (a.page == b.page ? 0 : 1);
            }
        });
    }

    /** Index in mToc of the heading the reader is currently under, or -1. */
    private int currentTocIndex(int page1) {
        int result = -1;
        for (int i = 0; i < mToc.size(); i++) {
            if (mToc.get(i).page <= page1) {
                result = i;
            } else {
                break;
            }
        }
        return result;
    }

    private int currentPage1() {
        int count = mPageProvider != null ? mPageProvider.getPageCount() : 1;
        return Math.max(1, Math.min(mCurlView.getCurrentIndex() + 1, count));
    }

    // ------------------------------------------------------------------
    // Top bar
    // ------------------------------------------------------------------
    private GradientDrawable barBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BAR_TOP, COLOR_BAR_BOTTOM});
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(barBackground());
        if (RTL_UI) {
            bar.setPadding(dp(18), 0, dp(8), 0);
        } else {
            bar.setPadding(dp(8), 0, dp(18), 0);
        }

        // Menu (three lines)
        MenuIconView menu = new MenuIconView(this);
        menu.setContentDescription(STR_MENU);
        makePressable(menu);
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mDrawerOpen) {
                    closeDrawer();
                } else {
                    openDrawer();
                }
            }
        });
        LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        if (RTL_UI) {
            menuLp.setMargins(dp(6), 0, 0, 0);
        } else {
            menuLp.setMargins(0, 0, dp(6), 0);
        }

        // Accent marker
        View marker = new View(this);
        GradientDrawable markerBg = new GradientDrawable();
        markerBg.setColor(COLOR_ACCENT);
        markerBg.setCornerRadius(dp(2));
        marker.setBackground(markerBg);
        LinearLayout.LayoutParams markerLp = new LinearLayout.LayoutParams(dp(4), dp(30));
        if (RTL_UI) {
            markerLp.setMargins(dp(12), 0, 0, 0);
        } else {
            markerLp.setMargins(0, 0, dp(12), 0);
        }

        // Title + subtitle (current heading)
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL | endGravity());

        NastaliqLabel title = new NastaliqLabel(SIZE_TITLE, COLOR_TEXT);
        title.setText(mBookTitle);
        texts.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mSubtitleView = new NastaliqLabel(SIZE_SUBTITLE, COLOR_TEXT_DIM);
        texts.addView(mSubtitleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Fixed once, from the font: stays the same when the subtitle is hidden or empty,
        // so the book area never resizes while reading.
        mTopBarHeight = Math.max(dp(60), title.naturalHeight() + mSubtitleView.naturalHeight() + dp(14));

        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

        // Screen order, left to right:   RTL: [ title ][ marker ][ menu ]    LTR: [ menu ][ marker ][ title ]
        if (RTL_UI) {
            bar.addView(texts, textsLp);
            bar.addView(marker, markerLp);
            bar.addView(menu, menuLp);
        } else {
            bar.addView(menu, menuLp);
            bar.addView(marker, markerLp);
            bar.addView(texts, textsLp);
        }
        return bar;
    }

    private void updateSubtitle(int page1) {
        int idx = currentTocIndex(page1);
        String t = idx >= 0 ? mToc.get(idx).title : APP_SUBTITLE;
        mSubtitleView.setText(t);
        mSubtitleView.setVisibility(!mHideSubtitle && t.length() > 0 ? View.VISIBLE : View.GONE);
    }

    /** Three-line "hamburger" icon drawn by hand (no image files needed). */
    private class MenuIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        MenuIconView(Context c) {
            super(c);
            paint.setColor(COLOR_TEXT);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float lineH = dp(2.5f);
            float gap = dp(5.5f);
            float full = dp(22);
            float[] widths = {full, dp(15), dp(19)};
            float top = (h - (lineH * 3 + gap * 2)) / 2f;
            float edgeLeft = (w - full) / 2f;
            for (int i = 0; i < 3; i++) {
                float x = RTL_UI ? edgeLeft + full - widths[i] : edgeLeft;
                float y = top + i * (lineH + gap);
                canvas.drawRoundRect(x, y, x + widths[i], y + lineH, lineH / 2f, lineH / 2f, paint);
            }
        }
    }

    // ------------------------------------------------------------------
    // Bottom bar (page box)
    // ------------------------------------------------------------------
    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackground(barBackground());

        // Rounded "pill" holding  [ 7 ] / 11
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(COLOR_PILL);
        pillBg.setStroke(dp(1), COLOR_PILL_STROKE);
        pillBg.setCornerRadius(dp(20));
        pill.setBackground(pillBg);

        mPageInput = new EditText(this);
        mPageInput.setBackground(null);
        mPageInput.setTextColor(COLOR_ACCENT);
        mPageInput.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mPageInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
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
        mPageTotal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        mPageTotal.setPadding(dp(4), 0, 0, 0);
        pill.addView(mPageTotal, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        bar.addView(pill, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)));

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
                goToPage(Integer.parseInt(text));
            } catch (NumberFormatException e) {
                // ignore bad input
            }
        }
        clearInputFocus();
        updatePageIndicator(mCurlView.getCurrentIndex());
    }

    /** Jump to a 1-based page number. */
    private void goToPage(int page1) {
        int count = mPageProvider.getPageCount();
        int page = Math.max(1, Math.min(page1, count));
        mCurlView.setCurrentIndex(page - 1);
        saveIndex(page - 1);
        updatePageIndicator(page - 1);
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
        updateSubtitle(shown);
    }

    private void saveIndex(int index) {
        int last = Math.max(0, mPageProvider.getPageCount() - 1);
        int clamped = Math.max(0, Math.min(index, last));
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_PAGE, clamped).apply();
    }

    // ------------------------------------------------------------------
    // Side menu (drawer)
    // ------------------------------------------------------------------
    private float drawerHiddenX() {
        return RTL_UI ? mDrawerWidth : -mDrawerWidth;
    }

    private void openDrawer() {
        if (mDrawerOpen) {
            return;
        }
        mDrawerOpen = true;
        clearInputFocus();
        refreshTocHighlight();
        mScrim.setAlpha(0f);
        mScrim.setVisibility(View.VISIBLE);
        mScrim.animate().alpha(1f).setDuration(200);
        mDrawer.setVisibility(View.VISIBLE);
        mDrawer.animate().translationX(0f).setDuration(220);
    }

    private void closeDrawer() {
        if (!mDrawerOpen) {
            return;
        }
        mDrawerOpen = false;
        mScrim.animate().alpha(0f).setDuration(180).withEndAction(new Runnable() {
            @Override
            public void run() {
                mScrim.setVisibility(View.GONE);
            }
        });
        mDrawer.animate().translationX(drawerHiddenX()).setDuration(200)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mDrawer.setVisibility(View.GONE);
                    }
                });
    }

    @Override
    public void onBackPressed() {
        if (mDrawerOpen) {
            closeDrawer();
        } else {
            super.onBackPressed();
        }
    }

    private View buildDrawer() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(COLOR_DRAWER);
        panel.setClickable(true);   // swallow touches so they never reach the book underneath

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL | endGravity());
        header.setBackground(barBackground());
        header.setPadding(dp(20), 0, dp(20), 0);
        NastaliqLabel menuTitle = new NastaliqLabel(SIZE_MENU_HEADER, COLOR_TEXT);
        menuTitle.setText(STR_MENU);
        header.addView(menuTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));

        View accentLine = new View(this);
        accentLine.setBackground(new GradientDrawable(
                RTL_UI ? GradientDrawable.Orientation.RIGHT_LEFT : GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{COLOR_ACCENT, COLOR_ACCENT_FADE}));
        panel.addView(accentLine, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 1) Table of contents
        LinearLayout tocBody = new LinearLayout(this);
        tocBody.setOrientation(LinearLayout.VERTICAL);
        populateToc(tocBody);
        addSection(list, STR_TOC, tocBody, true);

        // 2) Settings
        LinearLayout settingsBody = new LinearLayout(this);
        settingsBody.setOrientation(LinearLayout.VERTICAL);
        settingsBody.addView(buildHideSubtitleRow(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        addSection(list, STR_SETTINGS, settingsBody, false);

        return panel;
    }

    private void addSection(LinearLayout list, String title, final View body, boolean expanded) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(20), dp(10), dp(20), dp(10));

        NastaliqLabel label = new NastaliqLabel(SIZE_SECTION, COLOR_TEXT);
        label.setText(title);
        final TextView arrow = text(expanded ? "\u25B2" : "\u25BC", 12, COLOR_ACCENT);
        addPair(head, endBox(label), arrow);
        makePressable(head);

        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        head.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean show = body.getVisibility() != View.VISIBLE;
                body.setVisibility(show ? View.VISIBLE : View.GONE);
                arrow.setText(show ? "\u25B2" : "\u25BC");
            }
        });

        list.addView(head, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        list.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        View line = new View(this);
        line.setBackgroundColor(COLOR_DIVIDER);
        list.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private void populateToc(LinearLayout body) {
        mTocTitleViews.clear();
        if (mToc.isEmpty()) {
            TextView empty = text(STR_NO_TOC, 16, COLOR_TEXT_DIM);
            if (mNastaliq != null) {
                empty.setTypeface(mNastaliq);
            }
            LinearLayout box = endBox(empty);
            box.setPadding(dp(20), dp(4), dp(20), dp(18));
            body.addView(box, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (final TocEntry e : mToc) {
            boolean top = e.level == 1;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int vPad = dp(top ? 6 : 4);
            int indent = dp(20 + (e.level - 1) * 18);   // indent on the side the text starts from
            if (RTL_UI) {
                row.setPadding(dp(20), vPad, indent, vPad);
            } else {
                row.setPadding(indent, vPad, dp(20), vPad);
            }

            NastaliqLabel title = new NastaliqLabel(top ? SIZE_TOC_1 : SIZE_TOC_2, top ? COLOR_TEXT : COLOR_TEXT_SUB);
            title.setText(e.title);
            TextView page = text(String.valueOf(e.page), 13, COLOR_TEXT_DIM);
            addPair(row, endBox(title), page);

            makePressable(row);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeDrawer();
                    goToPage(e.page);
                }
            });
            body.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            mTocTitleViews.add(title);
        }
    }

    /** Colour the heading the reader is currently under. */
    private void refreshTocHighlight() {
        int cur = currentTocIndex(currentPage1());
        for (int i = 0; i < mTocTitleViews.size() && i < mToc.size(); i++) {
            boolean top = mToc.get(i).level == 1;
            mTocTitleViews.get(i).setTextColor(i == cur ? COLOR_ACCENT
                    : (top ? COLOR_TEXT : COLOR_TEXT_SUB));
        }
    }

    private View buildHideSubtitleRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(8), dp(20), dp(12));

        NastaliqLabel label = new NastaliqLabel(SIZE_SETTING, COLOR_TEXT);
        label.setText(STR_HIDE_SUBTITLE);

        final ToggleView toggle = new ToggleView(this, mHideSubtitle);
        toggle.setOnChangeListener(new ToggleView.OnChangeListener() {
            @Override
            public void onChanged(boolean checked) {
                mHideSubtitle = checked;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(KEY_HIDE_SUBTITLE, checked).apply();
                updateSubtitle(currentPage1());
            }
        });
        addPair(row, endBox(label), toggle);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle.toggle();
            }
        });
        return row;
    }

    /** Side of the screen where text "starts": right for Urdu / Arabic. */
    private int endGravity() {
        return RTL_UI ? Gravity.RIGHT : Gravity.LEFT;
    }

    /** Wraps a view in a full-width box that pins it to the reading-start side. */
    private LinearLayout endBox(View child) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL | endGravity());
        box.addView(child, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /** Puts a text box on the reading side and a small item (arrow, number, switch) opposite. */
    private void addPair(LinearLayout row, View textBox, View other) {
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams otherLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (RTL_UI) {
            otherLp.setMargins(0, 0, dp(12), 0);
            row.addView(other, otherLp);
            row.addView(textBox, boxLp);
        } else {
            otherLp.setMargins(dp(12), 0, 0, 0);
            row.addView(textBox, boxLp);
            row.addView(other, otherLp);
        }
    }

    // ------------------------------------------------------------------
    // Nastaleeq label + on/off toggle
    // ------------------------------------------------------------------

    /**
     * Single-line label drawn with the Nastaleeq font.
     * Nastaleeq fonts have huge built-in line heights, which makes normal TextViews
     * tall and badly centred. This view measures the real ink height of typical
     * Urdu words once and sizes / centres itself from that, so it always looks even.
     * Numbers are NOT drawn with this view (they use the normal font).
     */
    private class NastaliqLabel extends View {
        private final String[] REF_WORDS = {"فہرست", "سیٹنگ", "ذیلی عنوان", "باب", "فصل", "حج", "کتاب", "گھر", "ٹھیک"};

        private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private String text = "";
        private int refTop;      // negative: above the baseline
        private int refBottom;   // positive: below the baseline

        NastaliqLabel(float sp, int color) {
            super(CurlActivity.this);
            paint.setColor(color);
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                    getResources().getDisplayMetrics()));
            if (mNastaliq != null) {
                paint.setTypeface(mNastaliq);
            }
            paint.setTextAlign(RTL_UI ? Paint.Align.RIGHT : Paint.Align.LEFT);

            Rect r = new Rect();
            refTop = 0;
            refBottom = 0;
            for (String w : REF_WORDS) {
                paint.getTextBounds(w, 0, w.length(), r);
                refTop = Math.min(refTop, r.top);
                refBottom = Math.max(refBottom, r.bottom);
            }
        }

        int naturalHeight() {
            return (refBottom - refTop) + dp(4);
        }

        void setText(String t) {
            text = t == null ? "" : t;
            requestLayout();
            invalidate();
        }

        void setTextColor(int color) {
            paint.setColor(color);
            invalidate();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int textW = (int) Math.ceil(paint.measureText(text)) + dp(2);
            int wMode = MeasureSpec.getMode(widthSpec);
            int wSize = MeasureSpec.getSize(widthSpec);
            int w = wMode == MeasureSpec.EXACTLY ? wSize
                    : (wMode == MeasureSpec.AT_MOST ? Math.min(textW, wSize) : textW);

            int natural = naturalHeight();
            int hMode = MeasureSpec.getMode(heightSpec);
            int hSize = MeasureSpec.getSize(heightSpec);
            int h = hMode == MeasureSpec.EXACTLY ? hSize
                    : (hMode == MeasureSpec.AT_MOST ? Math.min(natural, hSize) : natural);
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (text.length() == 0 || getWidth() <= 0) {
                return;
            }
            CharSequence shown = TextUtils.ellipsize(text, paint, getWidth(), TextUtils.TruncateAt.END);
            float baseline = getHeight() / 2f - (refTop + refBottom) / 2f;
            float x = RTL_UI ? getWidth() : 0f;
            canvas.drawText(shown, 0, shown.length(), x, baseline, paint);
        }
    }

    /** Modern on/off toggle (sliding thumb). In right-to-left mode "on" is the left position. */
    private static class ToggleView extends View {
        interface OnChangeListener {
            void onChanged(boolean checked);
        }

        private static final int TRACK_OFF = 0xFF3A4556;
        private static final int TRACK_ON = COLOR_ACCENT;
        private static final int THUMB_OFF = 0xFFC3CCD8;
        private static final int THUMB_ON = 0xFFFFFFFF;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float density;
        private boolean checked;
        private float pos;   // 0 = off, 1 = on
        private OnChangeListener listener;
        private ValueAnimator animator;

        ToggleView(Context c, boolean initial) {
            super(c);
            density = c.getResources().getDisplayMetrics().density;
            checked = initial;
            pos = initial ? 1f : 0f;
            setClickable(true);
            setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggle();
                }
            });
        }

        void setOnChangeListener(OnChangeListener l) {
            listener = l;
        }

        void toggle() {
            checked = !checked;
            if (animator != null) {
                animator.cancel();
            }
            animator = ValueAnimator.ofFloat(pos, checked ? 1f : 0f);
            animator.setDuration(160);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    pos = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            animator.start();
            if (listener != null) {
                listener.onChanged(checked);
            }
        }

        private int blend(int from, int to, float t) {
            int a = (int) (((from >>> 24) & 255) + ((((to >>> 24) & 255) - ((from >>> 24) & 255)) * t));
            int r = (int) (((from >> 16) & 255) + ((((to >> 16) & 255) - ((from >> 16) & 255)) * t));
            int g = (int) (((from >> 8) & 255) + ((((to >> 8) & 255) - ((from >> 8) & 255)) * t));
            int b = (int) ((from & 255) + (((to & 255) - (from & 255)) * t));
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension((int) (52 * density + 0.5f), (int) (32 * density + 0.5f));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float trackH = 26 * density;
            float top = (h - trackH) / 2f;
            rect.set(0, top, w, top + trackH);
            paint.setColor(blend(TRACK_OFF, TRACK_ON, pos));
            canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint);

            float pad = 3 * density;
            float r = trackH / 2f - pad;
            float offX = RTL_UI ? w - trackH / 2f : trackH / 2f;
            float onX = RTL_UI ? trackH / 2f : w - trackH / 2f;
            float cx = offX + (onX - offX) * pos;
            paint.setColor(blend(THUMB_OFF, THUMB_ON, pos));
            canvas.drawCircle(cx, h / 2f, r, paint);
        }
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------
    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (RTL_UI) {
            t.setTextDirection(View.TEXT_DIRECTION_RTL);
        }
        return t;
    }

    private void makePressable(View v) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(0x26FFFFFF));
        states.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        v.setBackground(states);
        v.setClickable(true);
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
