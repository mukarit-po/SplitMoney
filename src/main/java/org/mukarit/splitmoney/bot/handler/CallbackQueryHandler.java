package org.mukarit.splitmoney.bot.handler;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.mukarit.splitmoney.service.ExpenseService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackQueryHandler {

    private final AdminHandler adminHandler;
    private final ExpenseService expenseService;

    public void handle(CallbackQuery query, TelegramClient bot) {
        String data = query.getData();
        Long chatId = query.getMessage().getChatId();
        Long userId = query.getFrom().getId();
        Integer messageId = query.getMessage().getMessageId();

        log.info("Handling callback query from user {} in chat {}: {}", userId, chatId, data);

        if (data.startsWith("admin_")) {
            handleAdminCallback(data, userId, chatId, messageId, bot);
        }
    }

    private void handleAdminCallback(String data, Long userId, Long chatId, Integer messageId, TelegramClient bot) {
        if (data.equals("admin_main")) {
            adminHandler.showMainMenu(userId, chatId, bot);
        } else if (data.equals("admin_exit")) {
            adminHandler.exitAdmin(userId, chatId, messageId, bot);
        } else if (data.equals("admin_stats")) {
            adminHandler.showStats(userId, chatId, messageId, bot);
        } else if (data.startsWith("admin_users_")) {
            int page = Integer.parseInt(data.substring("admin_users_".length()));
            adminHandler.showUsers(userId, chatId, messageId, page, bot);
        } else if (data.startsWith("admin_user_view_")) {
            Long targetUserId = Long.parseLong(data.substring("admin_user_view_".length()));
            adminHandler.showUserDetail(chatId, messageId, targetUserId, bot);
        } else if (data.startsWith("admin_groups_")) {
            int page = Integer.parseInt(data.substring("admin_groups_".length()));
            adminHandler.showGroups(userId, chatId, messageId, page, bot);
        } else if (data.startsWith("admin_group_view_")) {
            Long groupId = Long.parseLong(data.substring("admin_group_view_".length()));
            adminHandler.showGroupDetail(chatId, messageId, groupId, bot);
        } else if (data.startsWith("admin_expenses_")) {
            int page = Integer.parseInt(data.substring("admin_expenses_".length()));
            adminHandler.showExpenses(userId, chatId, messageId, page, bot);
        } else if (data.startsWith("admin_expense_view_")) {
            Long expenseId = Long.parseLong(data.substring("admin_expense_view_".length()));
            adminHandler.showExpenseDetail(chatId, messageId, expenseId, bot);
        } else if (data.startsWith("admin_expense_delete_")) {
            Long expenseId = Long.parseLong(data.substring("admin_expense_delete_".length()));
            expenseService.deleteExpense(expenseId);
            adminHandler.showExpenses(userId, chatId, messageId, 0, bot);
        }
    }
}
