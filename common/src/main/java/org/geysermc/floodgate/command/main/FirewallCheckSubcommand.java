/*
 * Copyright (c) 2019-2021 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Floodgate
 */

package org.geysermc.floodgate.command.main;

import static org.geysermc.floodgate.time.SntpClientUtils.requestTimeOffset;
import static org.geysermc.floodgate.util.Constants.COLOR_CHAR;

import cloud.commandframework.context.CommandContext;
import com.google.gson.JsonElement;
import it.unimi.dsi.fastutil.Pair;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.geysermc.floodgate.player.UserAudience;
import org.geysermc.floodgate.util.Constants;
import org.geysermc.floodgate.util.HttpUtils;
import org.geysermc.floodgate.util.HttpUtils.HttpResponse;
import org.geysermc.floodgate.util.Utils;

final class FirewallCheckSubcommand {
    private FirewallCheckSubcommand() {}

    static void executeFirewall(CommandContext<UserAudience> context) {
        UserAudience sender = context.getSender();
        executeChecks(
                globalApiCheck(sender),
                timeSyncCheck(sender)
        ).whenComplete((response, $) ->
                sender.sendMessage(String.format(
                        COLOR_CHAR + "eThe checks have finished. %s/%s were successful",
                        response.left(), response.left() + response.right()
                ))
        );
    }

    private static BooleanSupplier globalApiCheck(UserAudience sender) {
        return executeFirewallText(
                sender, "global api",
                () -> {
                    HttpResponse<JsonElement> response =
                            HttpUtils.get(Constants.HEALTH_URL, JsonElement.class);

                    if (!response.isCodeOk()) {
                        throw new IllegalStateException(String.format(
                                "Didn't receive an 'ok' http code. Got: %s, response: %s",
                                response.getHttpCode(), response.getResponse()
                        ));
                    }
                });
    }

    private static BooleanSupplier timeSyncCheck(UserAudience sender) {
        return executeFirewallText(sender, "time sync", () -> {
            // UDP packets can get lost
            for (int i = 0; i < 3; i++) {
                if (requestTimeOffset(Constants.NTP_SERVER, 2000) != Long.MIN_VALUE) {
                    return;
                }
            }
            throw new IllegalStateException("Failed to receive time offset");
        });
    }

    private static BooleanSupplier executeFirewallText(
            UserAudience sender, String name, Runnable runnable) {
        return () -> {
            sender.sendMessage(COLOR_CHAR + "eTesting " + name + "...");
            try {
                runnable.run();
                sender.sendMessage(COLOR_CHAR + "aWas able to connect to " + name + "!");
                return true;
            } catch (Exception e) {
                sender.sendMessage(COLOR_CHAR + "cFailed to connect:");
                sender.sendMessage(Utils.getStackTrace(e));
                return false;
            }
        };
    }

    private static CompletableFuture<Pair<Integer, Integer>> executeChecks(
            BooleanSupplier... checks) {

        return CompletableFuture.supplyAsync(() -> {
            AtomicInteger okCount = new AtomicInteger();
            AtomicInteger failCount = new AtomicInteger();

            for (BooleanSupplier check : checks) {
                if (check.getAsBoolean()) {
                    okCount.getAndIncrement();
                    continue;
                }
                failCount.getAndIncrement();
            }

            return Pair.of(okCount.get(), failCount.get());
        });
    }
}
