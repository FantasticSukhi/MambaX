package org.thunderdog.challegram.telegram;

import android.util.Log;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.tool.UI;

import java.util.HashMap;

public class TdlibEditMediaManager implements MessageEditMediaUploadCallback {
  private final Tdlib tdlib;
  private final HashMap<String, MessageEditMediaPending> pendingMessageMedia = new HashMap<>();

  public TdlibEditMediaManager (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  public void editMediaStart (long chatId, long messageId, TdApi.InputMessageContent inputMessageContent) {
    final MessageEditMediaPending pendingEdit = new MessageEditMediaPending(tdlib, chatId, messageId, inputMessageContent);
    pendingEdit.init(this);
    addPendingEditAndNotify(pendingEdit);
    tdlib.listeners().updateMessagePendingContentChanged(chatId, messageId);
  }

  public boolean hasPendingMessageMedia (long chatId, long messageId) {
    synchronized (pendingMessageMedia) {
      return pendingMessageMedia.containsKey(toKey(chatId, messageId));
    }
  }

  public MessageEditMediaPending getPendingMessageMedia (long chatId, long messageId) {
    synchronized (pendingMessageMedia) {
      return pendingMessageMedia.get(toKey(chatId, messageId));
    }
  }

  @Override
  public void onMediaPreliminaryUploadStart (MessageEditMediaPending pendingEdit, TdApi.File file) {
    notifyPendingContentChanged(pendingEdit);
  }

  @Override
  public void onMediaPreliminaryUploadComplete (MessageEditMediaPending pendingEdit, TdApi.InputMessageContent content) {
    tdlib.send(new TdApi.EditMessageMedia(pendingEdit.chatId, pendingEdit.messageId, null, content), (result, error) -> {
      removePendingEditAndNotify(pendingEdit);
      if (error != null) {
        UI.showError(error);
      }
    });

  }

  @Override
  public void onMediaPreliminaryUploadFailed (MessageEditMediaPending pendingEdit, boolean isCanceled) {
    removePendingEditAndNotify(pendingEdit);
  }

  private void addPendingEditAndNotify (MessageEditMediaPending pendingEdit) {
    final String key = toKey(pendingEdit.chatId, pendingEdit.messageId);
    synchronized (pendingMessageMedia) {
      pendingMessageMedia.put(key, pendingEdit);
    }
    notifyPendingContentChanged(pendingEdit);
  }

  private void removePendingEditAndNotify (MessageEditMediaPending pendingEdit) {
    final String key = toKey(pendingEdit.chatId, pendingEdit.messageId);
    MessageEditMediaPending currentData = pendingMessageMedia.get(key);
    if (currentData == pendingEdit) {
      synchronized (pendingMessageMedia) {
        pendingMessageMedia.remove(key);
      }
      notifyPendingContentChanged(pendingEdit);
    }
  }

  private void notifyPendingContentChanged (MessageEditMediaPending pendingEdit) {
    tdlib.listeners().updateMessagePendingContentChanged(pendingEdit.chatId, pendingEdit.messageId);
  }




  private static String toKey (long chatId, long messageId) {
    return chatId + "_" + messageId;
  }

  public static class UploadFuture implements TdlibFilesManager.FileListener {
    private final Tdlib tdlib;
    private final Callback listener;
    private final int id;

    public interface Callback {
      void onFileUpdate (int id, long fileId, TdApi.File file);
      void onComplete (int id, long fileId, TdApi.File file);
      void onFail (int id, boolean isCanceled);
    }

    TdApi.File file;
    private final TdApi.InputFile inputFile;
    private final TdApi.FileType fileType;
    private boolean isCanceled;
    private boolean isCompleted;
    private boolean isFailed;

    UploadFuture (Tdlib tdlib, int id, TdApi.InputFile inputFile, TdApi.FileType fileType, Callback callback) {
      this.tdlib = tdlib;
      this.listener = callback;
      this.inputFile = inputFile;
      this.fileType = fileType;
      this.id = id;
    }

    void init () {
      tdlib.send(new TdApi.PreliminaryUploadFile(inputFile, fileType, 1), (file, err) -> {
        if (err != null) {
          UI.showError(err);
          listener.onFail(id, false);
          return;
        }
        UI.post(() -> {
          if (isCanceled) {
            return;
          }

          if (TD.isFileUploaded(file)) {
            onUpdateFileImpl(file);
            this.isCompleted = true;
            this.listener.onComplete(id, file.id, file);
            return;
          }

          onUpdateFileImpl(file);
          tdlib.files().subscribe(file, this);
        });
      });
    }

    public void cancel () {
      if (isCanceled) {
        return;
      }

      isCanceled = true;
      listener.onFail(id, true);
      if (file != null) {
        tdlib.files().unsubscribe(file.id, this);
        tdlib.client().send(new TdApi.CancelPreliminaryUploadFile(file.id), tdlib.okHandler());
      }
    }

    @Override
    public void onFileLoadProgress (TdApi.File file) {
      Log.i("WTF_DEBUG", "progress " + file.id + " " + TD.getFileProgress(file));
      UI.post(() -> onUpdateFileImpl(file));
    }

    @Override
    public void onFileLoadStateChanged (Tdlib tdlib, int fileId, int state, @Nullable TdApi.File downloadedFile) {
      UI.post(() -> {
        if (downloadedFile != null) {
          onUpdateFileImpl(downloadedFile);
        }

        if (state == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED) {
          tdlib.files().unsubscribe(fileId, this);
          this.isCompleted = true;
          this.listener.onComplete(id, fileId, downloadedFile != null ? downloadedFile : file);
        }

        if (state == TdlibFilesManager.STATE_FAILED) {
          tdlib.files().unsubscribe(fileId, this);
          this.isFailed = true;
          this.listener.onFail(id, false);
        }

        Log.i("WTF_DEBUG", "state " + fileId + " " + state + " ");
      });
    }

    private void onUpdateFileImpl (TdApi.File file) {
      this.file = file;
      this.listener.onFileUpdate(id, file.id, file);
    }

    public boolean isCanceled () {
      return isCanceled;
    }

    public boolean isCompleted () {
      return isCompleted && !isCanceled && !isFailed;
    }

    public boolean isFailed () {
      return isFailed && !isCanceled;
    }
  }

}
