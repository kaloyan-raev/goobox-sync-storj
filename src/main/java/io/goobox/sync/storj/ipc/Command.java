/*
 * Copyright (C) 2018 Kaloyan Raev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.goobox.sync.storj.ipc;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Command {

    private static final Logger logger = LoggerFactory.getLogger(Command.class);

    private String method;
    private Map<String, String> args;

    public CommandResult execute() {
        if ("login".equals(method)) {
            return new LoginRequest(args.get("email"), args.get("password"), args.get("encryptionKey")).execute();
        } else if ("createAccount".equals(method)) {
            return new CreateAccountRequest(args.get("email"), args.get("password")).execute();
        } else {
            String msg = "Invalid command method: " + method;
            logger.error(msg);
            return new CommandResult(Status.ERROR, msg);
        }
    }

}