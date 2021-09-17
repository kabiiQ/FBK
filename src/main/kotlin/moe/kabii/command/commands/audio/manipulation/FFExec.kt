package moe.kabii.command.commands.audio.manipulation

import com.github.kokorin.jaffree.ffmpeg.ChannelOutput
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffmpeg.UrlInput
import discord4j.core.spec.MessageCreateFields
import discord4j.core.spec.MessageCreateSpec
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.verifyBotAdmin
import moe.kabii.discord.util.Embeds
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.apache.commons.io.FilenameUtils
import java.io.ByteArrayInputStream
import java.net.URL

object FFExec : Command("ffmpeg") {
    override val wikiPath: String? = null

    init {
        discord {
            event.verifyBotAdmin()
            if(args.isEmpty()) {
                reply(Embeds.error("No arguments specified.")).awaitSingle()
                return@discord
            }

            val arguments = args.toMutableList()

            val attachment = event.message.attachments.firstOrNull()
            val (url, inname) = if(attachment != null) attachment.url to attachment.filename else {
                try {
                    val url = args.first()
                    val path = FilenameUtils.getName(URL(url).path)
                    arguments.removeFirst()
                    url to path
                } catch(e: Exception) {
                    reply(Embeds.error("Unable to get audio attachment.")).awaitSingle()
                    return@discord
                }
            }

            val namearg = args.last()
            val outargs = namearg.split(".")
            val outname = if(outargs.size == 2) {
                arguments.removeLast()
                namearg
            } else inname

            val os = SeekableInMemoryByteChannel(ByteArray(800_000))
            try {
                FFmpeg.atPath()
                    .addInput(UrlInput.fromUrl(url))
                    .apply { arguments.forEach(::addArgument) }
                    .addOutput(
                        ChannelOutput.toChannel(outname, os)
                    )
                    .execute()

                val stream = ByteArrayInputStream(os.array())
                reply(
                    MessageCreateSpec.create().withFiles(MessageCreateFields.File.of(outname, stream))
                ).awaitSingle()
            } finally {
                os.close()
            }
        }
    }
}