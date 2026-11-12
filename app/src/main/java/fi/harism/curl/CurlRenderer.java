package fi.harism.curl;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import java.util.Vector;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CurlRenderer implements GLSurfaceView.Renderer {

    public static final int PAGE_LEFT = 1;
    public static final int PAGE_RIGHT = 2;
    public static final int SHOW_ONE_PAGE = 1;
    public static final int SHOW_TWO_PAGES = 2;

    private static final boolean USE_PERSPECTIVE_PROJECTION = false;

    public static float zoom = 1.0f;
    public static float zoomX;
    public static float zoomY;

    private boolean mMirror = false;
    private int mBackgroundColor;
    private Observer mObserver;
    private int mViewportHeight;
    private int mViewportWidth;

    private RectF mMargins = new RectF();
    private int mViewMode = 1;
    private RectF mViewRect = new RectF();
    private Vector<CurlMesh> mCurlMeshes = new Vector<>();
    private RectF mPageRectLeft = new RectF();
    private RectF mPageRectRight = new RectF();

    public interface Observer {
        void onDrawFrame();
        void onPageSizeChanged(int width, int height);
        void onSurfaceCreated();
    }

    public CurlRenderer(Observer observer) {
        this.mObserver = observer;
    }

    public synchronized void addCurlMesh(CurlMesh mesh) {
        removeCurlMesh(mesh);
        this.mCurlMeshes.add(mesh);
    }

    public RectF getPageRect(int page) {
        if (page == 1) {
            return this.mPageRectLeft;
        }
        if (page == 2) {
            return this.mPageRectRight;
        }
        return null;
    }

    @Override
    public synchronized void onDrawFrame(GL10 gl) {
        this.mObserver.onDrawFrame();
        gl.glClearColor(
                Color.red(this.mBackgroundColor) / 255.0f,
                Color.green(this.mBackgroundColor) / 255.0f,
                Color.blue(this.mBackgroundColor) / 255.0f,
                Color.alpha(this.mBackgroundColor) / 255.0f);
        gl.glClear(16384); // GL_COLOR_BUFFER_BIT
        gl.glLoadIdentity();
        if (mMirror) {
            gl.glScalef(-1.0f, 1.0f, 1.0f);
        }
        gl.glScalef(zoom, zoom, zoom);
        gl.glTranslatef(
                (this.mViewRect.width() * zoomX) / this.mViewportWidth,
                (this.mViewRect.height() * zoomY) / this.mViewportHeight,
                0.0f);
        for (int i = 0; i < this.mCurlMeshes.size(); i++) {
            this.mCurlMeshes.get(i).onDrawFrame(gl);
        }
    }

    public void setMirror(boolean mirror) {
        mMirror = mirror;
    }

    public static void setZoomX(float x) {
        zoomX = x;
    }

    public static void setZoomY(float y) {
        zoomY = y;
    }

    public static void setZoom(float value) {
        zoom = value;
    }

    public static float scale(float valueIn, float baseMin, float baseMax, float limitMin, float limitMax) {
        return (((limitMax - limitMin) * (valueIn - baseMin)) / (baseMax - baseMin)) + limitMin;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        gl.glViewport(0, 0, width, height);
        this.mViewportWidth = width;
        this.mViewportHeight = height;
        float ratio = (float) width / height;
        this.mViewRect.top = 1.0f;
        this.mViewRect.bottom = -1.0f;
        this.mViewRect.left = -ratio;
        this.mViewRect.right = ratio;
        updatePageRects();
        gl.glMatrixMode(5889); // GL_PROJECTION
        gl.glLoadIdentity();
        GLU.gluOrtho2D(gl, this.mViewRect.left, this.mViewRect.right, this.mViewRect.bottom, this.mViewRect.top);
        gl.glMatrixMode(5888); // GL_MODELVIEW
        gl.glLoadIdentity();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        gl.glShadeModel(7425); // GL_SMOOTH
        gl.glHint(3152, 4354); // GL_PERSPECTIVE_CORRECTION_HINT, GL_NICEST
        gl.glHint(3154, 4354); // GL_LINE_SMOOTH_HINT
        gl.glHint(3155, 4354); // GL_POINT_SMOOTH_HINT
        gl.glEnable(2848);     // GL_LINE_SMOOTH
        gl.glDisable(2929);    // GL_DEPTH_TEST
        gl.glDisable(2884);    // GL_CULL_FACE
        this.mObserver.onSurfaceCreated();
    }

    public synchronized void removeCurlMesh(CurlMesh mesh) {
        do {
        } while (this.mCurlMeshes.remove(mesh));
    }

    public void setBackgroundColor(int color) {
        this.mBackgroundColor = color;
    }

    public synchronized void setMargins(float left, float top, float right, float bottom) {
        this.mMargins.left = left;
        this.mMargins.top = top;
        this.mMargins.right = right;
        this.mMargins.bottom = bottom;
        updatePageRects();
    }

    public synchronized void setViewMode(int viewmode) {
        if (viewmode == 1) {
            this.mViewMode = viewmode;
            updatePageRects();
        } else if (viewmode == 2) {
            this.mViewMode = viewmode;
            updatePageRects();
        }
    }

    public void translate(PointF pt) {
        pt.x = this.mViewRect.left + ((this.mViewRect.width() * pt.x) / this.mViewportWidth);
        pt.y = this.mViewRect.top - (((-this.mViewRect.height()) * pt.y) / this.mViewportHeight);
    }

    private void updatePageRects() {
        if (this.mViewRect.width() != 0.0f && this.mViewRect.height() != 0.0f) {
            if (this.mViewMode == 1) {
                this.mPageRectRight.set(this.mViewRect);
                this.mPageRectRight.left += this.mViewRect.width() * this.mMargins.left;
                this.mPageRectRight.right -= this.mViewRect.width() * this.mMargins.right;
                this.mPageRectRight.top += this.mViewRect.height() * this.mMargins.top;
                this.mPageRectRight.bottom -= this.mViewRect.height() * this.mMargins.bottom;
                this.mPageRectLeft.set(this.mPageRectRight);
                this.mPageRectLeft.offset(-this.mPageRectRight.width(), 0.0f);
                int bitmapW = (int) ((this.mPageRectRight.width() * this.mViewportWidth) / this.mViewRect.width());
                int bitmapH = (int) ((this.mPageRectRight.height() * this.mViewportHeight) / this.mViewRect.height());
                this.mObserver.onPageSizeChanged(bitmapW, bitmapH);
                return;
            }
            if (this.mViewMode == 2) {
                this.mPageRectRight.set(this.mViewRect);
                this.mPageRectRight.left += this.mViewRect.width() * this.mMargins.left;
                this.mPageRectRight.right -= this.mViewRect.width() * this.mMargins.right;
                this.mPageRectRight.top += this.mViewRect.height() * this.mMargins.top;
                this.mPageRectRight.bottom -= this.mViewRect.height() * this.mMargins.bottom;
                this.mPageRectLeft.set(this.mPageRectRight);
                this.mPageRectLeft.right = (this.mPageRectLeft.right + this.mPageRectLeft.left) / 2.0f;
                this.mPageRectRight.left = this.mPageRectLeft.right;
                int bitmapW2 = (int) ((this.mPageRectRight.width() * this.mViewportWidth) / this.mViewRect.width());
                int bitmapH2 = (int) ((this.mPageRectRight.height() * this.mViewportHeight) / this.mViewRect.height());
                this.mObserver.onPageSizeChanged(bitmapW2, bitmapH2);
            }
        }
    }
}