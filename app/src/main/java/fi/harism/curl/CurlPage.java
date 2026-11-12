package fi.harism.curl;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public class CurlPage {

    public static final int SIDE_FRONT = 1;
    public static final int SIDE_BACK  = 2;
    public static final int SIDE_BOTH  = 3;

    private int mColorBack;
    private int mColorFront;
    private Bitmap mTextureBack;
    private Bitmap mTextureFront;
    private boolean mTexturesChanged;

    public CurlPage() {
        reset();
    }

    public int getColor(int side) {
        return (side == SIDE_FRONT) ? mColorFront : mColorBack;
    }

    private int getNextHighestPO2(int n) {
        n = n - 1;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        return n + 1;
    }

    private Bitmap getTexture(Bitmap bitmap, RectF textureRect) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int newW = getNextHighestPO2(w);
        int newH = getNextHighestPO2(h);

        Bitmap bitmapTex = Bitmap.createBitmap(newW, newH, bitmap.getConfig());
        Canvas c = new Canvas(bitmapTex);
        c.drawBitmap(bitmap, 0, 0, (Paint) null);

        textureRect.set(0, 0, (float) w / newW, (float) h / newH);
        return bitmapTex;
    }

    public Bitmap getTexture(RectF textureRect, int side) {
        if (side == SIDE_FRONT) {
            return getTexture(mTextureFront, textureRect);
        } else {
            return getTexture(mTextureBack, textureRect);
        }
    }

    public boolean getTexturesChanged() {
        return mTexturesChanged;
    }

    public boolean hasBackTexture() {
        return mTextureFront != mTextureBack;
    }

    public void recycle() {
        if (mTextureFront != null) {
            mTextureFront.recycle();
        }
        mTextureFront = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
        mTextureFront.eraseColor(mColorFront);

        if (mTextureBack != null) {
            mTextureBack.recycle();
        }
        mTextureBack = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
        mTextureBack.eraseColor(mColorBack);

        mTexturesChanged = false;
    }

    public void reset() {
        mColorBack = 0xFFFFFFFF;
        mColorFront = 0xFFFFFFFF;
        recycle();
    }

    public void setColor(int color, int side) {
        if ((side & SIDE_FRONT) != 0) {
            mColorFront = color;
        }
        if ((side & SIDE_BACK) != 0) {
            mColorBack = color;
        }
    }

    public void setTexture(Bitmap texture, int side) {
        if (texture == null) {
            texture = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
            if (side == SIDE_BACK) {
                texture.eraseColor(mColorBack);
            } else {
                texture.eraseColor(mColorFront);
            }
        }

        if ((side & SIDE_FRONT) != 0) {
            if (mTextureFront != null) {
                mTextureFront.recycle();
            }
            mTextureFront = texture;
        }
        if ((side & SIDE_BACK) != 0) {
            if (mTextureBack != null) {
                mTextureBack.recycle();
            }
            mTextureBack = texture;
        }
        mTexturesChanged = true;
    }
}