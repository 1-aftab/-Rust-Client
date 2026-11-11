package io.github.juanluci.starling;

/** Native game component; StarlingActivity keeps the existing Java/JNI bridge API. */
// Rust Client: not final, the host launcher subclasses it to put its own controls over the game.
public class GameActivity extends StarlingActivity {}
