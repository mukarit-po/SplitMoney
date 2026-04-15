package org.mukarit.splitmoney.bot.handler;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.mukarit.splitmoney.bot.keyboard.InlineKeyboardBuilder;
import org.mukarit.splitmoney.bot.state.BotState;
import org.mukarit.splitmoney.bot.state.UserStateManager;
import org.mukarit.splitmoney.entity.Expense;
import org.mukarit.splitmoney.entity.Group;
import org.mukarit.splitmoney.entity.User;
import org.mukarit.splitmoney.service.ExpenseService;
import org.mukarit.splitmoney.service.GroupService;
import org.mukarit.splitmoney.service.UserService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminHandler {

    private final UserService userService;
    private final GroupService groupService;
    private final ExpenseService expenseService;
    private final UserStateManager stateManager;

    public void handle(Message message, TelegramClient bot) {
        showMainMenu(message.getFrom().getId(), message.getChatId(), bot);
    }

    public void showMainMenu(Long userId, Long chatId, TelegramClient bot) {
        log.info("Showing admin main menu to user {} in chat {}", userId, chatId);
        stateManager.setState(userId, BotState.ADMIN_MENU);
        InlineKeyboardMarkup markup = new InlineKeyboardBuilder()
                .addButton("📊 Stats", "admin_stats").nextRow()
                .addButton("👥 Users", "admin_users_0").nextRow()
                .addButton("🏠 Groups", "admin_groups_0").nextRow()
                .addButton("💸 Expenses", "admin_expenses_0").nextRow()
                .addButton("🔙 Exit Admin", "admin_exit").nextRow()
                .build();

        sendMenu(chatId, "Admin Panel Main Menu:", markup, bot);
    }

    public void showStats(Long userId, Long chatId, Integer messageId, TelegramClient bot) {
        stateManager.setState(userId, BotState.VIEW_STATS);
        long userCount = userService.getAllUsers().size();
        long groupCount = groupService.getAllGroups().size();
        List<Expense> expenses = expenseService.getAllExpenses();
        BigDecimal totalMoney = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String text = String.format("📊 Stats:\nTotal Users: %d\nTotal Groups: %d\nTotal Expenses: %d\nTotal Money: %s",
                userCount, groupCount, expenses.size(), formatAmount(totalMoney));

        InlineKeyboardMarkup markup = new InlineKeyboardBuilder()
                .addButton("🔙 Back", "admin_main")
                .build();

        editMenu(chatId, messageId, text, markup, bot);
    }

    public void showUsers(Long userId, Long chatId, Integer messageId, int page, TelegramClient bot) {
        stateManager.setState(userId, BotState.VIEW_USERS);
        List<User> users = userService.getAllUsers();
        int pageSize = 5;
        int totalPages = (int) Math.ceil((double) users.size() / pageSize);
        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, users.size());

        StringBuilder sb = new StringBuilder("👥 Users (Page " + (page + 1) + "/" + totalPages + "):\n");
        InlineKeyboardBuilder builder = new InlineKeyboardBuilder();
        
        if (fromIndex < users.size()) {
            for (User u : users.subList(fromIndex, toIndex)) {
                builder.addButton(u.getName(), "admin_user_view_" + u.getId()).nextRow();
            }
        }

        if (page > 0) builder.addButton("⬅️ Prev", "admin_users_" + (page - 1));
        if (toIndex < users.size()) builder.addButton("Next ➡️", "admin_users_" + (page + 1));
        
        builder.nextRow().addButton("🔙 Back", "admin_main");

        editMenu(chatId, messageId, sb.toString(), builder.build(), bot);
    }

    public void showUserDetail(Long chatId, Integer messageId, Long targetUserId, TelegramClient bot) {
        userService.getUser(targetUserId).ifPresent(u -> {
            String text = String.format("User Details:\nName: %s\nID: %d\nGroup: %s",
                    u.getName(), u.getId(), u.getCurrentGroupId());
            InlineKeyboardMarkup markup = new InlineKeyboardBuilder()
                    .addButton("🔙 Back", "admin_users_0")
                    .build();
            editMenu(chatId, messageId, text, markup, bot);
        });
    }

    public void showGroups(Long userId, Long chatId, Integer messageId, int page, TelegramClient bot) {
        stateManager.setState(userId, BotState.VIEW_GROUPS);
        List<Group> groups = groupService.getAllGroups();
        int pageSize = 5;
        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, groups.size());

        StringBuilder sb = new StringBuilder("🏠 Groups:\n");
        InlineKeyboardBuilder builder = new InlineKeyboardBuilder();
        for (Group g : groups.subList(fromIndex, toIndex)) {
            builder.addButton(g.getName(), "admin_group_view_" + g.getId()).nextRow();
        }

        if (page > 0) builder.addButton("⬅️ Prev", "admin_groups_" + (page - 1));
        if (toIndex < groups.size()) builder.addButton("Next ➡️", "admin_groups_" + (page + 1));
        builder.nextRow().addButton("🔙 Back", "admin_main");

        editMenu(chatId, messageId, sb.toString(), builder.build(), bot);
    }

    public void showGroupDetail(Long chatId, Integer messageId, Long groupId, TelegramClient bot) {
        groupService.getGroup(groupId).ifPresent(g -> {
            String text = String.format("Group Details:\nName: %s\nID: %d\nMembers: %d",
                    g.getName(), g.getId(), g.getMembers().size());
            InlineKeyboardMarkup markup = new InlineKeyboardBuilder()
                    .addButton("🔙 Back", "admin_groups_0")
                    .build();
            editMenu(chatId, messageId, text, markup, bot);
        });
    }

    public void showExpenses(Long userId, Long chatId, Integer messageId, int page, TelegramClient bot) {
        stateManager.setState(userId, BotState.VIEW_EXPENSES);
        List<Expense> expenses = expenseService.getAllExpenses();
        int pageSize = 5;
        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, expenses.size());

        StringBuilder sb = new StringBuilder("💸 Recent Expenses:\n");
        InlineKeyboardBuilder builder = new InlineKeyboardBuilder();
        for (Expense e : expenses.subList(fromIndex, toIndex)) {
            builder.addButton(e.getDescription() + " (" + e.getAmount() + ")", "admin_expense_view_" + e.getId()).nextRow();
        }

        if (page > 0) builder.addButton("⬅️ Prev", "admin_expenses_" + (page - 1));
        if (toIndex < expenses.size()) builder.addButton("Next ➡️", "admin_expenses_" + (page + 1));
        builder.nextRow().addButton("🔙 Back", "admin_main");

        editMenu(chatId, messageId, sb.toString(), builder.build(), bot);
    }

    public void showExpenseDetail(Long chatId, Integer messageId, Long expenseId, TelegramClient bot) {
        expenseService.getAllExpenses().stream().filter(e -> e.getId().equals(expenseId)).findFirst().ifPresent(e -> {
            String text = String.format("Expense Detail:\nPayer: %s\nAmount: %s\nDesc: %s\nDate: %s",
                    e.getPayer().getName(), formatAmount(e.getAmount()), e.getDescription(), e.getCreatedAt());
            InlineKeyboardMarkup markup = new InlineKeyboardBuilder()
                    .addButton("❌ Delete", "admin_expense_delete_" + e.getId()).nextRow()
                    .addButton("🔙 Back", "admin_expenses_0")
                    .build();
            editMenu(chatId, messageId, text, markup, bot);
        });
    }

    public void exitAdmin(Long userId, Long chatId, Integer messageId, TelegramClient bot) {
        stateManager.clear(userId);
        editMenu(chatId, messageId, "Exited Admin Panel.", null, bot);
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0";
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.toPlainString();
    }

    private void sendMenu(Long chatId, String text, InlineKeyboardMarkup markup, TelegramClient bot) {
        SendMessage sm = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(markup)
                .build();
        try {
            bot.execute(sm);
        } catch (Exception ex) {
            log.error("Error sending menu to {}: {}", chatId, ex.getMessage());
        }
    }

    private void editMenu(Long chatId, Integer messageId, String text, InlineKeyboardMarkup markup, TelegramClient bot) {
        EditMessageText em = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text(text)
                .replyMarkup(markup)
                .build();
        try {
            bot.execute(em);
        } catch (Exception ex) {
            log.error("Error editing menu for {} (msg {}): {}", chatId, messageId, ex.getMessage());
        }
    }
}
