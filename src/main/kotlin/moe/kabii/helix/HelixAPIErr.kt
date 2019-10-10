package moe.kabii.helix

sealed class HelixAPIErr // ->
object HelixIOErr : HelixAPIErr()
object EmptyObject : HelixAPIErr()