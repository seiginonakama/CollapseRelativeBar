CollapseRelativeBar
=============

CollapseRelativeBar继承于RelativeLayout，展开时的子view布局同RelativeLayout一致，折叠时的子view状态用自定义属性定义。折叠和展开过程中，CollapseRelativeBar可以自动处理子view的过渡动画。
<center>![animated gif demo](http://o7ilr4hyc.bkt.clouddn.com/collapse_relative_bar_demo.gif)</center>

引入库
------
```groovy
repositories {
    jcenter()
}
dependencies {
    compile 'me.touko:CollapseRelativeBarLib:0.9.0'
}
```

Usage
------
1. xml布局
CollapseRelativeBar可用自定义属性:
```xml
    <declare-styleable name="CollapseRelativeBar">
        <attr name="clBarHeight" format="dimension"/> <!-- 折叠时高度，可选，默认系统ActionBar高度 -->
        <attr name="clStatusBarScrim" format="reference|color"/> <!-- 折叠时状态栏遮罩，只在api 19及以上有效果，并且需要在设置android:windowTranslucentNavigation=true,android:windowTranslucentStatus=true -->
        <attr name="clAnimDuration" format="integer"/> <!-- 折叠动画时间长度，单位毫秒，可选，默认250毫秒 -->
    </declare-styleable>
```

展开时，子view完全按照RelativeLayout的布局方式布局。折叠时，子view的状态用自定义属性定义。相邻的滑动view需要定义app:layout_behavior="@string/CollapseRelativeBarScrollViewBehavior"
```xml

<android.support.design.widget.CoordinatorLayout
    ......>
    <me.touko.library.ui.CollapseRelativeBar
        android:layout_width="match_parent"
        android:layout_height="300dp"
        app:clBarHeight="50dp"
        app:clStatusBarScrim="@android:color/holo_blue_dark">

        ......

        <TextView
            展开状态下布局方式同RelativeLayout一样
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Hello"
            android:textSize="30sp"
            android:textColor="@android:color/holo_orange_light"
            android:layout_toLeftOf="@id/icon"
            android:layout_centerVertical="true"
            android:layout_marginRight="20dp"

            折叠状态下的布局用自定义属性定义
            app:clMarginLeft="20dp"
            app:clTextColor="@android:color/white"
            app:clMode="center"
            app:clScale="0.7"
            />

        ......
    </me.touko.library.ui.CollapseRelativeBar>

    <android.support.v4.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"

        滑动view需要设置app:layout_behavior
        app:layout_behavior="@string/CollapseRelativeBarScrollViewBehavior">
        ......
    </android.support.v4.widget.NestedScrollView>
</android.support.design.widget.CoordinatorLayout>
```

####注意：
CollapseRelativeBar依赖CoordinatorLayout，如果CollapseRelativeBar不是CoordinatorLayout的直接子view，将不会正常工作。

2. 子view可用自定义属性
```xml
    <declare-styleable name="CollapseLayout_LayoutParams">
        <attr name="clScaleX" format="float"/> <!-- 折叠时scaleX, 默认初始状态的scaleX -->
        <attr name="clScaleY" format="float"/> <!-- 折叠时scaleY, 默认初始状态的scaleY -->
        <attr name="clScale" format="float"/> <!-- 折叠时scale, 默认初始状态的scaleX -->
        <attr name="clWidth" format="dimension"/> <!-- 折叠时宽度, 默认初始状态的width -->
        <attr name="clHeight" format="dimension"/> <!-- 折叠时高度, 默认初始状态的height -->
        <attr name="clMarginLeft" format="dimension"/> <!-- 折叠时，相对左边界的margin -->
        <attr name="clMarginRight" format="dimension"/> <!-- 折叠时，相对右边界的margin -->
        <attr name="clMarginTop" format="dimension"/> <!-- 折叠时，相对于上边界的margin -->
        <attr name="clMarginBottom" format="dimension"/> <!-- 折叠时，相对于展开状态下边界的marigin -->
        <attr name="clAlpha" format="float"/> <!-- 折叠时的alpha， 默认初始状态的alpha -->
        <attr name="clScrim" format="reference|color"/> <!-- 折叠时的遮罩，默认空 -->
        <attr name="clTextColor" format="color"/> <!-- 折叠时TextView字体颜色，只对TextView有效 -->
        <attr name="clMode"> <!-- 默认折叠模式为center-->
            <flag name="center" value="0"/> <!-- 折叠时view竖直居中 -->
            <flag name="out" value="1"/> <!-- 折叠时view从上边界离开 -->
            <flag name="none" value="2"/> <!-- 折叠时view竖直位置只由clMarginBottom或clMarginTop决定 -->
        </attr>
        <attr name="clInterpolator" format="reference"/> <!-- 折叠时动画插值器 -->
        <attr name="clScaleXInterpolator" format="reference"/> <!-- 折叠时ScaleX动画插值器 -->
        <attr name="clScaleYInterpolator" format="reference"/> <!-- 折叠时ScaleY动画插值器 -->
    </declare-styleable>
```

3. API
```java
  /**
   * 展开CollapseRelativeBar
   *
   * @param duration 动画时长
   */
  public void runAutoExpand(long duration)

  /**
   * 折叠CollapseRelativeBar
   *
   * @param duration 动画时长
   */
  public void runAutoCollapse(long duration)

  /**
   * 是否已折叠
   *
   */
  public boolean isCollapsed()

  /**
   * 是否已展开
   *
   */
  public boolean isExpanded()

  /**
   * 增加折叠过程动画处理者，可完全自定义折叠动画
   *
   * @param collapseHandler 折叠过程动画处理者
   */
  public void addCollapseHandler(CollapseHandler collapseHandler)

  /**
   * 移除CollapseHandler
   *
   * @param collapseHandler 折叠过程动画处理者
   */
  public void removeCollapseHandler(CollapseHandler collapseHandler)

  /**
   * 折叠动画处理者，可以自定义折叠过程动画
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
```
