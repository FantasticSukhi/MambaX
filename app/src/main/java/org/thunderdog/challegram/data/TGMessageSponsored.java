package org.thunderdog.challegram.data;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.telegram.Tdlib;

public class TGMessageSponsored {
  public static TGMessage sponsoredToTgx (MessagesManager manager, long chatId, int date, TdApi.SponsoredMessage sMsg) {
    return new TGMessageText(manager, sponsoredToTd(chatId, date, sMsg), (TdApi.MessageText) sMsg.content);
  }

  private static TdApi.Message sponsoredToTd (long chatId, int date, TdApi.SponsoredMessage sMsg) {
    TdApi.MessageText fMsgContent = (TdApi.MessageText) sMsg.content;
    fMsgContent.webPage = new TdApi.WebPage();
    fMsgContent.webPage.type = "telegram_adx";
    fMsgContent.webPage.url = sMsg.startParameter != null ? sMsg.startParameter : "";

    TdApi.Message fMsg = new TdApi.Message();
    fMsg.sender = new TdApi.MessageSenderChat(sMsg.sponsorChatId);
    fMsg.content = fMsgContent;
    fMsg.authorSignature = "sponsored";
    fMsg.id = -sMsg.id;
    fMsg.date = date;
    fMsg.isOutgoing = false;
    fMsg.chatId = chatId;
    fMsg.isChannelPost = true;
    fMsg.replyInChatId = sMsg.sponsorChatId;

    return fMsg;
  }

  public static TdApi.SponsoredMessage generateSponsoredMessage (Tdlib tdlib) {
    TdApi.SponsoredMessage msg = new TdApi.SponsoredMessage();
    msg.sponsorChatId = tdlib.myUserId();
    msg.id = 1;
    msg.content = new TdApi.MessageText(new TdApi.FormattedText("Test ad message", null), null);
    return msg;
  }
}
