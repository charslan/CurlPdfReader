package fi.harism.curl;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

public class CurlMesh {

    private static final boolean DRAW_CURL_POSITION = false;
    private static final boolean DRAW_POLYGON_OUTLINES = false;
    private static final boolean DRAW_SHADOW = true;
    private static final boolean DRAW_TEXTURE = true;

    private static final float[] SHADOW_INNER_COLOR = {0.0f, 0.0f, 0.0f, 0.5f};
    private static final float[] SHADOW_OUTER_COLOR = {0.0f, 0.0f, 0.0f, 0.0f};

    private Array<ShadowVertex> mArrDropShadowVertices;
    private Array<Vertex> mArrIntersections;
    private Array<Vertex> mArrOutputVertices;
    private Array<Vertex> mArrRotatedVertices;
    private Array<Double> mArrScanLines;
    private Array<ShadowVertex> mArrSelfShadowVertices;
    private Array<ShadowVertex> mArrTempShadowVertices;
    private Array<Vertex> mArrTempVertices;

    private FloatBuffer mBufColors;
    private FloatBuffer mBufCurlPositionLines;
    private FloatBuffer mBufShadowColors;
    private FloatBuffer mBufShadowVertices;
    private FloatBuffer mBufTexCoords;
    private FloatBuffer mBufVertices;

    private int mCurlPositionLinesCount;
    private int mDropShadowCount;
    private int mMaxCurlSplits;
    private int mSelfShadowCount;
    private int mVerticesCountBack;
    private int mVerticesCountFront;

    private boolean mFlipTexture = false;
    private final Vertex[] mRectangle = new Vertex[4];
    private boolean mTextureBack = false;
    private int[] mTextureIds = null;
    private final CurlPage mTexturePage = new CurlPage();
    private final RectF mTextureRectBack = new RectF();
    private final RectF mTextureRectFront = new RectF();

    public CurlMesh(int maxCurlSplits) {
        this.mMaxCurlSplits = maxCurlSplits < 1 ? 1 : maxCurlSplits;

        this.mArrScanLines = new Array<>(maxCurlSplits + 2);
        this.mArrOutputVertices = new Array<>(7);
        this.mArrRotatedVertices = new Array<>(4);
        this.mArrIntersections = new Array<>(2);
        this.mArrTempVertices = new Array<>(11);

        for (int i = 0; i < 11; i++) {
            this.mArrTempVertices.add(new Vertex());
        }

        this.mArrSelfShadowVertices = new Array<>((this.mMaxCurlSplits + 2) * 2);
        this.mArrDropShadowVertices = new Array<>((this.mMaxCurlSplits + 2) * 2);
        this.mArrTempShadowVertices = new Array<>((this.mMaxCurlSplits + 2) * 2);

        for (int i = 0; i < (this.mMaxCurlSplits + 2) * 2; i++) {
            this.mArrTempShadowVertices.add(new ShadowVertex());
        }

        for (int i = 0; i < 4; i++) {
            this.mRectangle[i] = new Vertex();
        }

        // Penumbra setup for rectangle corners
        this.mRectangle[0].mPenumbraX = -1.0d;
        this.mRectangle[0].mPenumbraY =  1.0d;
        this.mRectangle[1].mPenumbraX = -1.0d;
        this.mRectangle[1].mPenumbraY = -1.0d;
        this.mRectangle[2].mPenumbraX =  1.0d;
        this.mRectangle[2].mPenumbraY =  1.0d;
        this.mRectangle[3].mPenumbraX =  1.0d;
        this.mRectangle[3].mPenumbraY = -1.0d;

        int maxVerticesCount = (this.mMaxCurlSplits * 2) + 6;

        ByteBuffer vbb = ByteBuffer.allocateDirect(maxVerticesCount * 3 * 4);
        vbb.order(ByteOrder.nativeOrder());
        this.mBufVertices = vbb.asFloatBuffer();
        this.mBufVertices.position(0);

        ByteBuffer tbb = ByteBuffer.allocateDirect(maxVerticesCount * 2 * 4);
        tbb.order(ByteOrder.nativeOrder());
        this.mBufTexCoords = tbb.asFloatBuffer();
        this.mBufTexCoords.position(0);

        ByteBuffer cbb = ByteBuffer.allocateDirect(maxVerticesCount * 4 * 4);
        cbb.order(ByteOrder.nativeOrder());
        this.mBufColors = cbb.asFloatBuffer();
        this.mBufColors.position(0);

        int maxShadowVerticesCount = (this.mMaxCurlSplits + 2) * 2 * 2;

        ByteBuffer scbb = ByteBuffer.allocateDirect(maxShadowVerticesCount * 4 * 4);
        scbb.order(ByteOrder.nativeOrder());
        this.mBufShadowColors = scbb.asFloatBuffer();
        this.mBufShadowColors.position(0);

        ByteBuffer sibb = ByteBuffer.allocateDirect(maxShadowVerticesCount * 3 * 4);
        sibb.order(ByteOrder.nativeOrder());
        this.mBufShadowVertices = sibb.asFloatBuffer();
        this.mBufShadowVertices.position(0);

        this.mSelfShadowCount = 0;
        this.mDropShadowCount = 0;
    }

    private void addVertex(Vertex vertex) {
        this.mBufVertices.put((float) vertex.mPosX);
        this.mBufVertices.put((float) vertex.mPosY);
        this.mBufVertices.put((float) vertex.mPosZ);

        this.mBufColors.put((vertex.mColorFactor * Color.red(vertex.mColor)) / 255.0f);
        this.mBufColors.put((vertex.mColorFactor * Color.green(vertex.mColor)) / 255.0f);
        this.mBufColors.put((vertex.mColorFactor * Color.blue(vertex.mColor)) / 255.0f);
        this.mBufColors.put(Color.alpha(vertex.mColor) / 255.0f);

        this.mBufTexCoords.put((float) vertex.mTexX);
        this.mBufTexCoords.put((float) vertex.mTexY);
    }

    public synchronized void curl(PointF curlPos, PointF curlDir, double radius) {
        this.mBufVertices.position(0);
        this.mBufColors.position(0);
        this.mBufTexCoords.position(0);

        double curlAngle = Math.acos(curlDir.x);
        if (curlDir.y > 0.0f) {
            curlAngle = -curlAngle;
        }

        this.mArrTempVertices.addAll(this.mArrRotatedVertices);
        this.mArrRotatedVertices.clear();

        for (int i = 0; i < 4; i++) {
            Vertex v = this.mArrTempVertices.remove(0);
            v.set(this.mRectangle[i]);
            v.translate(-curlPos.x, -curlPos.y);
            v.rotateZ(-curlAngle);

            int j;
            for (j = 0; j < this.mArrRotatedVertices.size(); j++) {
                Vertex v2 = this.mArrRotatedVertices.get(j);
                if (v.mPosX > v2.mPosX || (v.mPosX == v2.mPosX && v.mPosY > v2.mPosY)) {
                    break;
                }
            }
            this.mArrRotatedVertices.add(j, v);
        }

        int[][] lines = {
                {0, 1},
                {0, 2},
                {1, 3},
                {2, 3}
        };

        Vertex v0 = this.mArrRotatedVertices.get(0);
        Vertex v2 = this.mArrRotatedVertices.get(2);
        Vertex v3 = this.mArrRotatedVertices.get(3);

        double dist2 = Math.sqrt(
                (v0.mPosX - v2.mPosX) * (v0.mPosX - v2.mPosX) +
                (v0.mPosY - v2.mPosY) * (v0.mPosY - v2.mPosY));
        double dist3 = Math.sqrt(
                (v0.mPosX - v3.mPosX) * (v0.mPosX - v3.mPosX) +
                (v0.mPosY - v3.mPosY) * (v0.mPosY - v3.mPosY));

        if (dist2 > dist3) {
            lines[1][1] = 3;
            lines[2][1] = 2;
        }

        this.mVerticesCountBack = 0;
        this.mVerticesCountFront = 0;

        this.mArrTempShadowVertices.addAll(this.mArrDropShadowVertices);
        this.mArrTempShadowVertices.addAll(this.mArrSelfShadowVertices);
        this.mArrDropShadowVertices.clear();
        this.mArrSelfShadowVertices.clear();

        double curlLength = Math.PI * radius;

        this.mArrScanLines.clear();
        if (this.mMaxCurlSplits > 0) {
            this.mArrScanLines.add(0.0d);
        }
        for (int i = 1; i < this.mMaxCurlSplits; i++) {
            this.mArrScanLines.add(((-curlLength) * i) / (this.mMaxCurlSplits - 1));
        }
        this.mArrScanLines.add(this.mArrRotatedVertices.get(3).mPosX - 1.0d);

        double scanXmax = this.mArrRotatedVertices.get(0).mPosX + 1.0d;

        for (int i = 0; i < this.mArrScanLines.size(); i++) {
            double scanXmin = this.mArrScanLines.get(i);

            for (int j = 0; j < this.mArrRotatedVertices.size(); j++) {
                Vertex v = this.mArrRotatedVertices.get(j);
                if (v.mPosX >= scanXmin && v.mPosX <= scanXmax) {
                    Vertex n = this.mArrTempVertices.remove(0);
                    n.set(v);
                    Array<Vertex> intersections = getIntersections(this.mArrRotatedVertices, lines, n.mPosX);

                    if (intersections.size() == 1 && intersections.get(0).mPosY > v.mPosY) {
                        this.mArrOutputVertices.addAll(intersections);
                        this.mArrOutputVertices.add(n);
                    } else if (intersections.size() <= 1) {
                        this.mArrOutputVertices.add(n);
                        this.mArrOutputVertices.addAll(intersections);
                    } else {
                        this.mArrTempVertices.add(n);
                        this.mArrTempVertices.addAll(intersections);
                    }
                }
            }

            Array<Vertex> intersections = getIntersections(this.mArrRotatedVertices, lines, scanXmin);
            if (intersections.size() == 2) {
                Vertex v1 = intersections.get(0);
                Vertex v2b = intersections.get(1);
                if (v1.mPosY < v2b.mPosY) {
                    this.mArrOutputVertices.add(v2b);
                    this.mArrOutputVertices.add(v1);
                } else {
                    this.mArrOutputVertices.addAll(intersections);
                }
            } else if (intersections.size() != 0) {
                this.mArrTempVertices.addAll(intersections);
            }

            while (this.mArrOutputVertices.size() > 0) {
                Vertex v = this.mArrOutputVertices.remove(0);
                this.mArrTempVertices.add(v);

                boolean textureFront;
                if (i == 0) {
                    textureFront = true;
                    this.mVerticesCountFront++;
                } else if (i == this.mArrScanLines.size() - 1 || curlLength == 0.0d) {
                    v.mPosX = -(v.mPosX + curlLength);
                    v.mPosZ = 2.0d * radius;
                    v.mPenumbraX = -v.mPenumbraX;
                    textureFront = false;
                    this.mVerticesCountBack++;
                } else {
                    double rotY = Math.PI * (v.mPosX / curlLength);
                    v.mPosX = Math.sin(rotY) * radius;
                    v.mPosZ = radius - (Math.cos(rotY) * radius);
                    v.mPenumbraX *= Math.cos(rotY);
                    v.mColorFactor = (float) (0.1 + 0.9 * Math.sqrt(Math.sin(rotY) + 1.0));

                    if (v.mPosZ >= radius) {
                        textureFront = false;
                        this.mVerticesCountBack++;
                    } else {
                        textureFront = true;
                        this.mVerticesCountFront++;
                    }
                }

                if (textureFront != this.mFlipTexture) {
                    v.mTexX *= this.mTextureRectFront.right;
                    v.mTexY *= this.mTextureRectFront.bottom;
                    v.mColor = this.mTexturePage.getColor(CurlPage.SIDE_FRONT);
                } else {
                    v.mTexX *= this.mTextureRectBack.right;
                    v.mTexY *= this.mTextureRectBack.bottom;
                    v.mColor = this.mTexturePage.getColor(CurlPage.SIDE_BACK);
                }

                v.rotateZ(curlAngle);
                v.translate(curlPos.x, curlPos.y);
                addVertex(v);

                // Drop shadow
                if (v.mPosZ > 0.0d && v.mPosZ <= radius) {
                    ShadowVertex sv = this.mArrTempShadowVertices.remove(0);
                    sv.mPosX = v.mPosX;
                    sv.mPosY = v.mPosY;
                    sv.mPosZ = v.mPosZ;
                    sv.mPenumbraX = (v.mPosZ / 2.0d) * (-curlDir.x);
                    sv.mPenumbraY = (v.mPosZ / 2.0d) * (-curlDir.y);
                    sv.mPenumbraColor = v.mPosZ / radius;
                    int idx = (this.mArrDropShadowVertices.size() + 1) / 2;
                    this.mArrDropShadowVertices.add(idx, sv);
                }

                // Self shadow
                if (v.mPosZ > radius) {
                    ShadowVertex sv = this.mArrTempShadowVertices.remove(0);
                    sv.mPosX = v.mPosX;
                    sv.mPosY = v.mPosY;
                    sv.mPosZ = v.mPosZ;
                    sv.mPenumbraX = ((v.mPosZ - radius) / 3.0d) * v.mPenumbraX;
                    sv.mPenumbraY = ((v.mPosZ - radius) / 3.0d) * v.mPenumbraY;
                    sv.mPenumbraColor = (v.mPosZ - radius) / (2.0d * radius);
                    int idx = (this.mArrSelfShadowVertices.size() + 1) / 2;
                    this.mArrSelfShadowVertices.add(idx, sv);
                }
            }
            scanXmax = scanXmin;
        }

        this.mBufVertices.position(0);
        this.mBufColors.position(0);
        this.mBufTexCoords.position(0);
        this.mBufShadowColors.position(0);
        this.mBufShadowVertices.position(0);

        this.mDropShadowCount = 0;
        for (int i = 0; i < this.mArrDropShadowVertices.size(); i++) {
            ShadowVertex sv = this.mArrDropShadowVertices.get(i);
            this.mBufShadowVertices.put((float) sv.mPosX);
            this.mBufShadowVertices.put((float) sv.mPosY);
            this.mBufShadowVertices.put((float) sv.mPosZ);
            this.mBufShadowVertices.put((float) (sv.mPosX + sv.mPenumbraX));
            this.mBufShadowVertices.put((float) (sv.mPosY + sv.mPenumbraY));
            this.mBufShadowVertices.put((float) sv.mPosZ);

            for (int j = 0; j < 4; j++) {
                double color = SHADOW_OUTER_COLOR[j] +
                        ((SHADOW_INNER_COLOR[j] - SHADOW_OUTER_COLOR[j]) * sv.mPenumbraColor);
                this.mBufShadowColors.put((float) color);
            }
            this.mBufShadowColors.put(SHADOW_OUTER_COLOR[0]);
            this.mBufShadowColors.put(SHADOW_OUTER_COLOR[1]);
            this.mBufShadowColors.put(SHADOW_OUTER_COLOR[2]);
            this.mBufShadowColors.put(SHADOW_OUTER_COLOR[3]);
            this.mDropShadowCount += 2;
        }

        this.mSelfShadowCount = 0;
        for (int i = 0; i < this.mArrSelfShadowVertices.size(); i++) {
            ShadowVertex sv = this.mArrSelfShadowVertices.get(i);
            this.mBufShadowVertices.put((float) sv.mPosX);
            this.mBufShadowVertices.put((float) sv.mPosY);
            this.mBufShadowVertices.put((float) sv.mPosZ);
            this.mBufShadowVertices.put((float) (sv.mPosX + sv.mPenumbraX));
            this.mBufShadowVertices.put((float) (sv.mPosY + sv.mPenumbraY));
            this.mBufShadowVertices.put((float) sv.mPosZ);

            for (int j = 0; j < 4; j++) {
                double color = SHADOW_OUTER_COLOR[j] +
                        ((SHADOW_INNER_COLOR[j] - SHADOW_OUTER_COLOR[j]) * sv.mPenumbraColor);
                this.mBufShadowColors.put((float) color);
            }
            this.mBufShadowColors.put(SHADOW_OUTER_COLOR[0]);
            this.mBufShadowColors.put(SHADOW_OUTER_COLOR[1]);
            this.mBufShadowColors.put(SHADOW_OUTER_COLOR[2]);
            this.mBufShadowColors.put(SHADOW_OUTER_COLOR[3]);
            this.mSelfShadowCount += 2;
        }

        this.mBufShadowColors.position(0);
        this.mBufShadowVertices.position(0);
    }

    private Array<Vertex> getIntersections(Array<Vertex> vertices, int[][] lineIndices, double scanX) {
        this.mArrIntersections.clear();
        for (int j = 0; j < lineIndices.length; j++) {
            Vertex v1 = vertices.get(lineIndices[j][0]);
            Vertex v2 = vertices.get(lineIndices[j][1]);
            if (v1.mPosX > scanX && v2.mPosX < scanX) {
                double c = (scanX - v2.mPosX) / (v1.mPosX - v2.mPosX);
                Vertex n = this.mArrTempVertices.remove(0);
                n.set(v2);
                n.mPosX = scanX;
                n.mPosY += (v1.mPosY - v2.mPosY) * c;
                n.mTexX += (v1.mTexX - v2.mTexX) * c;
                n.mTexY += (v1.mTexY - v2.mTexY) * c;
                n.mPenumbraX += (v1.mPenumbraX - v2.mPenumbraX) * c;
                n.mPenumbraY += (v1.mPenumbraY - v2.mPenumbraY) * c;
                this.mArrIntersections.add(n);
            }
        }
        return this.mArrIntersections;
    }

    public synchronized CurlPage getTexturePage() {
        return this.mTexturePage;
    }

    public synchronized void onDrawFrame(GL10 gl) {
        if (this.mTextureIds == null) {
            this.mTextureIds = new int[2];
            gl.glGenTextures(2, this.mTextureIds, 0);
            for (int textureId : this.mTextureIds) {
                gl.glBindTexture(GL10.GL_TEXTURE_2D, textureId);
                gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
                gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
                gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
            }
        }

        if (this.mTexturePage.getTexturesChanged()) {
            gl.glBindTexture(GL10.GL_TEXTURE_2D, this.mTextureIds[0]);
            Bitmap texture = this.mTexturePage.getTexture(this.mTextureRectFront, CurlPage.SIDE_FRONT);
            GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, texture, 0);
            texture.recycle();

            this.mTextureBack = this.mTexturePage.hasBackTexture();
            if (this.mTextureBack) {
                gl.glBindTexture(GL10.GL_TEXTURE_2D, this.mTextureIds[1]);
                Bitmap texture2 = this.mTexturePage.getTexture(this.mTextureRectBack, CurlPage.SIDE_BACK);
                GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, texture2, 0);
                texture2.recycle();
            } else {
                this.mTextureRectBack.set(this.mTextureRectFront);
            }
            this.mTexturePage.recycle();
            reset();
        }

        // Draw drop shadow
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glDisable(GL10.GL_TEXTURE_2D);
        gl.glEnable(GL10.GL_BLEND);
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, this.mBufShadowColors);
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, this.mBufShadowVertices);
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, this.mDropShadowCount);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
        gl.glDisable(GL10.GL_BLEND);

        // Draw front + back geometry
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, this.mBufTexCoords);
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, this.mBufVertices);
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, this.mBufColors);

        gl.glDisable(GL10.GL_TEXTURE_2D);
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, this.mVerticesCountFront);

        gl.glEnable(GL10.GL_BLEND);
        gl.glEnable(GL10.GL_TEXTURE_2D);
        if (!this.mFlipTexture || !this.mTextureBack) {
            gl.glBindTexture(GL10.GL_TEXTURE_2D, this.mTextureIds[0]);
        } else {
            gl.glBindTexture(GL10.GL_TEXTURE_2D, this.mTextureIds[1]);
        }
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, this.mVerticesCountFront);
        gl.glDisable(GL10.GL_BLEND);
        gl.glDisable(GL10.GL_TEXTURE_2D);

        int backStartIdx = Math.max(0, this.mVerticesCountFront - 2);
        int backCount = (this.mVerticesCountFront + this.mVerticesCountBack) - backStartIdx;
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, backStartIdx, backCount);

        gl.glEnable(GL10.GL_BLEND);
        gl.glEnable(GL10.GL_TEXTURE_2D);
        if (this.mFlipTexture || !this.mTextureBack) {
            gl.glBindTexture(GL10.GL_TEXTURE_2D, this.mTextureIds[0]);
        } else {
            gl.glBindTexture(GL10.GL_TEXTURE_2D, this.mTextureIds[1]);
        }
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, backStartIdx, backCount);
        gl.glDisable(GL10.GL_BLEND);
        gl.glDisable(GL10.GL_TEXTURE_2D);

        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);

        // Self shadow
        gl.glEnable(GL10.GL_BLEND);
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, this.mBufShadowColors);
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, this.mBufShadowVertices);
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, this.mDropShadowCount, this.mSelfShadowCount);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
        gl.glDisable(GL10.GL_BLEND);
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
    }

    public synchronized void reset() {
        this.mBufVertices.position(0);
        this.mBufColors.position(0);
        this.mBufTexCoords.position(0);

        for (int i = 0; i < 4; i++) {
            Vertex tmp = this.mArrTempVertices.get(0);
            tmp.set(this.mRectangle[i]);
            if (this.mFlipTexture) {
                tmp.mTexX *= this.mTextureRectBack.right;
                tmp.mTexY *= this.mTextureRectBack.bottom;
                tmp.mColor = this.mTexturePage.getColor(CurlPage.SIDE_BACK);
            } else {
                tmp.mTexX *= this.mTextureRectFront.right;
                tmp.mTexY *= this.mTextureRectFront.bottom;
                tmp.mColor = this.mTexturePage.getColor(CurlPage.SIDE_FRONT);
            }
            addVertex(tmp);
        }

        this.mVerticesCountFront = 4;
        this.mVerticesCountBack = 0;
        this.mBufVertices.position(0);
        this.mBufColors.position(0);
        this.mBufTexCoords.position(0);
        this.mSelfShadowCount = 0;
        this.mDropShadowCount = 0;
    }

    public synchronized void resetTexture() {
        this.mTextureIds = null;
    }

    public synchronized void setFlipTexture(boolean flipTexture) {
        this.mFlipTexture = flipTexture;
        if (flipTexture) {
            setTexCoords(1.0f, 0.0f, 0.0f, 1.0f);
        } else {
            setTexCoords(0.0f, 0.0f, 1.0f, 1.0f);
        }
    }

    public void setRect(RectF r) {
        this.mRectangle[0].mPosX = r.left;
        this.mRectangle[0].mPosY = r.top;
        this.mRectangle[1].mPosX = r.left;
        this.mRectangle[1].mPosY = r.bottom;
        this.mRectangle[2].mPosX = r.right;
        this.mRectangle[2].mPosY = r.top;
        this.mRectangle[3].mPosX = r.right;
        this.mRectangle[3].mPosY = r.bottom;
    }

    private synchronized void setTexCoords(float left, float top, float right, float bottom) {
        this.mRectangle[0].mTexX = left;
        this.mRectangle[0].mTexY = top;
        this.mRectangle[1].mTexX = left;
        this.mRectangle[1].mTexY = bottom;
        this.mRectangle[2].mTexX = right;
        this.mRectangle[2].mTexY = top;
        this.mRectangle[3].mTexX = right;
        this.mRectangle[3].mTexY = bottom;
    }

    // -------------------------------------------------------------------------
    // Helper classes
    // -------------------------------------------------------------------------

    public static class Array<T> {
        private final Object[] mArray;
        private final int mCapacity;
        private int mSize;

        public Array(int capacity) {
            this.mCapacity = capacity;
            this.mArray = new Object[capacity];
        }

        public void add(int index, T item) {
            if (index < 0 || index > this.mSize || this.mSize >= this.mCapacity) {
                throw new IndexOutOfBoundsException();
            }
            for (int i = this.mSize; i > index; i--) {
                this.mArray[i] = this.mArray[i - 1];
            }
            this.mArray[index] = item;
            this.mSize++;
        }

        public void add(T item) {
            if (this.mSize >= this.mCapacity) {
                throw new IndexOutOfBoundsException();
            }
            this.mArray[this.mSize++] = item;
        }

        public void addAll(Array<T> array) {
            if (this.mSize + array.size() > this.mCapacity) {
                throw new IndexOutOfBoundsException();
            }
            for (int i = 0; i < array.size(); i++) {
                this.mArray[this.mSize++] = array.get(i);
            }
        }

        public void clear() {
            this.mSize = 0;
        }

        @SuppressWarnings("unchecked")
        public T get(int i) {
            if (i < 0 || i >= this.mSize) {
                throw new IndexOutOfBoundsException();
            }
            return (T) this.mArray[i];
        }

        @SuppressWarnings("unchecked")
        public T remove(int i) {
            if (i < 0 || i >= this.mSize) {
                throw new IndexOutOfBoundsException();
            }
            T t = (T) this.mArray[i];
            for (int j = i; j < this.mSize - 1; j++) {
                this.mArray[j] = this.mArray[j + 1];
            }
            this.mSize--;
            return t;
        }

        public int size() {
            return this.mSize;
        }
    }

    private static class ShadowVertex {
        public double mPenumbraColor;
        public double mPenumbraX;
        public double mPenumbraY;
        public double mPosX;
        public double mPosY;
        public double mPosZ;
    }

    private static class Vertex {
        public int mColor;
        public double mPenumbraX;
        public double mPenumbraY;
        public double mTexX = 0.0d;
        public double mTexY = 0.0d;
        public double mPosX = 0.0d;
        public double mPosY = 0.0d;
        public double mPosZ = 0.0d;
        public float mColorFactor = 1.0f;

        public void rotateZ(double theta) {
            double cos = Math.cos(theta);
            double sin = Math.sin(theta);
            double x = (this.mPosX * cos) + (this.mPosY * sin);
            double y = (this.mPosX * (-sin)) + (this.mPosY * cos);
            this.mPosX = x;
            this.mPosY = y;

            double px = (this.mPenumbraX * cos) + (this.mPenumbraY * sin);
            double py = (this.mPenumbraX * (-sin)) + (this.mPenumbraY * cos);
            this.mPenumbraX = px;
            this.mPenumbraY = py;
        }

        public void set(Vertex vertex) {
            this.mPosX = vertex.mPosX;
            this.mPosY = vertex.mPosY;
            this.mPosZ = vertex.mPosZ;
            this.mTexX = vertex.mTexX;
            this.mTexY = vertex.mTexY;
            this.mPenumbraX = vertex.mPenumbraX;
            this.mPenumbraY = vertex.mPenumbraY;
            this.mColor = vertex.mColor;
            this.mColorFactor = vertex.mColorFactor;
        }

        public void translate(double dx, double dy) {
            this.mPosX += dx;
            this.mPosY += dy;
        }
    }
}