package fi.harism.curl;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class CurlActivity extends Activity {

    private static final String TAG = "CurlActivity";
    private static final String PDF_ASSET_NAME = "book.pdf";

    private CurlView mCurlView;
    private PdfPageProvider mPageProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mCurlView = new CurlView(this);
        setContentView(mCurlView);

        mPageProvider = new PdfPageProvider();
        mCurlView.setPageProvider(mPageProvider);

        mCurlView.setSizeChangedObserver(new CurlView.SizeChangedObserver() {
            @Override
            public void onSizeChanged(int w, int h) {
                if (w > h) {
                    mCurlView.setViewMode(CurlView.SHOW_TWO_PAGES);
                    mCurlView.setMargins(0.05f, 0.05f, 0.05f, 0.05f);
                } else {
                    mCurlView.setViewMode(CurlView.SHOW_ONE_PAGE);
                    mCurlView.setMargins(0.05f, 0.05f, 0.05f, 0.05f);
                }
            }
        });

        mCurlView.setCurrentIndex(0);
        mCurlView.setBackgroundColor(0xFF202830);
        mCurlView.setAllowLastPageCurl(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
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

        public PdfPageProvider() {
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

                fileDescriptor = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(fileDescriptor);
                pageCount = pdfRenderer.getPageCount();
                Log.i(TAG, "PDF loaded successfully. Pages: " + pageCount);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load PDF from assets", e);
                pageCount = 0;
            }
        }

        @Override
        public int getPageCount() {
            return pageCount > 0 ? pageCount : 1;
        }

        @Override
        public void updatePage(CurlPage page, int width, int height, int index) {
            if (pdfRenderer == null || index < 0 || index >= pageCount) {
                Bitmap blank = Bitmap.createBitmap(Math.max(width, 1), Math.max(height, 1), Bitmap.Config.ARGB_8888);
                blank.eraseColor(Color.WHITE);
                page.setTexture(blank, CurlPage.SIDE_BOTH);
                return;
            }

            PdfRenderer.Page pdfPage = null;
            try {
                pdfPage = pdfRenderer.openPage(index);
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

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

        public void close() {
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
