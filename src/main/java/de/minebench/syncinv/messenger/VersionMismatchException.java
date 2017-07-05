package de.minebench.syncinv.messenger;

/*
 * Copyright 2017 Phoenix616 All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation,  version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
class VersionMismatchException extends Exception {
    private final int receivedVersion;
    private final int supportedVersion;

    public VersionMismatchException(int receivedVersion, int supportedVersion, String message) {
        super(message);
        this.receivedVersion = receivedVersion;
        this.supportedVersion = supportedVersion;
    }

    public int getReceivedVersion() {
        return receivedVersion;
    }

    public int getSupportedVersion() {
        return supportedVersion;
    }
}
