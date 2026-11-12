#!/usr/bin/env python3
"""Rust Client's changes to the built-in mods' sources, made after they are fetched.

Free placement: the FPS Counter, Keystrokes and the Minimap can only sit in one of four corners.
Each gets three more buttons on its settings page (Options > Mods > the mod > Settings):
"Position: Corner/Free", "Across: N%" and "Down: N%". With Free, the box is drawn where it was
and then moved (engine patch 0010: `extras_hud::place_free`, `MINIMAP_AT`), so the mods' own
drawing code is untouched. The values are kept in the mod's settings folder as `rc_free`,
`rc_across` and `rc_down`.

Every change is an exact text replacement in a pinned version of the mod; a text that is no
longer there stops the build, so a new version of a mod cannot be mis-patched silently.
"""
import pathlib
import sys

PLACE = '''
// Rust Client: free placement (RustEngine/tools/mod_tweaks.py of the launcher).
thread_local! {
    static RC_PLACE: std::cell::Cell<Option<(bool, u8, u8)>> = const { std::cell::Cell::new(None) };
}
/// (free, across %, down %), read once through `get`, then kept.
pub fn rc_place(get: impl Fn(&str) -> Option<String>) -> (bool, u8, u8) {
    RC_PLACE.with(|p| {
        let found = p.get().unwrap_or_else(|| {
            let number = |key: &str, default: u8| {
                get(key)
                    .and_then(|v| v.trim().parse::<u8>().ok())
                    .map_or(default, |v| v.min(100))
            };
            (
                get("rc_free").is_some_and(|v| v.trim() == "true"),
                number("rc_across", RC_ACROSS),
                number("rc_down", RC_DOWN),
            )
        });
        p.set(Some(found));
        found
    })
}
pub fn rc_set_place(place: (bool, u8, u8)) {
    RC_PLACE.with(|p| p.set(Some(place)));
}
pub fn rc_labels(place: (bool, u8, u8)) -> [String; 3] {
    [
        format!("Position: {}", if place.0 { "Free" } else { "Corner" }),
        format!("Across: {}%", place.1),
        format!("Down: {}%", place.2),
    ]
}
/// Button `i` of the three: Free on or off, then 5% further across or down (round to 0).
pub fn rc_click(place: (bool, u8, u8), i: usize) -> (bool, u8, u8) {
    let step = |v: u8| if v >= 100 { 0 } else { v + 5 };
    match i {
        0 => (!place.0, place.1, place.2),
        1 => (place.0, step(place.1), place.2),
        2 => (place.0, place.1, step(place.2)),
        _ => place,
    }
}
'''

PAGE = "    buttons.extend(m::settings(|key| game.native_setting(m::ID, key)).labels());\n"
PAGE_MORE = "    buttons.extend(m::rc_labels(m::rc_place(|key| game.native_setting(m::ID, key))));\n"

CLICK = "    let mut settings = m::settings(|key| game.native_setting(m::ID, key));\n    settings.click(button);\n"
CLICK_FIRST = '''    let rc_first = m::settings(|key| game.native_setting(m::ID, key)).labels().len();
    if button >= rc_first {
        let place = m::rc_click(m::rc_place(|key| game.native_setting(m::ID, key)), button - rc_first);
        m::rc_set_place(place);
        game.native_set_setting(m::ID, "rc_free", if place.0 { "true" } else { "false" });
        game.native_set_setting(m::ID, "rc_across", &place.1.to_string());
        game.native_set_setting(m::ID, "rc_down", &place.2.to_string());
        return;
    }
'''

HUD_START = "    use horizon_client::extras::Corner;\n"
HUD_END = '''    let rc = m::rc_place(|key| game.native_setting(m::ID, key));
    if rc.0 {
        horizon_client::extras_hud::place_free(batch, corners, rc_start, f32::from(rc.1) / 100.0, f32::from(rc.2) / 100.0);
    }
}
'''

MAP_OFF = "    horizon_client::extras_hud::minimap_coordinates::OUTLINE.with(|s| s.set((0, false)));\n"
MAP_SETTINGS = "    let settings = m::settings(|key| game.native_setting(m::ID, key));\n"
MAP_AT = '''    let rc = m::rc_place(|key| game.native_setting(m::ID, key));
    horizon_client::extras_hud::MINIMAP_AT
        .with(|at| at.set(rc.0.then(|| (f32::from(rc.1) / 100.0, f32::from(rc.2) / 100.0))));
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
        + f"\nconst RC_ACROSS: u8 = {across};\nconst RC_DOWN: u8 = {down};\n" + PLACE,
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
