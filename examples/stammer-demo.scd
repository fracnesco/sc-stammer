// Stammer - a walkthrough, one block at a time
//
// Evaluate each ( ... ) block in turn with the cursor inside it. This is
// an editor document, not a file to .load.

// ---- 1. a sound, and Stammer driven from its Ndef
(
s.boot;
)

(
Ndef(\drums, { Stammer.ar(Decay2.ar(Impulse.ar(4), 0.002, 0.1) * WhiteNoise.ar(0.4 ! 2), \stammer) }).play;
~b = Stammer(Ndef(\drums), \stammer);
~b.gui;
)

// ---- 2. settings: each is a property, each moves the window
(
~b.bpm = 140; ~b.grid = '1/32'; ~b.chance = 0.5;
~b.pitchFall = 1;                  // each repeat a semitone lower
~b.repeatNow;                      // go, now
~b.resync;                         // the bar starts here
)

// ---- 3. every preset; evaluate one at a time
~b.preset = \default;
~b.preset = 'end of bar';
~b.preset = 'machine gun';
~b.preset = 'falling down';
~b.preset = 'tape stop';
~b.preset = \dice;
~b.preset = \backwards;
~b.preset = \radio;
~b.preset = 'only stutters';

// ---- 4. settings kept, and carried
(
~b.saveAsPreset("demo");
~b.copySettings;
Stammer.presetNames.postln;
Stammer.removePreset("demo");
)

// ---- 5. the same effect with a node of its own, on the main out
(
~own = Stammer.onBus(0, 2);
~own.pasteSettings;
~own.enabled = true;
~own.gui;
)

(
~own.free;
)

// ---- 6. tidy up
(
~b.free;
Ndef(\drums).clear;
)
