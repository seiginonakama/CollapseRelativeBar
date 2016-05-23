package me.touko.library.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.readystatesoftware.systembartint.SystemBarTintManager;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import me.touko.library.R;
import me.touko.library.utils.ReflectionUtils;

/**
 * CollapseRelativeBar extends RelativeLayout, you only need to define child collapsed state,
 * CollapseRelativeBar can auto handle the anim of child in expanding or collapsing process.
 * <p/>
 * This view depends heavily on being used as a direct child within a {@link CoordinatorLayout}.
 * If you use CollapseRelativeBar within a different {@link ViewGroup}, all of it's
 * functionality will
 * not work.
 * <p/>
 * CollapseRelativeBar also requires a separate scrolling sibling in order to know when to
 * scroll.
 * The binding is done through the {@link ScrollViewBehavior} behavior class, meaning that you
 * should set your scrolling view's behavior to be an instance of {@link ScrollViewBehavior}.
 * A string resource containing the full class name is available.
 */
@CoordinatorLayout.DefaultBehavior(CollapseRelativeBar.CollapseBehavior.class)
public class CollapseRelativeBar extends RelativeLayout {
  private static final int ACTION_NONE = 0;
  private static final int ACTION_EXPAND = 1;
  private static final int ACTION_COLLAPSE = 2;

  private static final float AUTO_EXPAND_PERCENT_THRESHOLD = 0.95f;
  private static final float AUTO_COLLAPSE_PERCENT_THRESHOLD = 0.05f;

  private static final long AUTO_ANIM_DEFAULT_DURATION = 250L;

  private static final int ANTI_SHAKE_THRESHOLD = 200;

  private int initHeight;
  private float prePercent;
  private int consumedY;
  private int preY;

  private AnimRunnable animRunnable = new AnimRunnable();

  private int currentAction = ACTION_NONE;

  private Drawable statusBarScrim;

  private final Set<CollapseHandler> collapseHandlers = new HashSet<>();

  private int COLLAPSED_HEIGHT;

  private final long AUTO_ANIM_DURATION;

  public CollapseRelativeBar(Context context) {
    this(context, null);
  }

  public CollapseRelativeBar(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public CollapseRelativeBar(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    TypedArray typedArray =
        context.obtainStyledAttributes(attrs, R.styleable.CollapseRelativeBar);

    TypedValue tv = new TypedValue();
    int actionBarHeight = 0;
    if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
      actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data,
          context.getResources().getDisplayMetrics());
    }
    COLLAPSED_HEIGHT = typedArray.getDimensionPixelSize(
        R.styleable.CollapseRelativeBar_clBarHeight, actionBarHeight);
    statusBarScrim =
        typedArray.getDrawable(R.styleable.CollapseRelativeBar_clStatusBarScrim);
    AUTO_ANIM_DURATION =
        typedArray.getInt(R.styleable.CollapseRelativeBar_clAnimDuration,
            (int) AUTO_ANIM_DEFAULT_DURATION);

    if (statusBarScrim != null) {
      if (context instanceof Activity) {
        SystemBarTintManager tintManager = new SystemBarTintManager((Activity) context);
        tintManager.setStatusBarTintEnabled(true);
        tintManager.setStatusBarTintDrawable(statusBarScrim);
      }
    }
    typedArray.recycle();
  }

  /**
   * 是否已折叠
   *
   */
  public boolean isCollapsed() {
    return getHeight() == COLLAPSED_HEIGHT;
  }

  /**
   * 是否已展开
   *
   */
  public boolean isExpanded() {
    return getHeight() == initHeight;
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    initHeight = 0;
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        initHeight = getLayoutParams().height;
        if (initHeight < COLLAPSED_HEIGHT) {
          throw new IllegalStateException("height can't < COLLAPSED_HEIGHT");
        }
        updateChildOriginState();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
          getViewTreeObserver().removeOnGlobalLayoutListener(this);
        } else {
          getViewTreeObserver().removeGlobalOnLayoutListener(this);
        }
      }
    });
  }

  /**
   * 展开CollapseRelativeBar
   *
   * @param duration 动画时长
   */
  public void runAutoExpand(long duration) {
    animRunnable.stop();

    int distance = initHeight - getHeight();
    int maxDistance = initHeight - COLLAPSED_HEIGHT;
    long animDuration = (long) (duration * ((float) distance / maxDistance));
    animRunnable.start(animDuration, distance, AnimationUtils.currentAnimationTimeMillis(),
        AnimRunnable.EXPAND_ACTION);
  }

  /**
   * 收拢CollapseRelativeBar
   *
   * @param duration 动画时长
   */
  public void runAutoCollapse(long duration) {
    animRunnable.stop();

    int distance = getHeight() - COLLAPSED_HEIGHT;
    int maxDistance = initHeight - COLLAPSED_HEIGHT;
    long animDuration = (long) (duration * ((float) distance / maxDistance));
    animRunnable.start(animDuration, distance, AnimationUtils.currentAnimationTimeMillis(),
        AnimRunnable.COLLAPSE_ACTION);
  }

  private int onTranslation(int y) {
    if (isShake(y)) {
      preY = y;
      return y;
    }

    if (animRunnable.isAnimating()) {
      animRunnable.stop();
    }

    // + preY for anti shake
    if (y + preY > 0) {
      currentAction = ACTION_COLLAPSE;
    } else if (y + preY < 0) {
      currentAction = ACTION_EXPAND;
    }

    preY = y;

    return doTranslation(y);
  }

  private int doTranslation(int y) {
    float percent = transitionHeightAndGetPercent(y);
    transitionChild(percent);
    requestLayout();
    prePercent = percent;
    return consumedY;
  }

  private boolean isShake(int y) {
    return y * preY < 0 && Math.abs((Math.abs(y) - Math.abs(preY))) < ANTI_SHAKE_THRESHOLD;
  }

  private void onStopNestedScroll() {
    switch (currentAction) {
      case ACTION_NONE:
        break;
      case ACTION_EXPAND:
        if (prePercent < AUTO_EXPAND_PERCENT_THRESHOLD) {
          runAutoExpand((long) (AUTO_ANIM_DURATION * (prePercent - 0)));
        }
        break;
      case ACTION_COLLAPSE:
        if (prePercent > AUTO_COLLAPSE_PERCENT_THRESHOLD) {
          runAutoCollapse((long) (AUTO_ANIM_DURATION * (1 - prePercent)));
        }
        break;
    }
  }

  private float transitionHeightAndGetPercent(int y) {
    ViewGroup.LayoutParams layoutParams = getLayoutParams();
    layoutParams.height -= y;
    if (layoutParams.height < COLLAPSED_HEIGHT) {
      consumedY = y - (COLLAPSED_HEIGHT - layoutParams.height);
      layoutParams.height = COLLAPSED_HEIGHT;
    } else if (layoutParams.height < 0) {
      consumedY = y - (0 - layoutParams.height);
      layoutParams.height = 0;
    } else if (layoutParams.height > initHeight) {
      consumedY = y + (layoutParams.height - initHeight);
      layoutParams.height = initHeight;
    } else {
      consumedY = y;
    }

    return (float) (initHeight - getLayoutParams().height)
        / (float) (initHeight - COLLAPSED_HEIGHT);
  }

  private void transitionChild(float percent) {
    int childCount = getChildCount();
    if (childCount <= 0) {
      return;
    }
    for (int i = 0; i < childCount; i++) {
      View child = getChildAt(i);
      if (letListenerHandle(child, percent)) {
        continue;
      }
      LayoutParams layoutParams = (LayoutParams) child.getLayoutParams();
      float childPercent = layoutParams.interpolator.getInterpolation(percent);
      float childScaleXPercent = layoutParams.scaleXInterpolator.getInterpolation(percent);
      float childScaleYPercent = layoutParams.scaleYInterpolator.getInterpolation(percent);

      ChildOriginState childOriginState = getChildOriginState(child);
      if (layoutParams.collapsedMarginBottom != LayoutParams.COLLAPSED_NO_TRANSLATION_Y) {
        transitionChildYForMarginBottom(child, layoutParams.collapsedMarginBottom,
            childPercent);
      } else if (layoutParams.collapsedMarginTop != LayoutParams.COLLAPSED_NO_TRANSLATION_Y) {
        transitionChildYForMarginTop(childOriginState, child, layoutParams.collapsedMarginTop,
            layoutParams.collapsedScaleY, childPercent);
      } else {
        switch (layoutParams.collapsedMode) {
          case LayoutParams.COLLAPSED_MODE_PIN:
            transitionChildYForPin(childOriginState, child, childPercent);
            break;
          case LayoutParams.COLLAPSED_MODE_OUT:
            transitionChildYForOut(childOriginState, child, layoutParams.collapsedScaleY,
                childPercent);
            break;
          case LayoutParams.COLLAPSED_MODE_NONE:
            break;
        }
      }
      if (layoutParams.collapsedMarginRight != LayoutParams.COLLAPSED_NO_TRANSLATION_X) {
        transitionChildXRight(childOriginState, child, layoutParams.collapsedMarginRight,
            layoutParams.collapsedScaleX, childPercent);
      } else if (layoutParams.collapsedMarginLeft != LayoutParams.COLLAPSED_NO_TRANSLATION_X) {
        transitionChildXLeft(childOriginState, child, layoutParams.collapsedMarginLeft,
            layoutParams.collapsedScaleX, childPercent);
      }
      if (layoutParams.collapsedWidth >= 0) {
        transitionChildWidth(childOriginState, child, layoutParams.collapsedWidth, childPercent);
      }
      if (layoutParams.collapsedHeight >= 0) {
        transitionChildHeight(childOriginState, child, layoutParams.collapsedHeight, childPercent);
      }
      if (layoutParams.collapsedScaleX != LayoutParams.COLLAPSED_NO_SCALE) {
        transitionChildScaleX(childOriginState, child, layoutParams.collapsedScaleX,
            childScaleXPercent);
      }
      if (layoutParams.collapsedScaleY != LayoutParams.COLLAPSED_NO_SCALE) {
        transitionChildScaleY(childOriginState, child, layoutParams.collapsedScaleY,
            childScaleYPercent);
      }
      if (layoutParams.collapsedAlpha != LayoutParams.COLLAPSED_NO_ALPHA) {
        transitionChildAlpha(childOriginState, child, layoutParams.collapsedAlpha, childPercent);
      }
      if (child instanceof TextView) {
        if (layoutParams.collapsedTextColor != null) {
          transitionChildTextColor(childOriginState, (TextView) child,
              layoutParams.collapsedTextColor, childPercent);
        }
      }
      notifyAfterTransition(child, percent);
    }
  }

  private void transitionChildYForPin(ChildOriginState childOriginState, View child,
                                      float percent) {
    float targetMarginTop =
        COLLAPSED_HEIGHT / 2 - childOriginState.height / 2 - getPaddingTop();
    float totalYDistance = childOriginState.top - targetMarginTop;
    ViewCompat.setTranslationY(child, -totalYDistance * percent);
  }

  private void transitionChildYForOut(ChildOriginState childOriginState, View child,
                                      float targetScaleY,
                                      float percent) {
    float targetMarginTop =
        -childOriginState.height - getScaleDelta(childOriginState.height, targetScaleY) / 2;
    float totalYDistance = childOriginState.top - targetMarginTop;
    ViewCompat.setTranslationY(child, -totalYDistance * percent);
  }

  private void transitionChildXLeft(ChildOriginState childOriginState, View child,
                                    int targetMarginLeft, float targetScaleX, float percent) {
    int totalXDistance = (int) (childOriginState.left - getPaddingLeft() - targetMarginLeft
        - getScaleDelta(childOriginState.width, targetScaleX) / 2);
    ViewCompat.setTranslationX(child, -totalXDistance * percent);
  }

  private void transitionChildYForMarginTop(ChildOriginState childOriginState, View child,
                                            int targetMarginTop,
                                            float targetScaleY,
                                            float percent) {
    float totalYDistance = childOriginState.top - (targetMarginTop + getPaddingTop()
        + getScaleDelta(childOriginState.height, targetScaleY) / 2);
    ViewCompat.setTranslationY(child, -totalYDistance * percent);
  }

  private void transitionChildXRight(ChildOriginState childOriginState, View child,
                                     int targetMarginRight, float targetScaleX, float percent) {
    int totalXDistance =
        (int) (getWidth() - getPaddingRight() - targetMarginRight - childOriginState.right
            - getScaleDelta(childOriginState.width, targetScaleX) / 2);
    ViewCompat.setTranslationX(child, totalXDistance * percent);
  }

  private void transitionChildYForMarginBottom(View child,
                                               int targetMarginBottom, float percent) {
    ViewCompat.setTranslationY(child, -targetMarginBottom * percent);
  }

  private void transitionChildWidth(ChildOriginState childOriginState, View child,
                                    int targetWidth, float percent) {
    LayoutParams layoutParams = (LayoutParams) child.getLayoutParams();
    layoutParams.width =
        (int) getPointBetweenTwoValue(childOriginState.width, targetWidth, percent);
  }

  private void transitionChildHeight(ChildOriginState childOriginState, View child,
                                     int targetHeight, float percent) {
    LayoutParams layoutParams = (LayoutParams) child.getLayoutParams();
    layoutParams.height =
        (int) getPointBetweenTwoValue(childOriginState.height, targetHeight, percent);
  }

  private void transitionChildScaleX(ChildOriginState childOriginState, View child,
                                     float targetScaleX, float percent) {
    ViewCompat.setScaleX(child,
        getPointBetweenTwoValue(childOriginState.scaleX, targetScaleX, percent));
  }

  private void transitionChildScaleY(ChildOriginState childOriginState, View child,
                                     float targetScaleY, float percent) {
    ViewCompat.setScaleY(child,
        getPointBetweenTwoValue(childOriginState.scaleY, targetScaleY, percent));
  }

  private void transitionChildAlpha(ChildOriginState childOriginState, View child,
                                    float targetAlpha, float percent) {
    ViewCompat.setAlpha(child,
        getPointBetweenTwoValue(childOriginState.alpha, targetAlpha, percent));
  }

  private void transitionChildTextColor(ChildOriginState childOriginState, TextView child,
                                        int[] targetTextColor, float percent) {
    child.setTextColor(Color.argb(
        (int) getPointBetweenTwoValue(childOriginState.textColor[0], targetTextColor[0], percent),
        (int) getPointBetweenTwoValue(childOriginState.textColor[1], targetTextColor[1], percent),
        (int) getPointBetweenTwoValue(childOriginState.textColor[2], targetTextColor[2], percent),
        (int) getPointBetweenTwoValue(childOriginState.textColor[3], targetTextColor[3], percent)));
  }

  private float getPointBetweenTwoValue(float point1, float point2, float percent) {
    float distance = Math.abs(point1 - point2);
    if (point1 < point2) {
      return point1 + distance * percent;
    } else {
      return point1 - distance * percent;
    }
  }

  private float getScaleDelta(float originValue, float scale) {
    return originValue * scale - originValue;
  }

  @Override
  public RelativeLayout.LayoutParams generateLayoutParams(AttributeSet attributeSet) {
    return new LayoutParams(getContext(), attributeSet);
  }

  private ChildOriginState getChildOriginState(View child) {
    ChildOriginState childOriginState =
        (ChildOriginState) child.getTag(R.id.collapse_layout_item_origin_state);
    if (childOriginState == null) {
      childOriginState = new ChildOriginState(child);
      child.setTag(R.id.collapse_layout_item_origin_state, childOriginState);
    }
    return childOriginState;
  }

  private void updateChildOriginState() {
    for (int i = 0, z = getChildCount(); i < z; i++) {
      View child = getChildAt(i);
      ChildOriginState childOriginState = (ChildOriginState) child.getTag(R.id.collapse_layout_item_origin_state);
      if (childOriginState == null) {
        child.setTag(R.id.collapse_layout_item_origin_state, new ChildOriginState(child));
      } else {
        childOriginState.update(child);
      }
    }
  }

  private boolean resetChildBorder() {
    for (int i = 0, z = getChildCount(); i < z; i++) {
      View child = getChildAt(i);
      ChildOriginState childOriginState = getChildOriginState(child);
      if (!childOriginState.canResetBorder) {
        return false;
      }
      RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) child.getLayoutParams();
      try {
        childOriginState.mLeft.setInt(layoutParams, childOriginState.left);
        childOriginState.mTop.setInt(layoutParams, childOriginState.top);
        childOriginState.mRight.setInt(layoutParams, childOriginState.right);
        childOriginState.mBottom.setInt(layoutParams, childOriginState.bottom);
      } catch (IllegalAccessException e) {
        return false;
      }
    }
    return true;
  }

  /**
   * 增加CollapseHandler
   *
   * @param collapseHandler 折叠过程动画处理者
   */
  public void addCollapseHandler(CollapseHandler collapseHandler) {
    collapseHandlers.add(collapseHandler);
  }

  /**
   * 移除CollapseHandler
   *
   * @param collapseHandler 折叠过程动画处理者
   */
  public void removeCollapseHandler(CollapseHandler collapseHandler) {
    collapseHandlers.remove(collapseHandler);
  }

  private boolean letListenerHandle(View child, float percent) {
    if (collapseHandlers.isEmpty()) {
      return false;
    }
    for (CollapseHandler observer : collapseHandlers) {
      if (observer.onCollapseTransition(this, child, percent)) {
        return true;
      }
    }
    return false;
  }

  private void notifyAfterTransition(View child, float percent) {
    if (collapseHandlers.isEmpty()) {
      return;
    }
    for (CollapseHandler observer : collapseHandlers) {
      observer.afterCollapseTransition(this, child, percent);
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    // relativeLayout only layout child when expanded
    if (isExpanded() || !ViewCompat.isLaidOut(this)) {
      super.onLayout(changed, l, t, r, b);
      updateChildOriginState();
    } else if (resetChildBorder()) {
      super.onLayout(changed, l, t, r, b);
    }
  }

  @Override
  protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
    // first drawing the child...
    boolean bool = super.drawChild(canvas, child, drawingTime);

    // then draw scrim
    LayoutParams layoutParams = (LayoutParams) child.getLayoutParams();
    if (layoutParams.collapsedScrim != null) {
      layoutParams.collapsedScrim.setBounds(
          (int) (child.getLeft() + ViewCompat.getTranslationX(child)),
          (int) (child.getTop() + ViewCompat.getTranslationY(child)),
          (int) (child.getRight() + ViewCompat.getTranslationX(child)),
          (int) (child.getBottom() + ViewCompat.getTranslationY(child)));
      layoutParams.collapsedScrim.mutate().setAlpha((int) (255 * prePercent));
      layoutParams.collapsedScrim.draw(canvas);
    }

    if (statusBarScrim != null) {
      statusBarScrim.mutate().setAlpha((int) (255 * prePercent));
    }
    return bool;
  }

  public static class LayoutParams extends RelativeLayout.LayoutParams {
    public static final int COLLAPSED_NO_SCALE = 1;
    public static final int COLLAPSED_NO_TRANSLATION_X = -99887766;
    public static final int COLLAPSED_NO_TRANSLATION_Y = -99887765;
    public static final float COLLAPSED_NO_ALPHA = -1;
    private static final int COLLAPSED_TEXT_COLOR_NO_CHANGE = 938271202;

    public static final int COLLAPSED_MODE_PIN = 0;
    public static final int COLLAPSED_MODE_OUT = 1;
    public static final int COLLAPSED_MODE_NONE = 2;

    private int collapsedMode = COLLAPSED_MODE_PIN;

    private float collapsedScale = COLLAPSED_NO_SCALE;
    private float collapsedScaleX = COLLAPSED_NO_SCALE;
    private float collapsedScaleY = COLLAPSED_NO_SCALE;

    private int collapsedWidth = -1;
    private int collapsedHeight = -1;

    private float collapsedAlpha = COLLAPSED_NO_ALPHA;

    private int collapsedMarginLeft = COLLAPSED_NO_TRANSLATION_X;
    private int collapsedMarginRight = COLLAPSED_NO_TRANSLATION_X;

    private int collapsedMarginTop = COLLAPSED_NO_TRANSLATION_Y;
    private int collapsedMarginBottom = COLLAPSED_NO_TRANSLATION_Y;

    private int collapsedTextColor[] = null;

    private Drawable collapsedScrim = null;

    private Interpolator interpolator;
    private Interpolator scaleXInterpolator;
    private Interpolator scaleYInterpolator;

    public LayoutParams(Context c, AttributeSet attrs) {
      super(c, attrs);
      TypedArray typedArray =
          c.obtainStyledAttributes(attrs, R.styleable.CollapseLayout_LayoutParams);

      collapsedMode = typedArray.getInt(R.styleable.CollapseLayout_LayoutParams_clMode,
          COLLAPSED_MODE_PIN);
      collapsedScale = typedArray.getFloat(R.styleable.CollapseLayout_LayoutParams_clScale,
          COLLAPSED_NO_SCALE);
      if (collapsedScale != COLLAPSED_NO_SCALE) {
        collapsedScaleX = collapsedScale;
        collapsedScaleY = collapsedScale;
      } else {
        collapsedScaleX =
            typedArray.getFloat(R.styleable.CollapseLayout_LayoutParams_clScaleX,
                COLLAPSED_NO_SCALE);
        collapsedScaleY =
            typedArray.getFloat(R.styleable.CollapseLayout_LayoutParams_clScaleY,
                COLLAPSED_NO_SCALE);
      }

      collapsedWidth =
          typedArray.getDimensionPixelOffset(R.styleable.CollapseLayout_LayoutParams_clWidth, -1);
      collapsedHeight =
          typedArray.getDimensionPixelSize(R.styleable.CollapseLayout_LayoutParams_clHeight, -1);

      collapsedMarginLeft = typedArray.getDimensionPixelSize(
          R.styleable.CollapseLayout_LayoutParams_clMarginLeft,
          COLLAPSED_NO_TRANSLATION_X);
      collapsedMarginRight = typedArray.getDimensionPixelSize(
          R.styleable.CollapseLayout_LayoutParams_clMarginRight,
          COLLAPSED_NO_TRANSLATION_X);
      collapsedMarginTop = typedArray.getDimensionPixelSize(
          R.styleable.CollapseLayout_LayoutParams_clMarginTop, COLLAPSED_NO_TRANSLATION_Y);
      collapsedMarginBottom = typedArray.getDimensionPixelSize(
          R.styleable.CollapseLayout_LayoutParams_clMarginBottom,
          COLLAPSED_NO_TRANSLATION_Y);
      collapsedScrim =
          typedArray.getDrawable(R.styleable.CollapseLayout_LayoutParams_clScrim);
      collapsedAlpha = typedArray.getFloat(R.styleable.CollapseLayout_LayoutParams_clAlpha,
          COLLAPSED_NO_ALPHA);
      int textColor = typedArray.getColor(
          R.styleable.CollapseLayout_LayoutParams_clTextColor,
          COLLAPSED_TEXT_COLOR_NO_CHANGE);
      if (textColor != COLLAPSED_TEXT_COLOR_NO_CHANGE) {
        collapsedTextColor = new int[]{Color.alpha(textColor), Color.red(textColor),
            Color.green(textColor), Color.blue(textColor)};
      }

      Interpolator defaultInterpolator = new LinearInterpolator();

      int interpolatorId = typedArray
          .getResourceId(R.styleable.CollapseLayout_LayoutParams_clInterpolator, -1);
      if (interpolatorId != -1) {
        interpolator = AnimationUtils.loadInterpolator(c, interpolatorId);
      } else {
        interpolator = defaultInterpolator;
      }

      int scaleXInterpolatorId = typedArray
          .getResourceId(R.styleable.CollapseLayout_LayoutParams_clScaleXInterpolator, -1);
      if (scaleXInterpolatorId != -1) {
        scaleXInterpolator = AnimationUtils.loadInterpolator(c, interpolatorId);
      } else {
        scaleXInterpolator = defaultInterpolator;
      }

      int scaleYInterpolatorId = typedArray
          .getResourceId(R.styleable.CollapseLayout_LayoutParams_clScaleYInterpolator, -1);
      if (scaleYInterpolatorId != -1) {
        scaleYInterpolator = AnimationUtils.loadInterpolator(c, interpolatorId);
      } else {
        scaleYInterpolator = defaultInterpolator;
      }

      typedArray.recycle();
    }

    public LayoutParams(int width, int height) {
      super(width, height);
    }

    public LayoutParams(ViewGroup.LayoutParams p) {
      super(p);
    }

    public LayoutParams(MarginLayoutParams source) {
      super(source);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public LayoutParams(LinearLayout.LayoutParams source) {
      super(source);
    }
  }

  private static class ChildOriginState {
    public int top;
    public int left;
    public int right;
    public int bottom;
    public int width;
    public int height;
    public float alpha;
    public float scaleX;
    public float scaleY;

    public float textSize;
    public int[] textColor;

    public Field mLeft;
    public Field mTop;
    public Field mRight;
    public Field mBottom;

    private boolean canResetBorder;

    public ChildOriginState(View child) {
      try {
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) child.getLayoutParams();
        mLeft = ReflectionUtils.getOriginalField(layoutParams, "mLeft");
        mTop = ReflectionUtils.getOriginalField(layoutParams, "mTop");
        mRight = ReflectionUtils.getOriginalField(layoutParams, "mRight");
        mBottom = ReflectionUtils.getOriginalField(layoutParams, "mBottom");
      } catch (NoSuchFieldException e) {
        e.printStackTrace();
      }
      update(child);
    }

    public void update(View child) {
      RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) child.getLayoutParams();

      canResetBorder = false;

      if (mLeft != null
          && mTop != null
          && mRight != null
          && mBottom != null) {
        try {
          left = mLeft.getInt(layoutParams);
          top = mTop.getInt(layoutParams);
          right = mRight.getInt(layoutParams);
          bottom = mBottom.getInt(layoutParams);
          canResetBorder = true;
        } catch (IllegalAccessException ignored) {
        }
      }

      if (!canResetBorder) {
        top = child.getTop();
        left = child.getLeft();
        right = child.getRight();
        bottom = child.getBottom();
      }

      width = child.getWidth();
      height = child.getHeight();
      alpha = ViewCompat.getAlpha(child);
      scaleX = ViewCompat.getScaleX(child);
      scaleY = ViewCompat.getScaleY(child);

      if (child instanceof TextView) {
        TextView textView = (TextView) child;
        textSize = textView.getTextSize();
        int color = textView.getTextColors().getDefaultColor();
        textColor =
            new int[]{Color.alpha(color), Color.red(color), Color.green(color), Color.blue(color)};
      }
    }
  }

  private class AnimRunnable implements Runnable {
    private long duration;
    private int totalDistance;
    protected long preTime;
    private int action;
    private boolean isAnimating = false;

    public static final int COLLAPSE_ACTION = 0;
    public static final int EXPAND_ACTION = 1;

    protected int getCurrentFragmentDistance() {
      return (int) (totalDistance * ((float) (AnimationUtils.currentAnimationTimeMillis() - preTime) / duration));
    }

    @Override
    public void run() {
      doTranslation(
          action == COLLAPSE_ACTION ? getCurrentFragmentDistance() : -getCurrentFragmentDistance());
      updatePreTime();
      if (action == COLLAPSE_ACTION ? !isCollapsed() : !isExpanded()) {
        post(this);
      } else {
        isAnimating = false;
      }
    }

    public void start(long duration, int totalDistance, long startTime, int action) {
      this.duration = duration;
      this.totalDistance = totalDistance;
      this.action = action;
      if (action != COLLAPSE_ACTION && action != EXPAND_ACTION) {
        throw new IllegalArgumentException("no this action:" + action);
      }
      preTime = startTime;
      isAnimating = true;
      postDelayed(this, AnimationUtils.currentAnimationTimeMillis() - startTime);
    }

    public void stop() {
      removeCallbacks(this);
      isAnimating = false;
    }

    public boolean isAnimating() {
      return isAnimating;
    }

    protected void updatePreTime() {
      preTime = AnimationUtils.currentAnimationTimeMillis();
    }
  }

  /**
   * CollapseHandler可以完全自定义折叠过程动画
   */
  public interface CollapseHandler {
    /**
     * 折叠过程回调，处理折叠动画
     *
     * @param parent  CollapseRelativeBar
     * @param child   需处理的子view
     * @param percent 目前折叠进度的百分比
     * @return 如果return true, CollapseRelativeBar会认为你已经处理了折叠变换，不会自动对child进行折叠处理。
     */
    boolean onCollapseTransition(CollapseRelativeBar parent, View child, float percent);

    /**
     * 监听折叠过程
     *
     * @param parent  CollapseRelativeBar
     * @param child   处理后的子view
     * @param percent 目前折叠进度的百分比
     */
    void afterCollapseTransition(CollapseRelativeBar parent, View child, float percent);
  }

  /**
   * author: zhou date: 2016/3/3.
   */
  public static class CollapseBehavior
      extends CoordinatorLayout.Behavior<CollapseRelativeBar> {

    @Override
    public boolean onStartNestedScroll(CoordinatorLayout coordinatorLayout,
                                       CollapseRelativeBar child, View directTargetChild, View target,
                                       int nestedScrollAxes) {
      return nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL;
    }

    @Override
    public void onNestedPreScroll(CoordinatorLayout coordinatorLayout,
                                  CollapseRelativeBar child,
                                  View target,
                                  int dx, int dy, int[] consumed) {
      if (!child.isCollapsed()) {
        consumed[1] = child.onTranslation(dy);
      }
    }

    @Override
    public void onNestedScroll(CoordinatorLayout coordinatorLayout, CollapseRelativeBar child,
                               View target,
                               int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
      if (dyUnconsumed != 0) {
        child.onTranslation(dyUnconsumed);
      }
    }

    @Override
    public void onStopNestedScroll(CoordinatorLayout coordinatorLayout,
                                   CollapseRelativeBar child,
                                   View target) {
      child.onStopNestedScroll();
    }

    @Override
    public boolean onNestedPreFling(CoordinatorLayout coordinatorLayout,
                                    CollapseRelativeBar child, View target,
                                    float velocityX, float velocityY) {
      return !child.isCollapsed();
    }
  }

  public static class ScrollViewBehavior extends CoordinatorLayout.Behavior<View> {
    private CollapseRelativeBar dependParent;

    public ScrollViewBehavior(Context context, AttributeSet attributeSet) {
      super(context, attributeSet);
    }

    @Override
    public boolean onLayoutChild(CoordinatorLayout parent, View child, int layoutDirection) {
      if (dependParent != null) {
        if (!dependParent.isCollapsed()) {
          parent.onLayoutChild(child, layoutDirection);
          ViewCompat.offsetTopAndBottom(child, dependParent.getHeight());
        } else {
          if (child.getTop() != dependParent.getBottom()) {
            ViewCompat.offsetTopAndBottom(child, 0);
            ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
            MarginLayoutParams marginLayoutParams = null;
            if (layoutParams instanceof MarginLayoutParams) {
              marginLayoutParams = (MarginLayoutParams)layoutParams;
            } else {
              marginLayoutParams = new MarginLayoutParams(layoutParams);
            }
            child.layout(parent.getLeft() + marginLayoutParams.leftMargin,
                dependParent.getBottom() + marginLayoutParams.topMargin,
                parent.getRight() - marginLayoutParams.rightMargin,
                parent.getBottom() - marginLayoutParams.bottomMargin);
          }
        }
      }
      return true;
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, View child, View dependency) {
      boolean isDepend = false;
      if (dependParent == null && dependency instanceof CollapseRelativeBar) {
        dependParent = (CollapseRelativeBar) dependency;
        isDepend = true;
      }
      return isDepend;
    }
  }
}
