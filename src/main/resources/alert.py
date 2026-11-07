"""Get the player's attention from a Minescript script. Installed by the Minescript Addon mod (overwritten on launch).

    import alert
    alert.alert("Inventory full")                         # bell, toast, taskbar flash if the window isn't focused
    alert.alert("Farm stopped", repeat=3, chat=True)      # ring 3 times, 0.5 s apart, and add a chat line
    alert.alert("Done", sound_id="minecraft:entity.experience_orb.pickup", volume=0.5, flash=False)

Everything is client-side: the sound plays only for you and the chat line is never sent to the server.
Nothing raises back into the script; parts that can't run (e.g. chat outside a world) are skipped and logged.
"""

from java import JavaClass

_Alert = JavaClass("com.niklas2233.minescriptaddon.MinescriptAlert")

DEFAULT_SOUND = "minecraft:block.note_block.bell"


def alert(text: str, sound: bool = True, toast: bool = True, flash: bool = True, chat: bool = False,
          repeat: int = 1, title: str = "Minescript", sound_id: str = DEFAULT_SOUND,
          volume: float = 1.0, pitch: float = 1.0):
    """Show text as a toast titled title, play sound_id repeat times (max 20) about 0.5 s apart, flash the
    window in the taskbar when it isn't focused, and with chat=True also add text to the chat box.
    An unknown sound_id plays the bell instead and logs a warning."""
    _Alert.alert(str(title), str(text), bool(sound), str(sound_id), float(volume), float(pitch), int(repeat),
                 bool(toast), bool(flash), bool(chat))
