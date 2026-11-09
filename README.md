# Stammer

**It catches a slice of what is playing and repeats it, falling, fading or backwards - a beat repeat, running anywhere a UGen does, with a window.** A SuperCollider extension.

```supercollider
s.boot;
Ndef(\drums, { Stammer.ar(Decay2.ar(Impulse.ar(4), 0.002, 0.1) * WhiteNoise.ar(0.4 ! 2), \stammer) }).play;

b = Stammer(Ndef(\drums), \stammer);
b.gui;
b.bpm = 140; b.grid = '1/32'; b.chance = 0.5;
b.pitchFall = 1;                  // each repeat a semitone lower
b.repeatNow;                      // go, now
b.resync;                         // the bar starts here
```

Every effect of this family lives in the same three places. Write `Stammer.ar` into a graph of your own and drive it from the object, as above; give it a node of its own on a bus, `Stammer.onBus(0, 2).enabled_(true)`; or put it in a chain, `FxChain.master.add(Stammer())`. If SuperDirtGUI is installed, it is an insert on an orbit too: `~sdg.addInsert(0, \stammer)`.

It is built on [FxKit](../sc%20fx%20kit), which is built on [ParaEQ](../sc%20EQ), and the class library will not compile without them; the installer links both when the folders sit side by side. It uses SuperCollider's own UGens, not sc3-plugins.

Written for SuperCollider 3.13 on macOS. Nothing in it is platform specific apart from the symlink in `install.scd`, which Linux shares and Windows does not.

## Install

### As a quark

```supercollider
Quarks.install("https://github.com/fracnesco/sc-stammer.git");
thisProcess.recompile;
```

The manifest names `sc-fx-kit` as a dependency, which names `sc-eq`, so `Quarks` fetches those first. The quark goes by the name of the repository, `sc-stammer`; the class inside it is `Stammer`.

### From a clone

Clone `sc-eq` into a folder called `sc EQ`, `sc-fx-kit` into `sc fx kit` and this into `sc stammer`, side by side. Open `install.scd` and evaluate its first block, then recompile the class library. It links what is missing and tells you what it did.

On Windows there is no `ln -s`, so use the quark, or copy the three folders into the extensions folder by hand: `Platform.userExtensionDir.openOS` opens it.

Install it one way or the other, **not both**: two copies on the class path is a duplicate class error at compile time.

### Check that it took

```supercollider
Stammer.presetNames;
Stammer.onBus(0, 2).enabled_(true).gui;
```

Select `Stammer` and press cmd/ctrl + d for the help file, and see [examples/stammer-demo.scd](examples/stammer-demo.scd) for a walkthrough you can evaluate a block at a time.

## What it does

Every `interval` it decides, by `chance`, whether to go, `offset` sixteenths into it. If it goes, it takes the last `grid` of sound and plays it over and over for `hold` sixteenths. Each repeat keeps `decay` of the level of the one before and falls `pitchFall` semitones (a negative number rises), `backwards` says how many slices run backwards, and `gridVary` changes the slice's length each time it goes. The repeats have a band filter of their own.

`mode` says what happens to the input: `mix` carries on under the repeats, `insert` is replaced by them while they last, and `gate` is only ever the repeats.

It counts its own bars at `bpm`. Tell it the tempo and call `resync` on the beat to line it up with something else. It does not read Tidal's clock or anyone else's, and it will drift from a clock that is not exactly its tempo. The window has both: the big `go` pad and the `resync` button at the top right.

Each instance takes 1.5 MB of the server's real time memory at stereo. A stock server has 8 MB and does not fit it at 12 channels (`alloc failed` in the post window), so several of them, or a wide bus, call for `s.options.memSize = 8192 * 32` before booting.

## The window

`b.gui` opens it (`b.panel(view)` puts the same thing in a view of yours): the top row (on, presets, save, the menu with copy and paste, `resync` at the right), the display with the in and out meters beside it, and two pages of cards. Hover over anything and the bar at the bottom says what it does.

The display is one interval as a strip of steps, a sixteenth each, with the beats numbered (more than one interval when the hold runs past its end):

- **steps** - the run it holds, lit in yellow from the step where it goes, fainter as `chance` falls; the % on the first step is the chance. Small flags mark where it would decide again inside the hold.
- **in** - the input as it passed, once sound comes in. The box is the slice it catches, just before it goes; the dashed bar under it, while `vary` is up, runs from the shortest slice it may take to the longest. The hatching is what `insert` mutes while it goes (all of it in `gate`).
- **repeats** - a block a repeat: as tall as it keeps, as low as it falls in semitones, the slice drawn backwards in about as many as `backwards` says. The numbers after the last one are where it ends up. When the filter is on, the lane says through which band.

While it runs, a line follows the server's own count and the repeat it is really playing lights up in orange.

| in the display | does |
|---|---|
| click a step | where it goes (`offset`) |
| drag along the steps | how long (`hold`) |
| the wheel on the steps | the slice (`grid`) |
| double click the steps | back to offset 0, hold 4 |
| drag the repeats up and down | how far each falls (`pitchFall`), shift for fine |
| the wheel on the repeats | how much each keeps (`decay`) |
| double click the repeats | back to 0 st and 90 % |

Page **repeat**: the `go` pad (it goes now, whatever the interval and the chance say), then *when* - chance, every, offset, hold, tempo - and *repeats* - slice (a stepped knob over the note values), keep, fall. Page **character**: *random* - vary, backwards - and *filter* - on, freq, width. The *output* card is on both: mode (mix / insert / gate), level (the repeats) and output.

## Presets

Built in: default, end of bar, machine gun, falling down, tape stop, dice, backwards, radio, only stutters.

Yours go in `Platform.userAppSupportDir/stammer-presets.scd`: `b.saveAsPreset("mine")`, or type a name in the window and press save. `b.copySettings` and `other.pasteSettings` carry settings from one to another.

## Parameters

Every parameter is a property: `b.bpm`, `b.bpm = ...`. These also work by name, `b.set(\bpm, ...)`, `b.get(\bpm)`. Knobs clip to their range. This table is generated from the class.

| parameter | section | range | default | what it does |
|---|---|---|---|---|
| `bpm` | when | 20 .. 300 bpm | 120 | the tempo it counts its bars in - for Tidal, cps x 240 (four beats a cycle) |
| `interval` | when | 1/2 1 bar 2 bars 4 bars 1/4 | 1 bar | how often it decides whether to go |
| `offset` | when | 0 .. 15 steps | 0 | how far into the interval it goes, in steps of a sixteenth - or click a step in the display |
| `chance` | when | 0 .. 1 | 1 | how likely it is to go when it comes round |
| `hold` | when | 1 .. 32 steps | 4 | how long it goes on, in steps of a sixteenth - or drag along the steps in the display |
| `mode` | output | mix insert gate | insert | mix: the input carries on under the repeats; insert: they replace it; gate: only ever the repeats |
| `grid` | repeats | 1/4 1/8 1/8t 1/16 1/16t 1/32 1/32t 1/64 | 1/16 | how long a slice it catches - or the wheel on the steps in the display |
| `gridVary` | random | 0 .. 3 oct | 0 | how far a slice's length may stray each time it goes, in octaves either way |
| `decay` | repeats | 0 .. 1 | 0.9 | how much of the level each repeat keeps - or the wheel on the repeats in the display |
| `pitchFall` | repeats | -12 .. 12 st | 0 | how far each repeat falls in pitch, under 0 how far it rises - or drag the repeats in the display |
| `backwards` | random | 0 .. 1 | 0 | how many slices play backwards - which ones is chance |
| `filterOn` | filter | on off | false | the band filter on the repeats |
| `filterFreq` | filter | 40 .. 16000 Hz | 1600 | the middle of the band |
| `filterWidth` | filter | 0.2 .. 9 oct | 3 | how wide the band is, in octaves |
| `level` | output | -24 .. 12 dB | 0 | the repeats, in dB |
| `output` | output | -36 .. 24 dB | 0 | the whole thing, in dB |

## Files

| file | what it is |
|---|---|
| [classes/Stammer.sc](classes/Stammer.sc) | the parameters, the presets, the DSP |
| [classes/StammerView.sc](classes/StammerView.sc) | the window's display: the strip of steps, the input, the repeats |
| [HelpSource/Classes/Stammer.schelp](HelpSource/Classes/Stammer.schelp) | the help file |
| [HelpSource/Classes/StammerView.schelp](HelpSource/Classes/StammerView.schelp) | the help file for the display |
| [examples/stammer-demo.scd](examples/stammer-demo.scd) | a walkthrough, one block at a time |
| [install.scd](install.scd) | the symlink, a check, an uninstall |
| [sc-stammer.quark](sc-stammer.quark) | the quark manifest |

## License

MIT - see [LICENSE](LICENSE).
