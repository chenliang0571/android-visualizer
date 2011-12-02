package com.pheelicks.visualizer;

// WARNING!!! This file has more magic numbers in it than you could shake a
// stick at

import java.util.Random;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.media.audiofx.Visualizer;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * A class that draws waveform data received from a
 * {@link Visualizer.OnDataCaptureListener#onWaveFormDataCapture }
 */
class VisualizerView extends View {
  private static final String TAG = "VisualizerView";

  private byte[] mBytes;
  private byte[] mFFTBytes;
  private float[] mPoints;
  private float[] mFFTPoints;
  private Rect mRect = new Rect();

  private Paint mCirclePaint = new Paint();
  private Paint mLinePaint = new Paint();
  private Paint mFFTLineTopPaint = new Paint();
  private Paint mFFTLineBottomPaint = new Paint();
  private Paint mSpecialLinePaint = new Paint();
  private Paint mProgressLinePaint = new Paint();
  private Paint mFlashPaint = new Paint();
  private Paint mFadePaint = new Paint();

  final int fft_divis_top = 8; // Set to some factor of 2 to adjust number of FFT bars
  final int fft_divis_bottom = 16; // Set to some factor of 2 to adjust number of FFT bars

  // Usual BS of 3 constructors
  public VisualizerView(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs);
    init();
  }

  public VisualizerView(Context context, AttributeSet attrs)
  {
    this(context, attrs, 0);
  }

  public VisualizerView(Context context)
  {
    this(context, null, 0);
  }

  private void init() {
    mBytes = null;

    mCirclePaint.setStrokeWidth(3f);
    mCirclePaint.setAntiAlias(true);
    mCirclePaint.setColor(Color.argb(255, 222, 92, 143));

    mLinePaint.setStrokeWidth(1f);
    mLinePaint.setAntiAlias(true);
    mLinePaint.setColor(Color.argb(88, 0, 128, 255));

    mSpecialLinePaint.setStrokeWidth(5f);
    mSpecialLinePaint.setAntiAlias(true);
    mSpecialLinePaint.setColor(Color.argb(188, 255, 255, 255));

    mProgressLinePaint.setStrokeWidth(4f);
    mProgressLinePaint.setAntiAlias(true);
    mProgressLinePaint.setColor(Color.argb(255, 22, 131, 255));

    mFFTLineTopPaint.setStrokeWidth(fft_divis_top * 3f);
    mFFTLineTopPaint.setAntiAlias(true);
    mFFTLineTopPaint.setColor(Color.argb(200, 233, 0, 44));

    mFFTLineBottomPaint.setStrokeWidth(fft_divis_bottom * 3f);
    mFFTLineBottomPaint.setAntiAlias(true);
    mFFTLineBottomPaint.setColor(Color.argb(88, 0, 233, 44));

    mFlashPaint.setColor(Color.argb(122, 255, 255, 255));

    mFadePaint.setColor(Color.argb(238, 255, 255, 255)); // Adjust alpha to change how quickly the image fades
    mFadePaint.setXfermode(new PorterDuffXfermode(Mode.MULTIPLY));
  }

  public void updateVisualizer(byte[] bytes) {
    mBytes = bytes;
    rotateColours();
    invalidate();
  }

  boolean mFlash = false;
  long mFlashTime = 0;
  long mFlashPeriod = 4000;

  public void flash() {
    mFlash = true;
    long now = SystemClock.currentThreadTimeMillis();
    mFlashPeriod = now - mFlashTime;
    mFlashTime = now;
    invalidate();
  }

  public void updateVisualizerFFT(byte[] bytes) {
    mFFTBytes = bytes;
    invalidate();
  }


  float colorCounter = 0;
  private void rotateColours()
  {
    int r = (int)Math.floor(128*(Math.sin(colorCounter) + 1));
    int g = (int)Math.floor(128*(Math.sin(colorCounter + 2) + 1));
    int b = (int)Math.floor(128*(Math.sin(colorCounter + 4) + 1));
    mLinePaint.setColor(Color.argb(128, r, g, b));
    mCirclePaint.setColor(Color.argb(255, g, b, r));
    colorCounter += 0.03;
  }

  Bitmap mCanvasBitmap;
  Canvas mCanvas;
  Random mRandom = new Random();
  float amplitude = 0;
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    if (mBytes == null) {
      return;
    }

    if (mPoints == null || mPoints.length < mBytes.length * 4) {
      mPoints = new float[mBytes.length * 4];
    }

    mRect.set(0, 0, getWidth(), getHeight());

    for (int i = 0; i < mBytes.length - 1; i++) {
      float[] cartPoint = {
          (float)i / (mBytes.length - 1),
          mRect.height() / 2 + ((byte) (mBytes[i] + 128)) * (mRect.height() / 2) / 128
      };

      float[] polarPoint = toPolar(cartPoint);
      mPoints[i * 4] = polarPoint[0];
      mPoints[i * 4 + 1] = polarPoint[1];

      float[] cartPoint2 = {
          (float)(i + 1) / (mBytes.length - 1),
          mRect.height() / 2 + ((byte) (mBytes[i + 1] + 128)) * (mRect.height() / 2) / 128
      };

      float[] polarPoint2 = toPolar(cartPoint2);
      mPoints[i * 4 + 2] = polarPoint2[0];
      mPoints[i * 4 + 3] = polarPoint2[1];
    }

    if(mCanvasBitmap == null)
    {
      mCanvasBitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Config.ARGB_8888);
    }
    if(mCanvas == null)
    {
      mCanvas = new Canvas(mCanvasBitmap);
    }

    mCanvas.drawLines(mPoints, mCirclePaint);


    // Draw normal line - offset by amplitude
    for (int i = 0; i < mBytes.length - 1; i++) {
      mPoints[i * 4] = mRect.width() * i / (mBytes.length - 1);
      mPoints[i * 4 + 1] =  mRect.height() / 2
          + ((byte) (mBytes[i] + 128)) * (mRect.height() / 3) / 128;
      mPoints[i * 4 + 2] = mRect.width() * (i + 1) / (mBytes.length - 1);
      mPoints[i * 4 + 3] = mRect.height() / 2
          + ((byte) (mBytes[i + 1] + 128)) * (mRect.height() / 3) / 128;
    }

    // Calc amplitude for this waveform
    float accumulator = 0;
    for (int i = 0; i < mBytes.length - 1; i++) {
      accumulator += Math.abs(mBytes[i]);
    }

    float amp = accumulator/(128 * mBytes.length);
    if(amp > amplitude)
    {
      amplitude = amp;
      // Occassionally, make a prominent line
      mCanvas.drawLines(mPoints, mSpecialLinePaint);
    }
    else
    {
      amplitude *= 0.99;
      mCanvas.drawLines(mPoints, mLinePaint);
    }

    // FFT time!!!!
    if (mFFTBytes == null) {
      return;
    }

    if (mFFTPoints == null || mFFTPoints.length < mBytes.length * 4) {
      mFFTPoints = new float[mFFTBytes.length * 4];
    }

    // Equalizer top
    if(mFFTBytes != null)
    {
      for (int i = 0; i < mFFTBytes.length / fft_divis_top; i++) {
        mFFTPoints[i * 4] = i * 4 * fft_divis_top;
        mFFTPoints[i * 4 + 1] = 0;
        mFFTPoints[i * 4 + 2] = i * 4 * fft_divis_top;
        byte rfk = mFFTBytes[fft_divis_top * i];
        byte ifk = mFFTBytes[fft_divis_top * i + 1];
        float magnitude = (rfk * rfk + ifk * ifk);
        int dbValue = (int) (10 * Math.log10(magnitude));
        mFFTPoints[i * 4 + 3] = (dbValue * 2 - 10);
      }

      mCanvas.drawLines(mFFTPoints, mFFTLineTopPaint);
    }

    // Equalizer bottom
    if(mFFTBytes != null)
    {
      for (int i = 0; i < mFFTBytes.length / fft_divis_bottom; i++) {
        mFFTPoints[i * 4] = i * 4 * fft_divis_bottom;
        mFFTPoints[i * 4 + 1] = mRect.height() - 2;
        mFFTPoints[i * 4 + 2] = i * 4 * fft_divis_bottom;
        byte rfk = mFFTBytes[fft_divis_bottom * i];
        byte ifk = mFFTBytes[fft_divis_bottom * i + 1];
        float magnitude = (rfk * rfk + ifk * ifk);
        int dbValue = (int) (10 * Math.log10(magnitude));
        mFFTPoints[i * 4 + 3] = mRect.height() - (dbValue * 4) - 2;
      }

      mCanvas.drawLines(mFFTPoints, mFFTLineBottomPaint);
    }

    // We totally need a thing moving along the bottom
    float cX = mRect.width()*(SystemClock.currentThreadTimeMillis() - mFlashTime)/mFlashPeriod;

    mCanvas.drawLine(cX - 35, mRect.height(), cX, mRect.height(), mProgressLinePaint);

    // Fade out old contents
    mCanvas.drawPaint(mFadePaint);

    if(mFlash)
    {
      mFlash = false;
      mCanvas.drawPaint(mFlashPaint);
    }

    canvas.drawBitmap(mCanvasBitmap, new Matrix(), null);
    modulation += 0.04;
  }

  float modulation = 0;
  float aggresive = 0.33f;
  private float[] toPolar(float[] cartesian)
  {
    double cX = mRect.width()/2;
    double cY = mRect.height()/2;
    double angle = (cartesian[0]) * 2 * Math.PI;
    double radius = ((mRect.width()/2) * (1 - aggresive) + aggresive * cartesian[1]/2) * (1.2 + Math.sin(modulation))/2.2;
    float[] out =  {
        (float)(cX + radius * Math.sin(angle)),
        (float)(cY + radius * Math.cos(angle))
    };
    return out;
  }


}