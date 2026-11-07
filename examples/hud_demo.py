"""In-game check for Minescript HUD. Not installed by the mod: copy to .minecraft/minescript/ and run \\hud_demo.

Draws a small "demo." panel for ~10 s (redrawn with ttl=2, so it also vanishes ~2 s after a \\killjob),
then clears it. Each kind of Java call is reported in chat once: OK, or the exception it raised.
"""
import time

import minescript

try:
    import hud
except Exception as e:  # mod missing, or the java bridge failed
    minescript.echo(f"[hud_demo] import hud FAILED: {type(e).__name__}: {e}")
    raise

reported = set()


def call(name, fn, *args, **kwargs):
    try:
        result = fn(*args, **kwargs)
        ok = f"OK -> {result!r}" if result is not None else "OK"
    except Exception as e:
        ok = f"FAILED: {type(e).__name__}: {e}"
    if name not in reported:
        reported.add(name)
        minescript.echo(f"[hud_demo] {name}: {ok}")


call("screen_size()", hud.screen_size)
call("text_width()", hud.text_width, "Minescript HUD")
call("old 6-arg text overload", hud._Hud.text, "demo.old", 8, 46, "old overload", -1, True)

end = time.time() + 10
try:
    while time.time() < end:
        left = end - time.time()
        call("rect(ttl=2)", hud.rect, "demo.bg", 4, 4, 130, 40, 0x000000, alpha=160, ttl=2)
        call("text(ttl=2)", hud.text, "demo.title", 8, 8, "§bMinescript HUD demo", ttl=2)
        call("text(alpha=255)", hud.text, "demo.timer", 8, 20, f"closing in {left:4.1f}s", 0xFFFF55, alpha=255, ttl=2)
        call("text(alpha=0) invisible", hud.text, "demo.hidden", 8, 32, "you should NOT see this", alpha=0, ttl=2)
        call("item(ttl=2)", hud.item, "demo.item", 112, 16, "minecraft:diamond", count=3, ttl=2)
        time.sleep(0.25)
finally:
    call("clear('demo.')", hud.clear, "demo.")
    minescript.echo("[hud_demo] done; the panel should be gone")
