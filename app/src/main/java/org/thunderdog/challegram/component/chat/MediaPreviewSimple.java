package org.thunderdog.challegram.component.chat;

import android.graphics.Canvas;
import android.graphics.Path;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageFileRemote;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.DrawableProvider;

import me.vkryl.td.Td;

public class MediaPreviewSimple extends MediaPreview {
  private Path outline;

  private ImageFile previewImage;
  private GifFile previewGif;

  private ImageFile targetImage;
  private GifFile targetGif;

  public MediaPreviewSimple (Tdlib tdlib, int size, int cornerRadius, TdApi.ProfilePhoto profilePhoto, TdApi.Thumbnail thumbnail) {
    super(size, cornerRadius);
    // TODO avatar preview
  }

  public MediaPreviewSimple (Tdlib tdlib, int size, int cornerRadius, TdApi.ChatPhotoInfo chatPhotoInfo) {
    super(size, cornerRadius);
    if (chatPhotoInfo.minithumbnail != null) {
      this.previewImage = new ImageFileLocal(chatPhotoInfo.minithumbnail);
      this.previewImage.setSize(size);
      this.previewImage.setScaleType(ImageFile.CENTER_CROP);
      this.previewImage.setDecodeSquare(true);
    }
    if (chatPhotoInfo.big != null) {
      this.targetImage = new ImageFile(tdlib, chatPhotoInfo.big, null);
      this.targetImage.setSize(size);
      this.targetImage.setScaleType(ImageFile.CENTER_CROP);
      this.targetImage.setDecodeSquare(true);
      this.targetImage.setNoBlur();
    }
  }

  public MediaPreviewSimple (Tdlib tdlib, int size, int cornerRadius, TdApi.Venue venue, TdApi.Thumbnail thumbnail) {
    this(tdlib, size, cornerRadius, venue.location, thumbnail);
  }

  public MediaPreviewSimple (Tdlib tdlib, int size, int cornerRadius, TdApi.Location location, TdApi.Thumbnail thumbnail) {
    super(size, cornerRadius);
    if (thumbnail != null) {
      this.previewImage = TD.toImageFile(tdlib, thumbnail);
      if (this.previewImage != null) {
        this.previewImage.setSize(size);
        this.previewImage.setScaleType(ImageFile.CENTER_CROP);
        this.previewImage.setDecodeSquare(true);
        this.previewImage.setNoBlur();
      }
      this.previewGif = TD.toGifFile(tdlib, thumbnail);
      if (this.previewGif != null) {
        this.previewGif.setOptimize(true);
        this.previewGif.setSize(size);
        this.previewGif.setScaleType(GifFile.CENTER_CROP);
        this.previewGif.setPlayOnce();
      }
    }

    switch (Settings.instance().getMapProviderType(true)) {
      case Settings.MAP_PROVIDER_GOOGLE: {
        this.targetImage = new ImageFileRemote(tdlib, U.getMapPreview(location.latitude, location.longitude, 16, false, size, size), new TdApi.FileTypeThumbnail());
        break;
      }
      case Settings.MAP_PROVIDER_TELEGRAM:
      default: {
        int scale = Screen.density() >= 2.0f ? 2 : 1;
        this.targetImage = new ImageFileRemote(tdlib, new TdApi.GetMapThumbnailFile(location, 16, size / scale, size / scale, scale, 0), "telegram_map_" + location.latitude + "," + location.longitude + "_" + size);
        break;
      }
    }
    this.targetImage.setSize(size);
    this.targetImage.setScaleType(ImageFile.CENTER_CROP);
    this.targetImage.setDecodeSquare(true);
    this.targetImage.setNoBlur();

    // TODO
    /*if (receiver.needPlaceholder()) {
      if (previewReceiver.needPlaceholder()) {
        previewReceiver.drawPlaceholder(c);
        if (needPinIcon) {
          Drawable drawable = view.getSparseDrawable(R.drawable.baseline_location_on_24, 0);
          Drawables.draw(c, drawable, receiver.centerX() - drawable.getMinimumWidth() / 2, receiver.centerY() - drawable.getMinimumHeight() / 2, Paints.getPorterDuffPaint(0xffffffff));
        }
      }
      previewReceiver.draw(c);
    }
    receiver.draw(c);*/
  }

  public MediaPreviewSimple (Tdlib tdlib, int size, int cornerRadius, TdApi.Thumbnail thumbnail, TdApi.Minithumbnail minithumbnail) {
    super(size, cornerRadius);
    if (minithumbnail != null) {
      this.previewImage = new ImageFileLocal(minithumbnail);
      this.previewImage.setSize(size);
      this.previewImage.setScaleType(ImageFile.CENTER_CROP);
      this.previewImage.setDecodeSquare(true);
    }
    if (thumbnail != null) {
      this.targetImage = TD.toImageFile(tdlib, thumbnail);
      if (this.targetImage != null) {
        this.targetImage.setSize(size);
        this.targetImage.setScaleType(ImageFile.CENTER_CROP);
        this.targetImage.setDecodeSquare(true);
        this.targetImage.setNoBlur();
      }
      this.targetGif = TD.toGifFile(tdlib, thumbnail);
      if (this.targetGif != null) {
        this.targetGif.setOptimize(true);
        this.targetGif.setSize(size);
        this.targetGif.setScaleType(GifFile.CENTER_CROP);
        this.targetGif.setPlayOnce();
      }
    }
  }

  public MediaPreviewSimple (Tdlib tdlib, int size, int cornerRadius, TdApi.Sticker sticker) {
    super(size, cornerRadius);
    this.outline = Td.buildOutline(sticker.outline,
      Math.min((float) size / (float) sticker.width, (float) size / (float) sticker.height),
      outline
    );
    if (sticker.thumbnail != null) {
      this.previewImage = TD.toImageFile(tdlib, sticker.thumbnail);
      if (this.previewImage != null) {
        this.previewImage.setSize(size);
        this.previewImage.setScaleType(ImageFile.FIT_CENTER);
      }
      this.previewGif = TD.toGifFile(tdlib, sticker.thumbnail);
      if (this.previewGif != null) {
        this.previewGif.setSize(size);
        this.previewGif.setScaleType(GifFile.FIT_CENTER);
      }
    }
    if (sticker.isAnimated) {
      this.targetGif = new GifFile(tdlib, sticker);
      this.targetGif.setOptimize(true);
      this.targetGif.setSize(size);
      this.targetGif.setScaleType(GifFile.FIT_CENTER);
      this.targetGif.setPlayOnce();
    } else {
      this.targetImage = new ImageFile(tdlib, sticker.sticker);
      this.targetImage.setWebp();
      this.targetImage.setNoBlur();
      this.targetImage.setSize(size);
      this.targetImage.setScaleType(GifFile.FIT_CENTER);
    }
  }

  @Override
  public void requestFiles (ComplexReceiver receiver, boolean invalidate) {
    GifReceiver gifPreview = receiver.getGifReceiver(0);
    gifPreview.requestFile(previewGif);

    GifReceiver gifReceiver = receiver.getGifReceiver(1);
    gifReceiver.requestFile(targetGif);

    DoubleImageReceiver imageReceiver = receiver.getPreviewReceiver(0);
    imageReceiver.requestFile(previewImage, targetImage);
  }

  private Receiver getTargetReceiver (ComplexReceiver receiver) {
    return targetGif != null ? receiver.getGifReceiver(1) : receiver.getPreviewReceiver(0).getReceiver();
  }

  private Receiver getPreviewReceiver (ComplexReceiver receiver) {
    return previewGif != null ? receiver.getGifReceiver(0) : receiver.getPreviewReceiver(0).getPreview();
  }

  @Override
  public boolean needPlaceholder (ComplexReceiver receiver) {
    Receiver target = getTargetReceiver(receiver);
    if (target.needPlaceholder()) {
      Receiver preview = getPreviewReceiver(receiver);
      return preview.needPlaceholder();
    }
    return false;
  }

  @Override
  public <T extends View & DrawableProvider> void draw (T view, Canvas c, ComplexReceiver receiver, float x, float y, float width, float height, int cornerRadius, float alpha) {
    Receiver preview = getPreviewReceiver(receiver);
    Receiver target = getTargetReceiver(receiver);

    preview.setRadius(cornerRadius);
    target.setRadius(cornerRadius);

    preview.setBounds((int) x, (int) y, (int) (x + width), (int) (y + height));
    target.setBounds((int) x, (int) y, (int) (x + width), (int) (y + height));

    if (alpha != 1f) {
      preview.setPaintAlpha(alpha);
      target.setPaintAlpha(alpha);
    }

    if (target.needPlaceholder()) {
      if (preview.needPlaceholder()) {
        if (outline != null) {
          preview.drawPlaceholderContour(c, outline, alpha);
        } else {
          preview.drawPlaceholder(c);
        }
      }
      preview.draw(c);
    }
    target.draw(c);

    if (alpha != 1f) {
      preview.restorePaintAlpha();
      target.restorePaintAlpha();
    }
  }
}
