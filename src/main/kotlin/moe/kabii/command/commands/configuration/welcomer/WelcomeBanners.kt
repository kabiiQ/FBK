package moe.kabii.command.commands.configuration.welcomer

import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent
import discord4j.core.`object`.component.ActionRow
import discord4j.core.`object`.component.SelectMenu
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.MessageCreateFields
import discord4j.rest.util.Image
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import moe.kabii.command.Command
import moe.kabii.command.params.DiscordParameters
import moe.kabii.command.verify
import moe.kabii.discord.util.Embeds
import moe.kabii.rusty.Err
import moe.kabii.rusty.Ok
import moe.kabii.util.extensions.awaitAction
import moe.kabii.util.extensions.orNull
import moe.kabii.util.extensions.toAutoCompleteSuggestions
import java.io.File
import java.time.Duration

object WelcomeBanners : Command("welcomebanners") {
    override val wikiPath: String? = null

    init {
        autoComplete {
            val guildId = event.interaction.guildId.orNull() ?: return@autoComplete
            val banners = WelcomeBannerUtil.getBanners(guildId.asLong())
            suggest(
                banners
                    .map(File::getName)
                    .toAutoCompleteSuggestions()
            )
        }

        chat {
            member.verify(Permission.MANAGE_CHANNELS)
            when(subCommand.name) {
                "add" -> WelcomeBanners::addBannerImage
                "list" -> WelcomeBanners::listBannerImages
                "remove" -> WelcomeBanners::removeBannerImage
                else -> error("subcommand mismatch")
            }.invoke(this)
        }
    }

    private suspend fun addBannerImage(origin: DiscordParameters) = with(origin) {
        // welcomebanners add <image>
        event
            .deferReply()
            .withEphemeral(true)
            .awaitAction()
        val attachment = interaction.commandInteraction.orNull()?.resolved?.orNull()?.attachments?.values?.firstOrNull()
        when(val validation = WelcomeBannerUtil.verifySaveImage(this, attachment)) {
            is Ok -> event
                .editReply()
                .withEmbeds(Embeds.fbk("Banner image has been uploaded: **${validation.value}**"))
            is Err -> event
                .editReply()
                .withEmbeds(Embeds.error(validation.value))
        }.awaitSingle()
    }

    private suspend fun listBannerImages(origin: DiscordParameters) = with(origin) {
        // welcomebanners view: display list of uploaded banners with dropdown to retrieve image files
        val banners = WelcomeBannerUtil.getBanners(target.id.asLong())
        if(banners.isEmpty()) {
            ereply(Embeds.error("There are no uploaded welcome banners for **${target.name}**.")).awaitSingle()
            return@with
        }

        // display list of banners
        val bannerList = banners
            .mapIndexed { i, file -> "${i + 1}. ${file.name}" }
            .joinToString("\n")
        val bannerOptions = banners.map { file ->
            SelectMenu.Option
                .of(file.name, file.name)
                .withDefault(false)
        }

        val dropdown = SelectMenu
            .of("banners", bannerOptions)
            .withMaxValues(bannerOptions.size)
            .run { ActionRow.of(this) }

        val view = Embeds.fbk("$bannerList\n\nA random banner image from this list will be selected each time a user joins.\n\nSelect banners below if you would like to download the image files:")
            .withAuthor(EmbedCreateFields.Author.of("Uploaded banner images for ${target.name}", null, target.getIconUrl(Image.Format.PNG).orNull()))

        event
            .reply()
            .withEmbeds(view)
            .withEphemeral(true)
            .withComponents(dropdown)
            .awaitAction()

        val response = listener(SelectMenuInteractionEvent::class, true, Duration.ofMinutes(5), "banners")
            .switchIfEmpty {
                event.deleteReply()
            }
            .awaitFirstOrNull() ?: return@with

        // depending on file size and number selected - could take a second to upload images - acknowledge interaction immediately
        response
            .deferEdit()
            .withEphemeral(true)
            .awaitAction()

        val selectedBanners = response.values
            .map { selection ->
                banners.first { b -> b.name == selection }
            }
        // upload the selected banners for the user
        val files = selectedBanners.map { banner ->
            MessageCreateFields.File.of(banner.name, banner.inputStream())
        }

        event.editReply()
            .withFiles(files)
            .withComponentsOrNull(null)
            .withEmbedsOrNull(null)
            .awaitSingle()
    }

    private suspend fun removeBannerImage(origin: DiscordParameters) = with(origin) {
        val args = subArgs(subCommand)
        // user should generally click autocompleted filename - but validate
        val banners = WelcomeBannerUtil.getBanners(target.id.asLong())
        val deleteArg = args.string("file")
        val deleteBanner = banners.find { b ->
            b.name == deleteArg
        }

        if(deleteBanner == null) {
            ereply(Embeds.error("Invalid banner filename **$deleteArg**. Select an existing uploaded banner from the auto-completed options")).awaitSingle()
            return@with
        }

        deleteBanner.delete()
        ereply(Embeds.fbk("Welcome banner **${deleteBanner.name}** has been deleted.")).awaitSingle()
    }
}