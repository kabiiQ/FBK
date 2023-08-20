package moe.kabii.util.i18n

import java.util.*

object Translations {
    val defaultLocale = Locale.ENGLISH
    private val supportedLocales = listOf(
        defaultLocale
    )

    val locales: Map<Locale, FBKLocale>

    init {
        // load bundles for supported locales
        locales = supportedLocales.associateWith { locale ->
            FBKLocale(
                locale,
                ResourceBundle.getBundle("Responses", locale),
                ResourceBundle.getBundle("Trackers", locale)
            )
        }
    }
}

class FBKLocale(
    private val locale: Locale,
    private val responses: ResourceBundle,
    private val trackers: ResourceBundle
) {
    /**
     * Format a string using this locale. See String#format
     */
    fun format(format: String, vararg args: String) = String.format(locale, format, *args)
    private val variable = Regex("\\$(\\w{1,24})")

    private fun namedVariableString(base: String, variables: Array<out Pair<String, Any>>): String {
        val args = variables.toMap()
        // Match variables in string
        return variable.findAll(base)
            .fold(base) { acc, arg ->
                val key = arg.groups[1]!!.value
                val value = requireNotNull(args[key].toString()) { "Placeholder $key in property string for locale ${locale.language} not provided by code :: $base" }
                acc.replace(arg.value, value)
            }
    }

    // unnamed variable variant, fill in order
    private fun orderedVariableString(base: String, variables: Array<out Any>): String {
        return variables.fold(base) { acc, arg ->
            acc.replace(variable, arg.toString())
        }
    }

    /**
     * Gets a string containing information with variable information inserted. Base string pulled from Responses_locale.properties
     * @param identifier The identifier or key for this line
     * @param args The variables to be inserted into this string. Pair<String, String> where first is the key identifier to insert as, and second is the value to insert.
     * @return The response string, with variables inserted.
     * @throws MissingResourceException If the base string requested does not exist.
     * @throws IllegalArgumentException If the args provided are mismatched to the base string loaded from file
     */
    @Throws(MissingResourceException::class, IllegalArgumentException::class)
    fun responseString(identifier: String, vararg variables: Pair<String, Any>) = namedVariableString(responses.getString(identifier), variables)

    /**
     * Gets a string containing information with variable information inserted. See responseString variant with Pair args for detail or for named pairs.
     * Mismatched variables will not be handled in this method of insertion.
     */
    @Throws(MissingResourceException::class)
    fun responseString(identifier: String, vararg variables: Any) = orderedVariableString(responses.getString(identifier), variables)

    /**
     * Gets a string containing a string for a tracker embed which may contain variables. Pulls from Trackers_locale.properties.
     * @param identifier The identifier or key for this line.
     * @param args The variables to be inserted into this string. Pair<String, String> where first is the key identifier to insert as, and second is the value to insert.
     * @return The tracker string, with variables inserted.
     * @throws MissingResourceException If the string requested does not exist.
     * @throws IllegalArgumentException If the args provided are mismatched to the base string loaded from file
     */
    @Throws(MissingResourceException::class)
    fun trackerString(identifier: String, vararg variables: Pair<String, Any>) = namedVariableString(trackers.getString(identifier), variables)

    /**
     * Gets a string containing tracker response information with variable information inserted. See trackerString variant with Pair args for detail or for named pairs.
     * Mismatched variables will not be handled in this method of insertion. Strings without variables are also acceptable for use with this method.
     */
    @Throws(MissingResourceException::class)
    fun trackerString(identifier: String, vararg variables: Any) = orderedVariableString(trackers.getString(identifier), variables)
}
