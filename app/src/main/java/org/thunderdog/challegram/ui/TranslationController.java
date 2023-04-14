package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.navigation.TelegramViewController;
import org.thunderdog.challegram.navigation.ToggleHeaderView2;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.TranslationCounterDrawable;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.LickView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.TextView;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;

public class TranslationController extends TelegramViewController<TranslationController.Args> implements PopupLayout.TouchSectionProvider, PopupLayout.PopupHeightProvider, Menu {
  private final ReplaceAnimator<TextWrapper> text = new ReplaceAnimator<>(this::updateTexts, AnimatorUtils.DECELERATE_INTERPOLATOR, 300L);

  private PopupLayout popupLayout;
  private boolean openLaunched;

  private final TranslationCounterDrawable translationCounterDrawable;
  private FrameLayoutFix wrapView;
  private MessageTextView messageTextView;
  private ComplexReceiver textMediaReceiver;
  private ToggleHeaderView2 headerCell;
  private CustomRecyclerView recyclerView;

  private TGMessage.TranslationsManager mTranslationsManager;
  private TGMessage messageToTranslate;
  private TdApi.FormattedText originalText;
  private String messageOriginalLanguage;
  private LickView lickView;

  public TranslationController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    translationCounterDrawable = new TranslationCounterDrawable(Drawables.get(R.drawable.baseline_translate_24));
    translationCounterDrawable.setColors(R.id.theme_color_icon, R.id.theme_color_background ,R.id.theme_color_iconActive);
  }

  @Override
  protected View onCreateView (Context context) {
    wrapView = new FrameLayoutFix(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        measureText(width - Screen.dp(36));

        int textHeight = getTextStaticHeight();
        LayoutParams layoutParams = (LayoutParams) recyclerView.getLayoutParams();
        layoutParams.topMargin = Screen.dp(67 - 6) + HeaderView.getTopOffset();
        layoutParams.bottomMargin = Screen.dp(48 - 6);
        int scrollViewHeight = height - layoutParams.bottomMargin - layoutParams.topMargin - Screen.dp(12);

        lickView.setVisibility(textHeight >= scrollViewHeight ? VISIBLE: GONE);
        recyclerView.setPadding(0, Math.max(scrollViewHeight / 3, scrollViewHeight - textHeight), 0, 0);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      }

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateLayout();
      }

      @Override
      protected void dispatchDraw (Canvas canvas) {
        float top = headerView != null ? headerView.getTranslationY(): 0;
        canvas.drawRect(0, top, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.getColor(R.id.theme_color_filling)));

        super.dispatchDraw(canvas);
      }
    };

    translationCounterDrawable.setInvalidateCallback(this::updateAnimations);

    lickView = new LickView(context);
    lickView.setHeaderBackground(Theme.getColor(R.id.theme_color_filling));
    lickView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, HeaderView.getTopOffset()));
    wrapView.addView(lickView);

    headerView = new HeaderView(context);
    headerCell = new ToggleHeaderView2(context);
    headerCell.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(67f), Gravity.TOP, Screen.dp(56), 0, Screen.dp(60), 0));
    headerCell.setTitle(Lang.getLanguageName(messageOriginalLanguage, Lang.getString(R.string.TranslateLangUnknown)), false);
    headerCell.setSubtitle(Lang.getString(R.string.TranslateOriginal), false);
    headerCell.setOnClickListener(v -> showTranslateOptions());
    headerCell.setTranslationY(Screen.dp(7.5f));

    headerView.initWithSingleController(this, false);
    headerView.getFilling().setShadowAlpha(0f);
    headerView.getBackButton().setIsReverse(true);
    headerView.setBackgroundHeight(Screen.dp(67));

    AvatarView senderAvatarView = new AvatarView(context);
    senderAvatarView.setMessageSender(tdlib, messageToTranslate.getSender().toSender());
    wrapView.addView(senderAvatarView, FrameLayoutFix.newParams(Screen.dp(20), Screen.dp(20), Gravity.LEFT | Gravity.BOTTOM, Screen.dp(18), 0, 0, Screen.dp(16)));

    TextView senderTextView = new TextView(context);
    senderTextView.setText(messageToTranslate.getSender().getName());
    senderTextView.setTextColor(Theme.getColor(R.id.theme_color_textLight));
    senderTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
    senderTextView.setTypeface(Fonts.getRobotoMedium());
    wrapView.addView(senderTextView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, Screen.dp(44), 0, 0, Screen.dp(19)));

    TextView dateTextView = new TextView(context);
    dateTextView.setText(Lang.dateYearShortTime(messageToTranslate.getComparingDate(), TimeUnit.SECONDS));
    dateTextView.setTextColor(Theme.getColor(R.id.theme_color_textLight));
    dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
    wrapView.addView(dateTextView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, Screen.dp(18), Screen.dp(19)));

    tdlib.ui().post(this::launchOpenAnimation);

    messageTextView = new MessageTextView(context);
    textMediaReceiver = new ComplexReceiver(messageTextView);

    recyclerView = new CustomRecyclerView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent e) {
        return !(e.getAction() == MotionEvent.ACTION_DOWN && headerView != null && e.getY() < headerView.getTranslationY() - HeaderView.getSize(true)) && super.onTouchEvent(e);
      }
    };
    recyclerView.setClipToPadding(false);
    recyclerView.setVerticalScrollBarEnabled(false);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    recyclerView.setAdapter(new RecyclerView.Adapter<>() {
      @NonNull
      @Override
      public RecyclerView.ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
        return new RecyclerView.ViewHolder(messageTextView) {

        };
      }

      @Override
      public void onBindViewHolder (@NonNull RecyclerView.ViewHolder holder, int position) {

      }

      @Override
      public int getItemCount () {
        return 1;
      }
    });
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        super.onScrollStateChanged(recyclerView, newState);
        if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
        if (lickView.getVisibility() != View.VISIBLE) return;
        if (lickView.getFactor() == 0f || lickView.getFactor() == 1f) return;

        LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (manager == null) return;

        recyclerView.stopScroll();
        int firstVisiblePosition = manager.findFirstVisibleItemPosition();
        if (firstVisiblePosition == RecyclerView.NO_POSITION) {
          return;
        }
        int scrollTop = 0;
        View view = manager.findViewByPosition(firstVisiblePosition);
        if (view != null) {
          scrollTop -= view.getTop();
        }
        recyclerView.smoothScrollBy(0, -scrollTop);
      }

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);
        updateLayout();
      }
    });
    // recyclerView.addView(messageTextView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    wrapView.addView(recyclerView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM));
    wrapView.addView(headerView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(67f)));

    addThemeInvalidateListener(lickView);
    addThemeInvalidateListener(headerView);
    addThemeInvalidateListener(headerCell);
    addThemeInvalidateListener(wrapView);
    addThemeInvalidateListener(messageTextView);

    text.replace(makeTextWrapper(originalText), false);
    return wrapView;
  }

  HeaderButton translationHeaderButton;

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    lickView.setHeaderBackground(Theme.getColor(R.id.theme_color_filling));
    if (headerView != null) {
      headerView.resetColors(this, null);
    }
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_done;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (headerView == null) return;

    translationHeaderButton =  headerView.addButton(menu, R.id.menu_done, getHeaderIconColorId(), this, 0, Screen.dp(60), R.drawable.bg_btn_header);
    translationHeaderButton.setCustomDrawable(translationCounterDrawable);
    headerView.getBackButton().setTranslationY(Screen.dp(7.5f));
    translationHeaderButton.setTranslationY(Screen.dp(7.5f));
  }

  public void onMenuItemPressed (int id, View view) {
    if (id != R.id.menu_done) return;
    if (mTranslationsManager.getCurrentTranslatedLanguage() != null) {
      mTranslationsManager.stopTranslation();
    } else {
      mTranslationsManager.requestTranslation(mTranslationsManager.getLastTranslatedLanguage());
    }
  }

  private void showTranslateOptions () {
    int y = (int) MathUtils.clamp(headerView != null ? headerView.getTranslationY(): 0, 0, wrapView.getMeasuredHeight() - Screen.dp(280 + 16));

    LanguageSelectorPopup languagePopupLayout = new LanguageSelectorPopup(context, mTranslationsManager::requestTranslation, mTranslationsManager.getCurrentTranslatedLanguage(), messageOriginalLanguage);
    languagePopupLayout.languageRecyclerWrap.setTranslationY(y);
    languagePopupLayout.show();
  }

  private void updateTexts (ReplaceAnimator animator) {
    messageTextView.invalidate();
    if (messageTextView.getMeasuredHeight() != getTextStaticHeight()) {
      messageTextView.requestLayout();
    }
    updateLayout();
  }

  private void updateAnimations () {
    messageTextView.invalidate();
    wrapView.invalidate();
    if (translationHeaderButton != null) {
      translationHeaderButton.invalidate();
    }
  }

  private void updateLayout () {
    if (headerView == null) {
      return;
    }

    int addTop = Math.min(recyclerView.getMeasuredHeight(), getTextStaticHeight()) - Math.min(recyclerView.getMeasuredHeight(), getTextAnimatedHeight());
    int y = Math.max(0, messageTextView.getTop() - recyclerView.getScrollY()) + HeaderView.getTopOffset() + addTop;
    headerView.setTranslationY(y);

    if (lickView != null) {
      final int topOffset = HeaderView.getTopOffset();
      final float top = y - topOffset;
      lickView.setTranslationY(top);
      float factor = top > topOffset ? 0f : 1f - ((float) top / (float) topOffset);
      lickView.setFactor(factor);
    }

    wrapView.invalidate();
  }



  private int currentTextWidth = -1;

  private void measureText (int width) {
    currentTextWidth = width;
    for (ListAnimator.Entry<TextWrapper> entry: text) {
      entry.item.prepare(width);
      entry.item.requestMedia(textMediaReceiver, 0, Integer.MAX_VALUE);
    }
  }

  private int getTextAnimatedHeight () {
    float height = 0;
    for (ListAnimator.Entry<TextWrapper> entry: text) {
      height += entry.item.getHeight() * entry.getVisibility();
    }
    return (int) height;
  }

  private int getTextStaticHeight () {
    int height = 0;
    for (ListAnimator.Entry<TextWrapper> entry: text) {
      if (entry.getVisibility() > 0f || entry.isAffectingList()) {
        height = Math.max(height, entry.item.getHeight());
      }
    }
    return height;
  }

  private TextWrapper makeTextWrapper (TdApi.FormattedText formattedText) {
    TextWrapper textWrapper = new TextWrapper(formattedText.text, TGMessage.getTextStyleProvider(), messageToTranslate.getTextColorSet())
      .setEntities(TextEntity.valueOf(tdlib, formattedText, null), (wrapper, text, specificMedia) -> messageTextView.invalidate())
      .setClickCallback(messageToTranslate.clickCallback())
      .addTextFlags(Text.FLAG_BIG_EMOJI);

    if (currentTextWidth > 0) {
      textWrapper.prepare(currentTextWidth);
    }

    return textWrapper;
  }

  /**/

  private void setTranslatedStatus (int status, boolean animated) {
    translationCounterDrawable.setStatus(status, animated);
    if (status == TranslationCounterDrawable.TRANSLATE_STATUS_DEFAULT) {
      headerCell.setTitle(Lang.getLanguageName(messageOriginalLanguage, Lang.getString(R.string.TranslateLangUnknown)), animated);
      headerCell.setSubtitle(Lang.getString(R.string.TranslateOriginal), animated);
    } else if (status == TranslationCounterDrawable.TRANSLATE_STATUS_SUCCESS) {
      headerCell.setTitle(Lang.getLanguageName(mTranslationsManager.getCurrentTranslatedLanguage(), Lang.getString(R.string.TranslateLangUnknown)), animated);
      headerCell.setSubtitle(Lang.getString(R.string.TranslatedFrom, Lang.getLanguageName(messageOriginalLanguage, Lang.getString(R.string.TranslateLangUnknown))), animated);
    } else if (status == TranslationCounterDrawable.TRANSLATE_STATUS_LOADING) {
      headerCell.setTitle(Lang.getString(R.string.TranslatingTo, Lang.getLanguageName(mTranslationsManager.getCurrentTranslatedLanguage(), Lang.getString(R.string.TranslateLangUnknown))), animated);
      headerCell.setSubtitle(Lang.getString(R.string.TranslateWait), animated);
    } else if (status == TranslationCounterDrawable.TRANSLATE_STATUS_ERROR) {
      headerCell.setTitle(Lang.getString(R.string.TranslationFailed), animated);
      headerCell.setSubtitle(Lang.getString(R.string.TranslateTryAgain), animated);
    }
  }

  private void setTranslationResult (TdApi.FormattedText translated) {
    TdApi.FormattedText textToSet = translated != null ? translated : originalText;
    text.replace(makeTextWrapper(textToSet), true);
    messageTextView.requestLayout();
  }

  /**/

  public void show () {
    if (tdlib == null) {
      return;
    }
    popupLayout = new PopupLayout(context());
    popupLayout.setBoundController(this);
    popupLayout.setPopupHeightProvider(this);
    popupLayout.init(true);
    popupLayout.setTouchProvider(this);
    popupLayout.setTouchDownInterceptor(this::onBackgroundTouchDown);

    if (originalText == null) {
      destroy();
      return;
    }

    getValue();

    mTranslationsManager.requestTranslation(Lang.getDefaultLanguageToTranslate());

    context().addFullScreenView(this, false);
  }

  protected void launchOpenAnimation () {
    if (!openLaunched) {
      openLaunched = true;
      popupLayout.showSimplePopupView(getValue(), Screen.currentHeight());
    }
  }

  @Override
  public boolean shouldTouchOutside (float x, float y) {
    return headerView != null && y < headerView.getTranslationY() - HeaderView.getSize(true);
  }

  private boolean onBackgroundTouchDown (PopupLayout popupLayout, MotionEvent e) {
    float y = e.getY();
    float y2 = (headerView != null ? headerView.getTranslationY(): 0); // - HeaderView.getTopOffset();
    return y > y2;
  }

  @Override
  public int getCurrentPopupHeight () {
    return wrapView.getMeasuredHeight();
  }

  /**/

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  public int getId () {
    return R.id.controller_msgTranslate;
  }

  @Override
  protected int getHeaderTextColorId () {
    return R.id.theme_color_text;
  }

  @Override
  protected int getHeaderColorId () {
    return R.id.theme_color_filling;
  }

  @Override
  protected int getHeaderIconColorId () {
    return R.id.theme_color_icon;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    messageToTranslate = args.message;
    originalText = args.message.getTextToTranslate();
    messageOriginalLanguage = args.message.getOriginalMessageLanguage();
    mTranslationsManager = new TGMessage.TranslationsManager(messageToTranslate, this::setTranslatedStatus, this::setTranslationResult);
  }

  public static class Args {
    private final TGMessage message;
    public Args(TGMessage message) {
      this.message = message;
    }
  }

  /**/

  @SuppressLint("ViewConstructor")
  public static class LanguageSelectorPopup extends PopupLayout {
    public final MenuMoreWrap languageRecyclerWrap;
    private final OnLanguageSelectListener delegate;

    public interface OnLanguageSelectListener {
      void onSelect (String langCode);
    }

    public LanguageSelectorPopup (Context context, OnLanguageSelectListener delegate, String selected, String original) {
      super(context);
      this.delegate = delegate;

      RecyclerView languageRecyclerView = new CustomRecyclerView(context);
      languageRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
      languageRecyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
      languageRecyclerView.setAdapter(new LanguageAdapter(context, this::onOptionClick, selected, original));
      languageRecyclerView.setItemAnimator(null);

      languageRecyclerWrap = new MenuMoreWrap(context) {
        @Override
        public int getItemsHeight () {
          return Screen.dp(280);
        }
      };
      languageRecyclerWrap.init(null, null);
      languageRecyclerWrap.addView(languageRecyclerView, FrameLayoutFix.newParams(Screen.dp(178), Screen.dp(280)));
      languageRecyclerWrap.setAnchorMode(MenuMoreWrap.ANCHOR_MODE_HEADER);
    }

    public void show () {
      init(true);
      setIgnoreAllInsets(true);
      showMoreView(languageRecyclerWrap);
    }

    private void onOptionClick (View v) {
      if (!(v instanceof LanguageView)) return;
      LanguageView languageView = (LanguageView) v;
      delegate.onSelect(languageView.langCode);
      hideWindow(true);
    }
  }

  public static class LanguageAdapter extends RecyclerView.Adapter<LanguageViewHolder> {
    private final ArrayList<String> languages;
    private final View.OnClickListener listener;
    private final Context context;
    private final int selectedPosition;
    private final int originalPosition;

    public LanguageAdapter (Context context, View.OnClickListener listener, String selected, String original) {
      this.languages = new ArrayList<>(Lang.supportedLanguagesForTranslate.length);
      this.listener = listener;
      this.context = context;

      ArrayList<String> recents = Settings.instance().getTranslateLanguageRecents();

      addLanguage(selected);
      addLanguage(original);
      for (String lang: recents) {
        if (StringUtils.equalsOrBothEmpty(lang, selected)) continue;
        if (StringUtils.equalsOrBothEmpty(lang, original)) continue;
        addLanguage(lang);
      }

      for (String lang: Lang.supportedLanguagesForTranslate) {
        if (StringUtils.equalsOrBothEmpty(lang, selected)) continue;
        if (StringUtils.equalsOrBothEmpty(lang, original)) continue;
        if (recents.contains(lang)) continue;
        addLanguage(lang);
      }

      originalPosition = getPosition(original);
      selectedPosition = getPosition(selected);
    }

    private void addLanguage (String lang) {
      if (Lang.getLanguageName(lang, null) != null) {
        languages.add(lang);
      }
    }

    private int getPosition (String language) {
      for (int a = 0; a < languages.size(); a++) {
        if (StringUtils.equalsOrBothEmpty(language, languages.get(a))) {
          return a;
        }
      }
      return -1;
    }

    @NonNull
    @Override
    public LanguageViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return LanguageViewHolder.create(context, listener);
    }

    @Override
    public void onBindViewHolder (@NonNull LanguageViewHolder holder, int position) {
      holder.bind(languages.get(position), position == originalPosition, position == selectedPosition);
    }

    @Override
    public int getItemCount () {
      return languages.size();
    }
  }

  public static class LanguageViewHolder extends RecyclerView.ViewHolder {
    public LanguageViewHolder (View view) {
      super(view);
    }

    public static LanguageViewHolder create (Context context, View.OnClickListener onClickListener) {
      LanguageView view = new LanguageView(context);
      view.setOnClickListener(onClickListener);
      Views.setClickable(view);
      RippleSupport.setSimpleWhiteBackground(view);
      return new LanguageViewHolder(view);
    }

    public void bind (String language, boolean isOriginal, boolean isSelected) {
      LanguageView languageView = (LanguageView) itemView;
      languageView.langCode = language;
      languageView.isSelected = isSelected;
      languageView.titleView.setText(Lang.getLanguageName(language, language));
      languageView.titleView.setTranslationY(isOriginal ? -Screen.dp(9.5f): 0);
      languageView.subtitleView.setVisibility(isOriginal ? View.VISIBLE: View.GONE);
      languageView.setPadding(Screen.dp(16), 0, Screen.dp(isSelected ? 40: 16), 0);
      languageView.invalidate();
    }
  }

  public static class LanguageView extends FrameLayout {
    private final TextView titleView;
    private final TextView subtitleView;
    private String langCode;
    private boolean isSelected;

    private static Drawable check;

    public LanguageView (@NonNull Context context) {
      super(context);

      if (check == null) {
        check = Drawables.get(R.drawable.baseline_check_24);
        check.setColorFilter(new PorterDuffColorFilter(Theme.getColor(R.id.theme_color_iconActive), PorterDuff.Mode.SRC_IN));
      }

      titleView = new TextView(context);
      titleView.setTextColor(Theme.getColor(R.id.theme_color_text));
      titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
      titleView.setEllipsize(TextUtils.TruncateAt.END);
      titleView.setMaxLines(1);
      addView(titleView, FrameLayoutFix.newParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

      subtitleView = new TextView(context);
      subtitleView.setText(Lang.getString(R.string.ChatTranslateOriginal));
      subtitleView.setTextColor(Theme.getColor(R.id.theme_color_textLight));
      subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
      subtitleView.setEllipsize(TextUtils.TruncateAt.END);
      subtitleView.setMaxLines(1);
      addView(subtitleView, FrameLayoutFix.newParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, Screen.dp(6)));
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(MeasureSpec.makeMeasureSpec(Screen.dp(178), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Screen.dp(50), MeasureSpec.EXACTLY));
    }

    @Override
    protected void dispatchDraw (Canvas canvas) {
      super.dispatchDraw(canvas);
      if (isSelected) {
        Drawables.draw(canvas, check, getMeasuredWidth() - Screen.dp(40), Screen.dp(13), null);
      }
    }
  }

  /**/

  private class MessageTextView extends View {
    public MessageTextView (Context context) {
      super(context);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int textHeight = getTextStaticHeight() + Screen.dp(12);
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(textHeight, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw (Canvas canvas) {
      int addTop = Screen.dp(6) + Math.min(recyclerView.getMeasuredHeight(), getTextStaticHeight()) - Math.min(recyclerView.getMeasuredHeight(), getTextAnimatedHeight());
      float alpha = translationCounterDrawable.getLoadingTextAlpha();
      for (ListAnimator.Entry<TextWrapper> entry: text) {
        entry.item.draw(canvas, Screen.dp(18), addTop, null, alpha * entry.getVisibility(), textMediaReceiver);
      }
      invalidate();
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      textMediaReceiver.attach();
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      textMediaReceiver.detach();
    }

    @Override
    public boolean onTouchEvent (MotionEvent event) {
      if (super.onTouchEvent(event)) return true;
      for (ListAnimator.Entry<TextWrapper> entry: text) {
        if (entry.getVisibility() == 1f && entry.item.onTouchEvent(this, event)) {
          return true;
        }
      }

      return false;
    }
  }
}
