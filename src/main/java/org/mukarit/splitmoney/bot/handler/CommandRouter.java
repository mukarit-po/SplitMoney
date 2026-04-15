package org.mukarit.splitmoney.bot.handler;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.mukarit.splitmoney.bot.state.BotState;
import org.mukarit.splitmoney.bot.state.UserStateManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommandRouter {

    private final UserHandler userHandler;
    private final AdminHandler adminHandler;
    private final UserStateManager stateManager;

    @Value("${telegram.admin.chat-id}")
    private Long adminChatId;

    public void route(Message message, TelegramClient bot, Long adminChatId) {
        Long chatId = message.getChatId();
        Long userId = message.getFrom().getId();
        String text = message.getText();
        BotState state = stateManager.getState(userId);

        log.debug("Routing message from {} (user {}): {}, state: {}", chatId, userId, text, state);

        if (text.startsWith("/admin") || isAdminState(state)) {
            if (userId.equals(adminChatId)) {
                if (text.startsWith("/admin") && !chatId.equals(userId)) {
                    // Admin typed /admin in a group
                    userHandler.sendMessage(chatId, "The admin panel can only be used in private messages with the bot.", bot);
                    return;
                }
                adminHandler.handle(message, bot);
            } else {
                log.warn("Unauthorized admin access attempt from user {} in chat {}", userId, chatId);
                userHandler.sendMessage(chatId, "Unauthorized: Admin only.", bot);
            }
            return;
        }

        if (text.startsWith("/") || state != BotState.DEFAULT) {
            userHandler.handle(message, bot, adminChatId);
        } else {
            userHandler.sendMessage(chatId, "Type / to see available commands.", bot);
        }
    }

    private boolean isAdminState(BotState state) {
        return state == BotState.ADMIN_MENU || state == BotState.VIEW_STATS ||
                state == BotState.VIEW_USERS || state == BotState.VIEW_GROUPS ||
                state == BotState.VIEW_EXPENSES;
    }
}
