# SyncInv
Paper cross server player data syncing using redis and (optionally) [OpenInv](https://github.com/jikoo/OpenInv).

Provided without any (implied) support nor warranty! (See [GPLv3 license](https://github.com/Minebench/SyncInv/blob/master/LICENSE#L589))

## Requirements

- At least Java 11
- [Paper](https://papermc.io) Minecraft server software (tested on 1.16.5+, should be the same version on all synced servers)
- [redis](https://redis.io) pub-sub
- [OpenInv](https://github.com/jikoo/OpenInv) (optionally, for a more smooth experience)

## Setup

1. Install redis, Paper, SyncInv (and optionally OpenInv).
2. Configure the SyncInv [`config.yml`](https://github.com/Minebench/SyncInv/blob/master/src/main/resources/config.yml):
    1. Specify the `server-name` for each server as set in your proxy config
    2. Setup the values for the pub-sub connection to your redis server in teh `redis` section
    3. Toggle the types of data you want to sync in the `sync` section
    4. Familiarize yourself with all the [other settings](https://github.com/Minebench/SyncInv/blob/master/src/main/resources/config.yml) and adjust if necessary (the most important one probably beeing the `required-servers` list and the `server-group` name if you want multiple different sync groups in your network)

## Download

Pre-build plugin jars can be downloaded from the [Minebench.de build server](https://ci.minebench.de/job/SyncInv/).

## License

```
SyncInv - Cross server player data syncing
Copyright (C) 2021 Max Lee aka Phoenix616 (max@themoep.de)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
