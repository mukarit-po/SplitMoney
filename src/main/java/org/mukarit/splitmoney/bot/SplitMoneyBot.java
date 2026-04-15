package org.mukarit.splitmoney.bot;

import lombok.extern.slf4j.Slf4j;
import org.mukarit.splitmoney.bot.handler.UpdateHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SplitMoneyBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final UpdateHandler updateHandler;
    private final String botToken;
    private final TelegramClient telegramClient;

    public SplitMoneyBot(UpdateHandler updateHandler, @Value("${telegram.bot.token}") String botToken) {
        this.updateHandler = updateHandler;
        this.botToken = botToken;
        this.telegramClient = new OkHttpTelegramClient(botToken);
        registerCommands();
    }

    private void registerCommands() {
        List<BotCommand> commands = new ArrayList<>();
        commands.add(new BotCommand("/start", "Start the bot"));
        commands.add(new BotCommand("/create_group", "Create a new group"));
        commands.add(new BotCommand("/join", "Join an existing group"));
        commands.add(new BotCommand("/add", "Add expense: /add {amount} {desc}"));
        commands.add(new BotCommand("/pay", "Pay member: /pay {amount} {username/name}"));
        commands.add(new BotCommand("/balance", "Check group balances"));
        commands.add(new BotCommand("/history", "View expense history"));
        commands.add(new BotCommand("/settle", "Settle all debts in group"));
        commands.add(new BotCommand("/help", "Show bot manual"));
        commands.add(new BotCommand("/admin", "Admin panel (Admin only)"));

        SetMyCommands setMyCommands = SetMyCommands.builder()
                .commands(commands)
                .scope(new BotCommandScopeDefault())
                .build();
        try {
            telegramClient.execute(setMyCommands);
            log.info("Bot commands registered successfully");
        } catch (Exception e) {
            log.error("Failed to register bot commands: ", e);
        }
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        try {
            updateHandler.handleUpdate(update, telegramClient);
        } catch (Exception e) {
            log.error("Error handling update: ", e);
        }
    }
}
