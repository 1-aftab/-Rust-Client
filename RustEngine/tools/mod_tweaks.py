#!/usr/bin/env python3
"""Rust Client's changes to the built-in mods' sources, made after they are fetched.

Free placement: the FPS Counter, Keystrokes and the Minimap can only sit in one of four corners.
Here each becomes a box the player drags anywhere on the screen, with the launcher's "Move HUD"
screen (game menu of the Rust Edition; RustHudEditor.kt). The box is drawn where it was and then
moved (engine patch 0010: `extras_hud::FreeHud`, `MINIMAP_AT`), so the mods' own drawing code is
untouched. The place is kept in the mod's settings folder as `rc_place`, and where the box was
drawn is told to the launcher in `rc_box`. Each settings page (Options > Mods > the mod >
Settings) also gets three buttons: "Position: Corner/Free", "Across: N%" and "Down: N%".

Every change is an exact text replacement in a pinned version of the mod; a text that is no
longer there stops the build, so a new version of a mod cannot be mis-patched silently.
Test changes against the pinned versions' sources, not the head of horizon-mods.
"""
import pathlib
import sys

PLACE = '''
// Rust Client: free placement (RustEngine/tools/mod_tweaks.py of the launcher).
thread_local! {
    pub static RC: horizon_client::extras_hud::FreeHud =
        const { horizon_client::extras_hud::FreeHud::new(RC_ACROSS, RC_DOWN) };
}
'''

PAGE = "    buttons.extend(m::settings(|key| game.native_setting(m::ID, key)).labels());\n"
PAGE_MORE = "    buttons.extend(m::RC.with(|rc| rc.place(|key| game.native_setting(m::ID, key))).labels());\n"

CLICK = "    let mut settings = m::settings(|key| game.native_setting(m::ID, key));\n    settings.click(button);\n"
CLICK_FIRST = '''    let rc_first = m::settings(|key| game.native_setting(m::ID, key)).labels().len();
    if button >= rc_first {
        m::RC.with(|rc| {
            let place = rc.place(|key| game.native_setting(m::ID, key)).click(button - rc_first);
            rc.set(place, |key, value| game.native_set_setting(m::ID, key, value));
        });
        return;
    }
'''

HUD_START = "    use horizon_client::extras::Corner;\n"
HUD_END = '''    m::RC.with(|rc| {
        rc.settle(
            batch,
            corners,
            rc_start,
            |key| game.native_setting(m::ID, key),
            |key, value| game.native_set_setting(m::ID, key, value),
        )
    });
}
'''

MAP_OFF = "    use horizon_client::extras::Corner;\n"
MAP_SETTINGS = "    let settings = m::settings(|key| game.native_setting(m::ID, key));\n"
MAP_AT = '''    m::RC.with(|rc| {
        let place = rc.place(|key| game.native_setting(m::ID, key));
        horizon_client::extras_hud::MINIMAP_AT
            .with(|at| at.set(place.free.then(|| (place.across / 100.0, place.down / 100.0))));
        if let Some(drawn) = horizon_client::extras_hud::MINIMAP_BOX.with(|b| b.get()) {
            rc.tell(drawn, |key, value| game.native_set_setting(m::ID, key, value));
        }
    });
'''


def fail(text):
    print(f"::error::mod_tweaks: {text}")
    sys.exit(f"mod_tweaks: {text}")


def replace(path, old, new, last=False):
    text = path.read_text(encoding="utf-8")
    if text.count(old) < 1:
        fail(f"{path.parent.name}/{path.name} no longer has: {old.strip()[:80]}")
    if last:
        at = text.rindex(old)
        text = text[:at] + new + text[at + len(old):]
    else:
        text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")


def free_placement(bundle, across, down, hud=None, minimap=None):
    runtime = bundle / "runtime.rs"
    runtime.write_text(
        runtime.read_text(encoding="utf-8")
        + f"\nconst RC_ACROSS: f32 = {across:.1f};\nconst RC_DOWN: f32 = {down:.1f};\n" + PLACE,
        encoding="utf-8")
    replace(bundle / "settings.rs", PAGE, PAGE + PAGE_MORE)
    replace(bundle / "click.rs", CLICK, CLICK_FIRST + CLICK)
    if hud:
        replace(bundle / hud, HUD_START, HUD_START + "    let rc_start = batch.vertices.len();\n")
        replace(bundle / hud, "}\n", HUD_END, last=True)
    if minimap:
        replace(bundle / minimap, MAP_OFF,
                MAP_OFF + "    horizon_client::extras_hud::MINIMAP_AT.with(|at| at.set(None));\n")
        replace(bundle / minimap, MAP_SETTINGS, MAP_SETTINGS + MAP_AT)


TWEAKS = {
    "fps_overlay": lambda b: free_placement(b, 0, 0, hud="hud.rs"),
    "keystrokes": lambda b: free_placement(b, 0, 40, hud="hud.rs"),
    "minimap": lambda b: free_placement(b, 100, 0, minimap="minimap.rs"),
}


def apply(name, bundle):
    """Rust Client's changes to mod `name`, whose bundle was unpacked in `bundle`."""
    tweak = TWEAKS.get(name)
    if tweak:
        tweak(pathlib.Path(bundle))
        print(f"mod_tweaks: {name}: free placement", file=sys.stderr)
