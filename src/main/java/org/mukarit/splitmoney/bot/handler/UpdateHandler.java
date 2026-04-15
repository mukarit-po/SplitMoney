package org.mukarit.splitmoney.bot.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
@RequiredArgsConstructor
public class UpdateHandler {

    private final CommandRouter commandRouter;
    private final CallbackQueryHandler callbackQueryHandler;

    @Value("${telegram.admin.chat-id}")
    private Long adminChatId;

    public void handleUpdate(Update update, TelegramClient bot) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            commandRouter.route(update.getMessage(), bot, adminChatId);
        } else if (update.hasCallbackQuery()) {
            callbackQueryHandler.handle(update.getCallbackQuery(), bot);
        }
    }
}
