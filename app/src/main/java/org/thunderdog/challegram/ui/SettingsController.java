package org.thunderdog.challegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.ComplexRecyclerView;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ConnectionListener;
import org.thunderdog.challegram.telegram.StickersListener;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.CancellableRunnable;

/**
 * Date: 16/11/2016
 * Author: default
 */

public class SettingsController extends ViewController<Void> implements
  View.OnClickListener, ComplexHeaderView.Callback,
  Menu, MoreDelegate, OptionDelegate,
  TdlibCache.MyUserDataChangeListener, ConnectionListener, StickersListener, MediaLayout.MediaGalleryCallback,
  ActivityResultHandler, Client.ResultHandler, View.OnLongClickListener {
  private ComplexHeaderView headerCell;
  private ComplexRecyclerView contentView;
  private SettingsAdapter adapter;

  public SettingsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_settings;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  private boolean oneShot;

  @Override
  public void onFocus () {
    super.onFocus();
    contentView.setFactorLocked(false);
    preloadStickers();
    if (!oneShot) {
      oneShot = true;
      tdlib.listeners().subscribeToStickerUpdates(this);
    }
  }

  private CancellableRunnable supportOpen;

  private void cancelSupportOpen () {
    if (supportOpen != null) {
      supportOpen.cancel();
      supportOpen = null;
    }
  }

  @Override
  public void onBlur () {
    super.onBlur();
    contentView.setFactorLocked(true);
    cancelSupportOpen();
  }

  @Override
  protected int getHeaderIconColorId () {
    return headerCell != null && !headerCell.isCollapsed() ? R.id.theme_color_white : R.id.theme_color_headerIcon;
  }

  @Override
  protected int getHeaderHeight () {
    return (int) (Size.getHeaderPortraitSize() + Size.getHeaderSizeDifference(true) * contentView.getScrollFactor());
  }

  @Override
  protected int getMaximumHeaderHeight () {
    return Size.getHeaderBigPortraitSize(true);
  }

  @Override
  protected int getFloatingButtonId () {
    return R.drawable.baseline_edit_24;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_more_settings;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_more_settings: {
        header.addMoreButton(menu, this);
        break;
      }
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_more: {
        showMore(new int[] {R.id.more_btn_logout}, new String[] { Lang.getString(R.string.LogOut) }, 0);
        break;
      }
    }
  }

  @Override
  public boolean onOptionItemPressed (View optionItemView, int id) {
    return tdlib.ui().handleProfileOption(this, id, tdlib.myUser());
  }

  @Override
  protected void onFloatingButtonPressed () {
    EditNameController editNameController = new EditNameController(context, tdlib);
    editNameController.setMode(EditNameController.MODE_RENAME_SELF);
    navigateTo(editNameController);
  }

  // https://bitbucket.org/challegram/challegram/issues/416
  private float textSize;
  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (adapter != null) {
      float textSize = Settings.instance().getChatFontSize();
      if (this.textSize != 0 && this.textSize != textSize) {
        int i = adapter.indexOfViewById(R.id.btn_bio);
        if (i != -1) {
          View view = contentView.getLayoutManager().findViewByPosition(i);
          if (view != null) {
            view.requestLayout();
          } else {
            adapter.notifyItemChanged(i);
          }
        }
      }
      this.textSize = textSize;
    }
    checkErrors();
  }

  @Override
  public void onMoreItemPressed (int id) {
    switch (id) {
      case R.id.menu_btn_more: {
        tdlib.ui().logOut(this, true);
        break;
      }
      default: {
        tdlib.ui().handleProfileMore(this, id, tdlib.myUser(), null);
        break;
      }
    }
  }

  @Override
  protected void attachNavigationController (NavigationController navigationController) {
    super.attachNavigationController(navigationController);
    contentView.setFloatingButton(navigationController.getFloatingButton());
  }

  @Override
  protected void detachNavigationController () {
    super.detachNavigationController();
    contentView.setFloatingButton(null);
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  private void changeProfilePhoto () {
    IntList ids = new IntList(4);
    StringList strings = new StringList(4);
    IntList colors = new IntList(4);
    IntList icons = new IntList(4);

    final TdApi.User user = tdlib.myUser();
    if (user != null && user.profilePhoto != null) {
      ids.append(R.id.btn_open);
      strings.append(R.string.Open);
      icons.append(R.drawable.baseline_visibility_24);
      colors.append(OPTION_COLOR_NORMAL);
    }

    ids.append(R.id.btn_changePhotoCamera);
    strings.append(R.string.ChatCamera);
    icons.append(R.drawable.baseline_camera_alt_24);
    colors.append(OPTION_COLOR_NORMAL);

    ids.append(R.id.btn_changePhotoGallery);
    strings.append(R.string.Gallery);
    icons.append(R.drawable.baseline_image_24);
    colors.append(OPTION_COLOR_NORMAL);

    final long profilePhotoToDelete = user != null && user.profilePhoto != null ? user.profilePhoto.id : 0;
    if (user != null && user.profilePhoto != null) {
      ids.append(R.id.btn_changePhotoDelete);
      strings.append(R.string.Delete);
      icons.append(R.drawable.baseline_delete_24);
      colors.append(OPTION_COLOR_RED);
    }

    showOptions(null, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
      switch (id) {
        case R.id.btn_open: {
          MediaViewController.openFromProfile(SettingsController.this, user, headerCell);
          break;
        }
        case R.id.btn_changePhotoCamera: {
          UI.openCameraDelayed(context);
          break;
        }
        case R.id.btn_changePhotoGallery: {
          UI.openGalleryDelayed(false);
          break;
        }
        case R.id.btn_changePhotoDelete: {
          tdlib.client().send(new TdApi.DeleteProfilePhoto(profilePhotoToDelete), tdlib.okHandler());
          break;
        }
      }
      return true;
    });
  }

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    if (resultCode == Activity.RESULT_OK) {
      tdlib.ui().handlePhotoChange(requestCode, data, null);
    }
  }

  private boolean hasNotificationError;

  private void checkErrors () {
    boolean hasNotificationError = tdlib.notifications().hasLocalNotificationProblem();
    if (this.hasNotificationError != hasNotificationError) {
      this.hasNotificationError = hasNotificationError;
      if (adapter != null) {
        adapter.updateValuedSettingById(R.id.btn_notificationSettings);
      }
    }
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    checkErrors();
  }

  private void updateButtonsColor () {
    if (headerView != null) {
      headerView.getBackButton().setColor(ColorUtils.fromToArgb(Theme.headerBackColor(), Color.WHITE, headerCell != null ? headerCell.getAvatarExpandFactor() : 0f));
      headerView.updateButtonsTransform(getMenuId(), this, getTransformFactor());
    }
  }

  @Override
  protected void applyHeaderMenuTransform (LinearLayout menu, float factor) {
    super.applyHeaderMenuTransform(menu, factor);
    for (int i = 0; i < menu.getChildCount(); i++ ){
      View v = menu.getChildAt(i);
      if (v instanceof HeaderButton) {
        ((HeaderButton) v).setThemeColorId(R.id.theme_color_headerIcon, R.id.theme_color_white, headerCell != null ? headerCell.getAvatarExpandFactor() : 0f);
      }
    }
  }

  @Override
  protected View onCreateView (Context context) {
    this.headerCell = new ComplexHeaderView(context, tdlib, this);
    this.headerCell.setAvatarExpandListener((headerView1, expandFactor, byCollapse, allowanceFactor, collapseFactor) -> updateButtonsColor());
    this.headerCell.setAllowEmptyClick();
    this.headerCell.initWithController(this, true);
    this.headerCell.setInnerMargins(Screen.dp(56f), Screen.dp(49f));
    this.headerCell.setPhotoOpenCallback(this);
    updateHeader();

    initMyUser();

    this.contentView = new ComplexRecyclerView(context, this);
    this.contentView.setHasFixedSize(true);
    this.contentView.setHeaderView(headerCell, this);
    this.contentView.setItemAnimator(null);
    ViewSupport.setThemedBackground(contentView, R.id.theme_color_background, this);
    this.contentView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    this.contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    this.adapter = new SettingsAdapter(this) {
      @Override
      public void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        boolean hasError = false;
        switch (item.getId()) {
          case R.id.btn_notificationSettings:
            hasError = hasNotificationError = tdlib.notifications().hasLocalNotificationProblem();
            break;
        }
        view.setUnreadCounter(hasError ? Tdlib.CHAT_FAILED : 0, false, isUpdate);
        switch (item.getId()) {
          case R.id.btn_changePhoneNumber: {
            view.setData(Lang.getStringBold(R.string.ReminderCheckPhoneNumberText, myPhone));
            break;
          }
          case R.id.btn_2fa: {
            view.setData(R.string.ReminderCheckTfaPasswordText);
            break;
          }
          case R.id.btn_username: {
            if (myUsername == null) {
              view.setData(R.string.LoadingUsername);
            } else if (myUsername.isEmpty()) {
              view.setData(R.string.SetUpUsername);
            } else {
              view.setData("@" + myUsername);
            }
            break;
          }
          case R.id.btn_phone: {
            view.setData(myPhone);
            break;
          }
          case R.id.btn_bio: {
            String text;
            if (about == null) {
              text = Lang.getString(R.string.LoadingInformation);
            } else if (StringUtils.isEmpty(about)) {
              text = Lang.getString(R.string.BioNone);
            } else {
              text = about;
            }
            view.setText(obtainWrapper(text));
            break;
          }
        }
      }
    };
    this.adapter.setOnLongClickListener(this);

    List<ListItem> items = adapter.getItems();
    ArrayUtils.ensureCapacity(items, 27);

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET));
    items.add(new ListItem(ListItem.TYPE_INFO_SETTING, R.id.btn_username, R.drawable.baseline_alternate_email_24, R.string.Username).setContentStrings(R.string.LoadingUsername, R.string.SetUpUsername));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_INFO_SETTING, R.id.btn_phone, R.drawable.baseline_phone_24, R.string.Phone));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_bio, R.drawable.baseline_info_24, R.string.UserBio).setContentStrings(R.string.LoadingInformation, R.string.BioNone));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    TdApi.SuggestedAction[] actions = tdlib.getSuggestedActions();
    if (actions.length > 0 && tdlib.haveAnySettingsSuggestions()) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

      int addedItems = 0;

      for (TdApi.SuggestedAction action : actions) {
        if (tdlib.isSettingSuggestion(action)) {
          if (addedItems++ > 0) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          }
        }

        switch (action.getConstructor()) {
          case TdApi.SuggestedActionCheckPhoneNumber.CONSTRUCTOR: {
            items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_changePhoneNumber, R.drawable.baseline_sim_card_alert_24, R.string.ReminderCheckPhoneNumber));
            break;
          }

          case TdApi.SuggestedActionCheckPassword.CONSTRUCTOR: {
            items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_2fa, R.drawable.baseline_gpp_maybe_24, R.string.ReminderCheckTfaPassword));
            break;
          }
        }
      }

      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    /*if (Settings.instance().hasProxyConfiguration()) {
      items.add(newProxyItem());
      items.add(new SettingItem(SettingItem.TYPE_SEPARATOR));
    }*/
    this.hasNotificationError = tdlib.notifications().hasLocalNotificationProblem();
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_devices, R.drawable.baseline_devices_other_24, R.string.Devices));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_notificationSettings, R.drawable.baseline_notifications_24, R.string.Notifications));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_chatSettings, R.drawable.baseline_data_usage_24, R.string.DataSettings));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_privacySettings, R.drawable.baseline_lock_24, R.string.PrivacySettings));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_themeSettings, R.drawable.baseline_palette_24, R.string.ThemeSettings));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tweakSettings, R.drawable.baseline_extension_24, R.string.TweakSettings));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_stickerSettings, R.drawable.deproko_baseline_stickers_filled_24, R.string.Stickers));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_languageSettings, R.drawable.baseline_language_24, R.string.Language));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_help, R.drawable.baseline_live_help_24, R.string.AskAQuestion));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_faq, R.drawable.baseline_help_24, R.string.TelegramFAQ));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_privacyPolicy, R.drawable.baseline_policy_24, R.string.PrivacyPolicy));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_BUILD_NO, R.id.btn_build, 0, Lang.getAppBuildAndVersion(tdlib), false));

    processUserFull(tdlib.myUserFull());

    this.contentView.setAdapter(adapter);

    tdlib.cache().addMyUserListener(this);
    tdlib.listeners().subscribeToConnectivityUpdates(this);
    TGLegacyManager.instance().addEmojiListener(adapter);

    return contentView;
  }

  @Override
  public boolean onLongClick (View v) {
    switch (v.getId()) {
      case R.id.btn_build:
        showBuildOptions(true);
        return true;
    }
    return false;
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (aboutWrapper != null) {
      aboutWrapper.setTextFlagEnabled(Text.FLAG_ALIGN_RIGHT, Lang.rtl());
      if (adapter != null)
        adapter.updateValuedSettingById(R.id.btn_bio);
    }
  }

  @Override
  public void handleLanguagePackEvent (int event, int arg1) {
    if (adapter != null) {
      switch (event) {
        case Lang.EVENT_PACK_CHANGED:
          adapter.notifyAllStringsChanged();
          if (headerCell != null) {
            headerCell.setSubtitle(getSubtext());
          }
          break;
        case Lang.EVENT_DIRECTION_CHANGED:
          adapter.notifyAllStringsChanged();
          break;
        case Lang.EVENT_STRING_CHANGED:
          adapter.notifyStringChanged(arg1);
          break;
        case Lang.EVENT_DATE_FORMAT_CHANGED:
          // Nothing to change
          break;
      }
    }
  }

  private TextWrapper aboutWrapper;

  private TextWrapper obtainWrapper (String text) {
    if (aboutWrapper == null || !StringUtils.equalsOrBothEmpty(aboutWrapper.getText(), text)) {
      aboutWrapper = new TextWrapper(tdlib, text, TGMessage.simpleTextStyleProvider(), TextColorSets.Regular.NORMAL, Text.ENTITY_FLAGS_ALL_NO_COMMANDS, null);
      aboutWrapper.addTextFlags(Text.FLAG_CUSTOM_LONG_PRESS | (Lang.rtl() ? Text.FLAG_ALIGN_RIGHT : 0));
    }
    return aboutWrapper;
  }

  private void processUserFull (final TdApi.UserFullInfo userFull) {
    if (userFull == null) {
      return;
    }
    tdlib.uiExecute(() -> {
      if (!isDestroyed()) {
        setBio(userFull.bio);
      }
    });
  }

  private void setBio (@NonNull String about) {
    if (this.about == null || !StringUtils.equalsOrBothEmpty(this.about, about)) {
      this.about = about;
      adapter.updateValuedSettingById(R.id.btn_bio);
    }
  }

  @Override
  public void onResult (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.Error.CONSTRUCTOR:
        UI.showError(object);
        break;
      default:
        Log.unexpectedTdlibResponse(object, TdApi.GetUserFullInfo.class, TdApi.UserFullInfo.class, TdApi.Error.class);
        break;
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.cache().removeMyUserListener(this);
    tdlib.listeners().unsubscribeFromConnectivityUpdates(this);
    tdlib.listeners().unsubscribeFromStickerUpdates(this);
    TGLegacyManager.instance().removeEmojiListener(adapter);
    headerCell.performDestroy();
  }

  private void updateHeader () {
    TdApi.User user = tdlib.myUser();
    if (headerCell != null) {
      if (user == null || TD.isPhotoEmpty(user.profilePhoto)) {
        headerCell.setAvatarPlaceholder(tdlib.cache().userPlaceholder(tdlib.myUserId(), user, false, ComplexHeaderView.getBaseAvatarRadiusDp(), null));
      } else {
        headerCell.setAvatar(user.profilePhoto);
      }
      headerCell.setText(user != null ? TD.getUserName(user) : Lang.getString(R.string.LoadingUser), getSubtext());
      headerCell.invalidate();
    }
  }

  private String getSubtext () {
    if (tdlib.isConnected()) {
      return Lang.lowercase(Lang.getString(tdlib.myUser() != null ? R.string.status_Online : R.string.network_Connecting));
    } else {
      return Lang.lowercase(Lang.getString(TdlibUi.stringForConnectionState(tdlib.connectionState())));
    }
  }

  // Callbacks

  @Override
  public void onMyUserUpdated (final TdApi.User myUser) {
    if (myUser == null) { // Ignoring log-out update
      return;
    }
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        updateHeader();
        if (setUsername(myUser)) {
          adapter.updateValuedSettingById(R.id.btn_username);
        }
        if (setPhoneNumber(myUser)) {
          adapter.updateValuedSettingById(R.id.btn_phone);
        }
      }
    });
  }

  @Override
  public void onMyUserBioUpdated (String newBio) {
    setBio(newBio);
  }

  private String myUsername;
  private String myPhone;
  private @Nullable String about;

  private void initMyUser () {
    TdApi.User user = tdlib.myUser();
    setUsername(user);
    setPhoneNumber(user);
  }

  private boolean setUsername (@Nullable TdApi.User myUser) {
    String username = myUser != null ? myUser.username : null;
    if ((myUsername == null && username != null) || (myUsername != null && !myUsername.equals(username))) {
      this.myUsername = username;
      return true;
    }
    return false;
  }

  private boolean setPhoneNumber (@Nullable TdApi.User user) {
    String phoneNumber;
    if (user != null) {
      phoneNumber = Strings.formatPhone(user.phoneNumber);
      if (Settings.instance().needHidePhoneNumber()) {
        phoneNumber = Strings.replaceNumbers(phoneNumber);
      }
    } else {
      phoneNumber = Lang.getString(R.string.LoadingPhone);
    }
    if (myPhone == null || !myPhone.equals(phoneNumber)) {
      this.myPhone = phoneNumber;
      return true;
    }
    return false;
  }

  @Override
  public void onConnectionStateChanged (int newState, int oldState) {
    tdlib.ui().post(() -> {
      if (headerCell != null && !isDestroyed()) {
        headerCell.setSubtitle(getSubtext());
      }
    });
  }

  @Override
  public void performComplexPhotoOpen () {
    TdApi.User user = tdlib.myUser();
    if (user != null) {
      changeProfilePhoto();
    }
  }

  @Override
  public void onSendVideo (ImageGalleryFile file, boolean isFirst) { }

  @Override
  public void onSendPhoto (ImageGalleryFile file, boolean isFirst) {
    
  }

  @Override
  public void onClick (View v) {
    cancelSupportOpen();
    if (tdlib.ui().handleProfileClick(this, v, v.getId(), tdlib.myUser(), true)) {
      return;
    }
    switch (v.getId()) {
      case R.id.btn_bio: {
        EditBioController c = new EditBioController(context, tdlib);
        c.setArguments(new EditBioController.Arguments(about, 0));
        navigateTo(c);
        break;
      }
      case R.id.btn_languageSettings: {
        navigateTo(new SettingsLanguageController(context, tdlib));
        break;
      }
      case R.id.btn_notificationSettings: {
        navigateTo(new SettingsNotificationController(context, tdlib));
        break;
      }
      case R.id.btn_devices: {
        navigateTo(new SettingsSessionsController(context, tdlib));
        break;
      }
      case R.id.btn_themeSettings: {
        navigateTo(new SettingsThemeController(context, tdlib));
        break;
      }
      case R.id.btn_tweakSettings: {
        SettingsThemeController c = new SettingsThemeController(context, tdlib);
        c.setArguments(new SettingsThemeController.Args(SettingsThemeController.MODE_INTERFACE_OPTIONS));
        navigateTo(c);
        break;
      }
      case R.id.btn_chatSettings: {
        navigateTo(new SettingsDataController(context, tdlib));
        break;
      }
      case R.id.btn_privacySettings: {
        navigateTo(new SettingsPrivacyController(context, tdlib));
        break;
      }
      case R.id.btn_help: {
        supportOpen = tdlib.ui().openSupport(this);
        break;
      }
      case R.id.btn_stickerSettings: {
        SettingsStickersController c = new SettingsStickersController(context, tdlib);
        c.setArguments(this);
        navigateTo(c);
        break;
      }
      case R.id.btn_faq: {
        tdlib.ui().openUrl(this, Lang.getString(R.string.url_faq), new TdlibUi.UrlOpenParameters().forceInstantView());
        break;
      }
      case R.id.btn_privacyPolicy: {
        tdlib.ui().openUrl(this, Lang.getStringSecure(R.string.url_privacyPolicy), new TdlibUi.UrlOpenParameters().forceInstantView());
        break;
      }
      case R.id.btn_changePhoneNumber: {
        showSuggestionPopup(new TdApi.SuggestedActionCheckPhoneNumber());
        break;
      }
      case R.id.btn_2fa: {
        showSuggestionPopup(new TdApi.SuggestedActionCheckPassword());
        break;
      }
      case R.id.btn_build: {
        if (Settings.instance().hasLogsEnabled()) {
          showBuildOptions(true);
        } else {
          tdlib.getTesterLevel(testerLevel -> tdlib.ui().post(() -> {
            if (!isDestroyed()) {
              showBuildOptions(testerLevel >= Tdlib.TESTER_LEVEL_TESTER);
            }
          }));
        }
        break;
      }
    }
  }

  private void showSuggestionPopup (TdApi.SuggestedAction suggestedAction) {
    CharSequence info = null;
    IntList ids = new IntList(3);
    StringList titles = new StringList(3);
    IntList colors = new IntList(3);
    IntList icons = new IntList(3);

    switch (suggestedAction.getConstructor()) {
      case TdApi.SuggestedActionCheckPhoneNumber.CONSTRUCTOR: {
        info = Lang.getString(R.string.ReminderCheckPhoneNumberDescription);

        ids.append(R.id.btn_changePhoneNumber);
        titles.append(R.string.ReminderActionChangePhoneNumber);
        colors.append(OPTION_COLOR_RED);
        icons.append(R.drawable.baseline_edit_24);

        ids.append(R.id.btn_info);
        titles.append(R.string.ReminderActionLearnMore);
        colors.append(OPTION_COLOR_NORMAL);
        icons.append(R.drawable.baseline_info_24);

        break;
      }
      case TdApi.SuggestedActionCheckPassword.CONSTRUCTOR: {
        info = Lang.getString(R.string.ReminderCheckTfaPasswordDescription);

        ids.append(R.id.btn_2fa);
        titles.append(R.string.ReminderActionVerifyPassword);
        colors.append(OPTION_COLOR_RED);
        icons.append(R.drawable.baseline_security_24);

        break;
      }
    }

    ids.append(R.id.btn_cancel);
    titles.append(R.string.ReminderActionDismiss);
    colors.append(OPTION_COLOR_NORMAL);
    icons.append(R.drawable.baseline_cancel_24);

    showOptions(info, ids.get(), titles.get(), colors.get(), icons.get(), (view, id) -> {
      switch (id) {
        case R.id.btn_changePhoneNumber: {
          navigateTo(new SettingsPhoneController(context, tdlib));
          break;
        }
        case R.id.btn_2fa: {
          tdlib.client().send(new TdApi.GetPasswordState(), result -> {
            if (result.getConstructor() == TdApi.PasswordState.CONSTRUCTOR) {
              runOnUiThreadOptional(() -> {
                PasswordController controller = new PasswordController(context, tdlib);
                controller.setArguments(new PasswordController.Args(PasswordController.MODE_CONFIRM, (TdApi.PasswordState) result).setSuccessListener((pwd) -> {
                  dismissSuggestion(suggestedAction);
                }));
                navigateTo(controller);
              });
            } else {
              UI.showError(result);
            }
          });
          break;
        }
        case R.id.btn_info: {
          tdlib.ui().openUrl(this, Lang.getStringSecure(R.string.url_faqPhoneNumber), new TdlibUi.UrlOpenParameters().forceInstantView());
          break;
        }
        case R.id.btn_cancel: {
          dismissSuggestion(suggestedAction);
          break;
        }
      }

      return true;
    });
  }

  private void dismissSuggestion (TdApi.SuggestedAction suggestedAction) {
    if (!tdlib.isSettingSuggestion(suggestedAction))
      return;

    int removalIndex;

    switch (suggestedAction.getConstructor()) {
      case TdApi.SuggestedActionCheckPhoneNumber.CONSTRUCTOR: {
        removalIndex = adapter.indexOfViewById(R.id.btn_changePhoneNumber);
        break;
      }
      case TdApi.SuggestedActionCheckPassword.CONSTRUCTOR: {
        removalIndex = adapter.indexOfViewById(R.id.btn_2fa);
        break;
      }
      default: {
        return;
      }
    }

    if (removalIndex == -1)
      return;
    ListItem previousItem = adapter.getItem(removalIndex - 1);
    ListItem nextItem = adapter.getItem(removalIndex + 1);
    if (nextItem == null || previousItem == null)
      return;
    int previousViewType = previousItem.getViewType();
    int nextViewType = nextItem.getViewType();

    switch (nextViewType) {
      case ListItem.TYPE_SHADOW_BOTTOM: {
        if (previousViewType == ListItem.TYPE_SHADOW_TOP) {
          adapter.removeRange(removalIndex - 1, 3);
        } else if (previousViewType == ListItem.TYPE_SEPARATOR) {
          adapter.removeRange(removalIndex - 1, 2);
        }
        break;
      }

      case ListItem.TYPE_SEPARATOR: {
        adapter.removeRange(removalIndex, 2);
        break;
      }
    }

    tdlib.client().send(new TdApi.HideSuggestedAction(suggestedAction), tdlib.okHandler());
  }

  private void showBuildOptions (boolean allowDebug) {
    final int size = allowDebug ? 3 : 2;
    IntList ids = new IntList(size);
    IntList icons = new IntList(size);
    StringList strings = new StringList(size);

    ids.append(R.id.btn_sourceCode);
    strings.append(R.string.SourceCode);
    icons.append(R.drawable.baseline_code_24);

    ids.append(R.id.btn_copyText);
    strings.append(R.string.CopyVersion);
    icons.append(R.drawable.baseline_content_copy_24);

    if (allowDebug) {
      ids.append(R.id.btn_copyDebug);
      strings.append(R.string.CopyReportData);
      icons.append(R.drawable.baseline_content_copy_24);

      ids.append(R.id.btn_tdlib);
      strings.append(R.string.TdlibLogs);
      icons.append(R.drawable.baseline_build_24);

      ids.append(R.id.btn_build);
      strings.append(R.string.AppLogs);
      icons.append(R.drawable.baseline_build_24);
    }

    SpannableStringBuilder b = new SpannableStringBuilder();
    b.append(Lang.getMarkdownStringSecure(this, R.string.AppSignature, BuildConfig.VERSION_NAME));
    b.append('\n').append(Lang.getMarkdownStringSecure(this, R.string.CommitSignature, BuildConfig.COMMIT_SHORT, BuildConfig.COMMIT_URL));
    b.append('\n').append(Lang.getMarkdownStringSecure(this, R.string.CreatedOn, Lang.getTimestamp(BuildConfig.COMMIT_DATE, TimeUnit.SECONDS)));

    showOptions(b, ids.get(), strings.get(), null, icons.get(), (itemView, id) -> {
      switch (id) {
        case R.id.btn_copyText: {
          UI.copyText(Lang.getAppBuildAndVersion(tdlib), R.string.CopiedText);
          break;
        }
        case R.id.btn_sourceCode: {
          UI.openUrl(BuildConfig.COMMIT_URL);
          break;
        }
        case R.id.btn_copyDebug: {
          UI.copyText(U.getUsefulMetadata(tdlib), R.string.CopiedText);
          break;
        }
        case R.id.btn_build: {
          navigateTo(new SettingsBugController(context, tdlib));
          break;
        }
        case R.id.btn_tdlib: {
          tdlib.getTesterLevel(level -> runOnUiThread(() -> {
            if (!isDestroyed()) {
              openTdlibLogs(level, null);
            }
          }));
          break;
        }
      }
      return true;
    });
  }

  @Override
  public void onInstalledStickerSetsUpdated (long[] stickerSetIds, boolean isMasks) {
    if (!isMasks) {
      tdlib.ui().post(() -> {
        allStickerSets = null;
        hasPreloadedStickers = false;
        preloadStickers();
      });
    }
  }

  @Override
  public void onRecentStickersUpdated (int[] stickerIds, boolean isAttached) { }

  @Override
  public void onTrendingStickersUpdated (TdApi.StickerSets stickerSets, int unreadCount) { }

  @Override
  public void onFavoriteStickersUpdated (int[] stickerIds) { }

  // Stickers preloading

  private @Nullable ArrayList<TGStickerSetInfo> allStickerSets;

  public @Nullable ArrayList<TGStickerSetInfo> getStickerSets () {
    return allStickerSets;
  }

  public interface StickerSetLoadListener {
    void onStickerSetsLoaded (ArrayList<TGStickerSetInfo> stickerSets);
  }

  private @Nullable StickerSetLoadListener stickerSetListener;

  public void setStickerSetListener (@Nullable StickerSetLoadListener listener) {
    this.stickerSetListener = listener;
  }

  private void setStickerSets (@Nullable ArrayList<TGStickerSetInfo> stickerSets) {
    this.allStickerSets = stickerSets;
    if (stickerSetListener != null) {
      stickerSetListener.onStickerSetsLoaded(stickerSets);
    }
  }

  private boolean hasPreloadedStickers;

  private void preloadStickers () {
    if (hasPreloadedStickers) {
      return;
    }
    hasPreloadedStickers = true;
    tdlib.client().send(new TdApi.GetInstalledStickerSets(false), object -> {
      if (!isDestroyed()) {
        switch (object.getConstructor()) {
          case TdApi.StickerSets.CONSTRUCTOR: {
            TdApi.StickerSetInfo[] stickerSets = ((TdApi.StickerSets) object).sets;
            final ArrayList<TGStickerSetInfo> parsedStickerSets = new ArrayList<>(stickerSets.length);
            for (TdApi.StickerSetInfo stickerSet : stickerSets) {
              parsedStickerSets.add(new TGStickerSetInfo(tdlib, stickerSet));
            }
            parsedStickerSets.trimToSize();
            tdlib.ui().post(() -> {
              if (!isDestroyed()) {
                setStickerSets(parsedStickerSets);
              }
            });
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            UI.showError(object);
            break;
          }
        }
      }
    });
  }
}
