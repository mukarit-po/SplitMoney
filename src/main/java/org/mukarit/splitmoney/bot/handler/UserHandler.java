package org.mukarit.splitmoney.bot.handler;

import lombok.RequiredArgsConstructor;
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
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserHandler {

    private final UserService userService;
    private final GroupService groupService;
    private final ExpenseService expenseService;
    private final UserStateManager stateManager;

    public void handle(Message message, TelegramClient bot, Long adminChatId) {
        Long chatId = message.getChatId(); // The chat where the message was sent (can be a group)
        Long userId = message.getFrom().getId(); // The actual user who sent the message
        String text = message.getText();
        BotState state = stateManager.getState(userId);

        User user = userService.getOrCreateUser(userId, message.getFrom().getFirstName(), message.getFrom().getUserName());

        if (text.startsWith("/")) {
            if (state != BotState.DEFAULT) {
                stateManager.clear(userId);
                state = BotState.DEFAULT;
            }
            handleCommands(text, user, chatId, bot, adminChatId);
        } else if (state != BotState.DEFAULT) {
            handleStateInputs(text, user, chatId, bot);
        }
    }

    private void handleCommands(String text, User user, Long chatId, TelegramClient bot, Long adminChatId) {
        Long userId = user.getId();
        if (text.startsWith("/create_group")) {
            if (!userId.equals(adminChatId)) {
                sendMessage(chatId, "Only the admin can create groups.", bot);
                return;
            }
            sendMessage(chatId, "Enter group name:", bot);
            stateManager.setState(userId, BotState.WAITING_FOR_GROUP_NAME);
        } else if (text.startsWith("/join")) {
            sendMessage(chatId, "Enter group ID:", bot);
            stateManager.setState(userId, BotState.WAITING_FOR_GROUP_ID);
        } else if (text.startsWith("/add")) {
            if (user.getCurrentGroupId() == null) {
                sendMessage(chatId, "Please join or create a group first.", bot);
                return;
            }
            String[] parts = text.split("\\s+", 3);
            if (parts.length < 3) {
                sendMessage(chatId, "Format: /add {amount} {description}\nExample: /add 50.0 Dinner", bot);
                return;
            }
            try {
                BigDecimal amount = new BigDecimal(parts[1]);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    sendMessage(chatId, "Amount must be positive.", bot);
                    return;
                }
                String description = parts[2];
                Long groupId = user.getCurrentGroupId();
                groupService.getGroup(groupId).ifPresent(g -> {
                    List<Long> members = g.getMembers().stream().map(User::getId).collect(Collectors.toList());
                    expenseService.addExpense(user, amount, description, groupId, members);
                    sendMessage(chatId, String.format("Expense added: %s for %s", formatAmount(amount), description), bot);
                });
            } catch (NumberFormatException e) {
                sendMessage(chatId, "Invalid amount format. Use numbers like 10", bot);
            }
        } else if (text.startsWith("/pay")) {
            handlePay(text, user, chatId, bot);
        } else if (text.startsWith("/balance")) {
            handleBalance(user, chatId, bot);
        } else if (text.startsWith("/history")) {
            handleHistory(user, chatId, bot);
        } else if (text.startsWith("/settle")) {
            handleSettle(user, chatId, bot, adminChatId);
        } else if (text.startsWith("/help")) {
            handleHelp(chatId, bot);
        } else if (text.startsWith("/start")) {
            sendMessage(chatId, "Welcome to SplitMoney! Use /create_group or /join to begin.", bot);
        }
    }

    private void handleStateInputs(String text, User user, Long chatId, TelegramClient bot) {
        Long userId = user.getId();
        BotState state = stateManager.getState(userId);

        switch (state) {
            case WAITING_FOR_GROUP_NAME:
                Group group = groupService.createGroup(text, userId);
                sendMessage(chatId, "Group created! ID: " + group.getId(), bot);
                stateManager.clear(userId);
                break;
            case WAITING_FOR_GROUP_ID:
                try {
                    Long groupId = Long.parseLong(text);
                    if (groupService.joinGroup(groupId, userId)) {
                        sendMessage(chatId, "Joined group!", bot);
                    } else {
                        sendMessage(chatId, "Group not found.", bot);
                    }
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Invalid ID format.", bot);
                }
                stateManager.clear(userId);
                break;
        }
    }

    private void handleBalance(User user, Long chatId, TelegramClient bot) {
        if (user.getCurrentGroupId() == null) {
            sendMessage(chatId, "No group active.", bot);
            return;
        }
        groupService.getGroup(user.getCurrentGroupId()).ifPresent(g -> {
            Map<Long, Map<Long, BigDecimal>> debts = expenseService.calculateBalances(g.getId(), List.copyOf(g.getMembers()));
            if (debts.isEmpty()) {
                sendMessage(chatId, "Everyone is settled up!", bot);
                return;
            }
            StringBuilder sb = new StringBuilder("Balances:\n");
            debts.forEach((debtorId, creditors) -> {
                User debtor = userService.getUser(debtorId).get();
                creditors.forEach((creditorId, amount) -> {
                    User creditor = userService.getUser(creditorId).get();
                    sb.append(String.format("%s owes %s %s\n", debtor.getName(), creditor.getName(), formatAmount(amount)));
                });
            });
            sendMessage(chatId, sb.toString(), bot);
        });
    }

    private void handleHistory(User user, Long chatId, TelegramClient bot) {
        if (user.getCurrentGroupId() == null) {
            sendMessage(chatId, "No group active.", bot);
            return;
        }
        List<Expense> history = expenseService.getGroupHistory(user.getCurrentGroupId());
        if (history.isEmpty()) {
            sendMessage(chatId, "No expenses yet.", bot);
            return;
        }
        StringBuilder sb = new StringBuilder("Recent Expenses:\n");
        history.stream().limit(10).forEach(e -> {
            sb.append(String.format("- %s: %s by %s (%s)\n", e.getDescription(), formatAmount(e.getAmount()), e.getPayer().getName(), e.getCreatedAt().toLocalDate()));
        });
        sendMessage(chatId, sb.toString(), bot);
    }

    private void handleSettle(User user, Long chatId, TelegramClient bot, Long adminChatId) {
        if (!user.getId().equals(adminChatId)) {
            sendMessage(chatId, "Only the admin can settle all debts.", bot);
            return;
        }
        // Simple settlement logic: delete all expenses in group
        if (user.getCurrentGroupId() == null) {
            sendMessage(chatId, "No group active.", bot);
            return;
        }
        expenseService.getGroupHistory(user.getCurrentGroupId()).forEach(e -> expenseService.deleteExpense(e.getId()));
        sendMessage(chatId, "All debts in current group have been settled (records cleared).", bot);
    }

    private void handleHelp(Long chatId, TelegramClient bot) {
        String helpText = "🤖 *SplitMoney Bot Manual*\n\n" +
                "This bot helps you track shared expenses with roommates or friends.\n\n" +
                "📍 *Getting Started:*\n" +
                "1. Admin creates a group using `/create_group`.\n" +
                "2. Share the Group ID with your friends.\n" +
                "3. They can join using `/join`.\n\n" +
                "💸 *Commands:*\n" +
                "• `/add {amount} {description}` - Add a new expense (e.g. `/add 50 Dinner`). It will be split equally among all members.\n" +
                "• `/pay {amount} {username/name}` - Record a payment to a member (e.g. `/pay 20 @user` or `/pay 20 Ali`).\n" +
                "• `/balance` - See who owes whom in the current group.\n" +
                "• `/history` - View the last 10 expenses.\n" +
                "• `/settle` - [Admin only] Clear all expenses and reset balances.\n\n" +
                "⚙️ *Admin:* \n" +
                "• `/admin` - Access the admin panel (restricted access).";
        
        SendMessage sm = SendMessage.builder()
                .chatId(chatId.toString())
                .text(helpText)
                .parseMode("Markdown")
                .build();
        try {
            bot.execute(sm);
        } catch (Exception e) {
            // log error
        }
    }

    private void handlePay(String text, User user, Long chatId, TelegramClient bot) {
        if (user.getCurrentGroupId() == null) {
            sendMessage(chatId, "Please join or create a group first.", bot);
            return;
        }
        String[] parts = text.split("\\s+");
        if (parts.length < 3) {
            sendMessage(chatId, "Format: /pay {amount} {username/name}\nExample: /pay 20 @ali or /pay 20 Ali", bot);
            return;
        }
        try {
            BigDecimal amount = new BigDecimal(parts[1]);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                sendMessage(chatId, "Amount must be positive.", bot);
                return;
            }
            String targetIdentifier = parts[2];
            if (targetIdentifier.startsWith("@")) {
                userService.getUserByUsername(targetIdentifier).ifPresentOrElse(targetUser -> {
                    recordPayment(user, targetUser, amount, chatId, bot);
                }, () -> sendMessage(chatId, "User not found with username " + targetIdentifier, bot));
            } else {
                // Try searching by name within the group
                groupService.getGroup(user.getCurrentGroupId()).ifPresent(group -> {
                    List<User> matchingMembers = group.getMembers().stream()
                            .filter(m -> m.getName() != null && m.getName().toLowerCase().contains(targetIdentifier.toLowerCase()))
                            .collect(Collectors.toList());

                    if (matchingMembers.isEmpty()) {
                        sendMessage(chatId, "Member not found with name: " + targetIdentifier, bot);
                    } else if (matchingMembers.size() > 1) {
                        sendMessage(chatId, "Multiple members found with name: " + targetIdentifier + ". Please be more specific.", bot);
                    } else {
                        recordPayment(user, matchingMembers.get(0), amount, chatId, bot);
                    }
                });
            }
        } catch (NumberFormatException e) {
            sendMessage(chatId, "Invalid amount format.", bot);
        }
    }

    private void recordPayment(User user, User targetUser, BigDecimal amount, Long chatId, TelegramClient bot) {
        expenseService.addPayment(user, targetUser, amount, user.getCurrentGroupId());
        sendMessage(chatId, String.format("Payment recorded: %s to %s", formatAmount(amount), targetUser.getName()), bot);
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0";
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.toPlainString();
    }

    public void sendMessage(Long chatId, String text, TelegramClient bot) {
        SendMessage sm = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build();
        try {
            bot.execute(sm);
        } catch (Exception e) {
            // log error
        }
    }
}
