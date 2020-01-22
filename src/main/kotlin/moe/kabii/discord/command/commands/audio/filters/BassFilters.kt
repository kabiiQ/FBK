package moe.kabii.discord.command.commands.audio.filters

object BassFilters {
    /*
    This table taken from https://github.com/Devoxin/JukeBot/blob/master/src/main/java/jukebot/audio/BassBooster.kt
       Copyright 2019 Devoxin
       Licensed under the Apache License, Version 2.0 (the "License");
       you may not use this file except in compliance with the License.
       You may obtain a copy of the License at
           http://www.apache.org/licenses/LICENSE-2.0
       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
       See the License for the specific language governing permissions and
       limitations under the License.
     */
    val gain = mapOf(
        0 to -0.05f,  // 25 Hz
        1 to 0.07f,   // 40 Hz
        2 to 0.16f,   // 63 Hz
        3 to 0.03f,   // 100 Hz
        4 to -0.05f,  // 160 Hz
        5 to -0.11f   // 250 Hz
    )
}