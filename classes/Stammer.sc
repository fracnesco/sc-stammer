// =====================================================================
// Stammer - it catches a slice of what is playing and repeats it
//
// A beat repeat. Every `interval` it decides, by `chance`, whether to go; if it
// goes, it takes the last `grid` of sound and plays it over and over for `hold`
// sixteenths. Every repeat can fall in pitch (`pitchFall`), in level (`decay`),
// or run backwards (`backwards`), and `gridVary` changes the slice's length each
// time it goes. The repeats go through their own band filter.
//
//   Ndef(\drums, { Stammer.ar(Decay2.ar(Impulse.ar(4), 0.002, 0.1) * WhiteNoise.ar(0.4 ! 2), \stammer) }).play;
//   b = Stammer(Ndef(\drums), \stammer);
//   b.gui;
//   b.bpm = 140; b.grid = '1/32'; b.chance = 0.5; b.pitchFall = 1;
//   b.repeatNow;                         // go, now
//   b.resync;                            // the bar starts here
//
// It counts its own bars at `bpm`. Tell it the tempo, and call resync on the
// beat to line it up with something else: it does not read Tidal's or anyone's
// clock, and it will drift from one that is not exactly its tempo.
// =====================================================================

Stammer : FxParamUnit {
	classvar <>maxTime = 4;

	*controlName { ^\stammer }
	*displayClass { ^StammerView }

	*intervals { ^#['1/2', 2, '1 bar', 4, '2 bars', 8, '4 bars', 16, '1/4', 1] }
	*intervalNames { ^this.intervals.clump(2).collect(_.first) }
	*intervalBeats { ^this.intervals.clump(2).collect(_.last) }

	*grids { ^#['1/4', 1, '1/8', 0.5, '1/8t', 0.33333, '1/16', 0.25, '1/16t', 0.16667, '1/32', 0.125, '1/32t', 0.08333, '1/64', 0.0625] }
	*gridNames { ^this.grids.clump(2).collect(_.first) }
	*gridBeats { ^this.grids.clump(2).collect(_.last) }

	// parameters renamed on 2026-11-08 so each is a free Tidal word (old -> new):
	// old presets, sessions and code still reach them
	*renamed { ^(reverse: \backwards) }

	// what a Tidal pattern may move through SuperDirtGUI, event by event
	*tidalParams { ^#[\chance, \hold, \grid, \gridVary, \decay, \pitchFall, \backwards, \filterOn, \filterFreq, \filterWidth, \level] }

	*params {
		// labels, sections, sizes and hints are the window's (sc-fx-ui, 2026-11-09);
		// the order and the keys are the nodeMaps' - append, never reorder
		^[
			this.knob(\bpm, 20, 300, \lin, 120, "bpm", "tempo",
				"the tempo it counts its bars in - for Tidal, cps x 240 (four beats a cycle)",
				lag: 0, section: \when, dec: 1, size: \small),
			this.menu(\interval, this.intervalNames, '1 bar', "every", "how often it decides whether to go", \when),
			this.knob(\offset, 0, 15, \lin, 0, "steps", "offset",
				"how far into the interval it goes, in steps of a sixteenth - or click a step in the display",
				lag: 0, section: \when, dec: 0, step: 1),
			this.knob(\chance, 0, 1, \lin, 1, "", "chance", "how likely it is to go when it comes round",
				box: \pct, lag: 0, section: \when, size: \big),
			this.knob(\hold, 1, 32, \lin, 4, "steps", "hold",
				"how long it goes on, in steps of a sixteenth - or drag along the steps in the display",
				lag: 0, section: \when, dec: 0, step: 1),
			this.menu(\mode, #[\mix, \insert, \gate], \insert, "mode",
				"mix: the input carries on under the repeats; insert: they replace it; gate: only ever the repeats", \output),

			this.menu(\grid, this.gridNames, '1/16', "slice",
				"how long a slice it catches - or the wheel on the steps in the display", \repeats)
				.put(\style, \knob).put(\size, \big),
			this.knob(\gridVary, 0, 3, \lin, 0, "oct", "vary",
				"how far a slice's length may stray each time it goes, in octaves either way",
				lag: 0, section: \random, floorText: "off"),
			this.knob(\decay, 0, 1, \lin, 0.9, "", "keep",
				"how much of the level each repeat keeps - or the wheel on the repeats in the display",
				box: \pct, lag: 0, section: \repeats),
			this.knob(\pitchFall, -12, 12, \lin, 0, "st", "fall",
				"how far each repeat falls in pitch, under 0 how far it rises - or drag the repeats in the display",
				lag: 0, section: \repeats, dec: 1),
			this.knob(\backwards, 0, 1, \lin, 0, "", "backwards", "how many slices play backwards - which ones is chance",
				box: \pct, lag: 0, section: \random),
			this.toggle(\filterOn, false, "on", "the band filter on the repeats", \filter),
			this.knob(\filterFreq, 40, 16000, \exp, 1600, "Hz", "freq", "the middle of the band", section: \filter, dec: 0),
			this.knob(\filterWidth, 0.2, 9, \lin, 3, "oct", "width", "how wide the band is, in octaves", section: \filter),
			this.knob(\level, -24, 12, \lin, 0, "dB", "level", "the repeats, in dB", section: \output, dec: 1),
			this.knob(\output, -36, 24, \lin, 0, "dB", "output", "the whole thing, in dB", section: \output, dec: 1)
		]
	}

	*presetList {
		^[
			\default, (),
			'end of bar', (offset: 12, hold: 4, grid: '1/16'),
			'machine gun', (grid: '1/32', hold: 4, offset: 12, decay: 0.95),
			'falling down', (grid: '1/16', hold: 8, offset: 8, pitchFall: 2, decay: 0.92),
			'tape stop', (grid: '1/32', hold: 8, offset: 8, pitchFall: 4, decay: 0.85, filterOn: true, filterFreq: 800, filterWidth: 4),
			'dice', (interval: '1/4', chance: 0.25, hold: 3, grid: '1/32', gridVary: 2, backwards: 0.4),
			'backwards', (grid: '1/8', hold: 8, offset: 8, backwards: 1),
			'radio', (grid: '1/16t', hold: 6, chance: 0.6, interval: '1/2', filterOn: true, filterFreq: 1800, filterWidth: 1.5, mode: \mix),
			'only stutters', (mode: \gate, interval: '1/4', chance: 0.7, hold: 2, grid: '1/32')
		]
	}

	// ------------------------------------------------------------- the window

	// The family is the beat repeat: the display is one interval as a strip of
	// steps with the caught slice stamped along it (StammerView), go is a big
	// pad, the clock's other half - resync - sits in the header. Seventeen
	// controls with the pad do not fit one row, so the colour of the repeats
	// (vary, backwards, the filter) has a page of its own.
	*guiClass { ^FxDeviceGUI }
	*panelClass { ^FxDevicePanel }
	*windowSize { ^Point(1000, 580) }

	// a caught slice: the repeats, the run of steps, the hero knobs
	*identityColour { ^FxPalette.identity(\repeat) }

	*devicePages {
		^[
			\repeat -> [
				(title: "when", keys: [
					(button: "go", size: \big, action: { |unit| unit.repeatNow },
						hint: "go now: it catches the slice just played and repeats it for hold, whatever the interval and the chance say"),
					\chance, \interval, \offset, \hold, \bpm
				]),
				(title: "repeats", colour: \identity, keys: [\grid, \decay, \pitchFall])
			],
			\character -> [
				(title: "random", keys: [\gridVary, \backwards]),
				(title: "filter", keys: [\filterOn, \filterFreq, \filterWidth])
			]
		]
	}

	*isActive { |key, unit|
		if ((key == \filterFreq) or: { key == \filterWidth }) { ^unit.get(\filterOn) == true };
		^true
	}

	// resync, at the right of the header: the interval starts when you click it
	*panelExtras { |panel|
		var b = FxButton("resync", { panel.unit !? (_.resync) });
		b.view.acceptsMouseOver = true;
		b.view.mouseOverAction = {
			panel.tryPerform(\hints) !? { |h|
				h.string = "resync: the interval starts now - click it on a downbeat to line Stammer's bars up with the music"
			};
		};
		^[b.view]
	}

	// ------------------------------------------------------------ the object

	// go, now, whatever the interval says
	repeatNow { this.prTrigger("Go") }

	// the interval starts here
	resync { this.prTrigger("Sync") }

	// seconds, as the graph counts them
	sixteenth { ^60 / values[\bpm] * 0.25 }
	intervalTime { ^this.class.intervalBeats[this.class.intervalNames.indexOf(values[\interval])] * 60 / values[\bpm] }
	sliceTime { ^this.class.gridBeats[this.class.gridNames.indexOf(values[\grid])] * 60 / values[\bpm] }

	// --------------------------------------------------------------- the DSP

	*process { |sig, p, ctx|
		var n = sig.size, sr = SampleRate.ir;
		var beat = 60 / p[\bpm];
		var name = ctx[\name];
		var go = NamedControl.tr(this.prSub(name, "Go"), 0);
		var sync = NamedControl.tr(this.prSub(name, "Sync"), 0);
		var interval = Select.kr(p[\interval], this.intervalBeats) * beat;
		var clock = Phasor.kr(sync, ControlDur.ir / interval, 0, 1);
		var place = (clock - (p[\offset] * beat * 0.25 / interval)).wrap(0, 1);
		var fire = CoinGate.kr(p[\chance], place < Delay1.kr(place)) + go;
		var active = Trig1.kr(fire, p[\hold] * beat * 0.25);
		var slice = Latch.kr(
			Select.kr(p[\grid], this.gridBeats) * beat * (2 ** TRand.kr(p[\gridVary].neg, p[\gridVary], fire)),
			fire + Impulse.kr(0)
		).clip(0.002, maxTime * 0.5);
		var fireAr = T2A.ar(fire);
		var loop = FxLoop(n, maxTime + 0.1);
		var from = Latch.ar(loop.writePhase, fireAr);
		var t = Phasor.ar(fireAr, SampleDur.ir / slice, 0, 1);
		var again = (t < Delay1.ar(t)) + fireAr;
		var count = PulseCount.ar(again, fireAr);
		var speed = Latch.ar(K2A.ar(2 ** (p[\pitchFall].neg / 12)) ** count, again);
		var back = Latch.ar(K2A.ar(TRand.kr(0, 1, Impulse.kr(50))) < p[\backwards], again);
		var ran = Phasor.ar(again, speed, 0, 1e7);
		var length = slice * sr;
		var fade = (0.002 / slice).min(0.5);
		var window = (t / fade).min((1 - t) / fade).clip(0, 1);
		var amp = Latch.ar(K2A.ar(p[\decay]) ** count, again);
		var read = (from - length + ((ran * (1 - back)) + ((length - ran) * back))).wrap(0, loop.frames);
		var slices = loop.buffers.collect { |b| BufRd.ar(1, b, read, 1, 2) };
		var on = Lag.kr(active, 0.002);
		var wet, dryAmp, hp, lp;
		loop.write(sig);
		wet = slices * window * amp * on * p[\level].dbamp;
		hp = (p[\filterFreq] * (2 ** (p[\filterWidth] * -0.5))).clip(10, 20000);
		lp = (p[\filterFreq] * (2 ** (p[\filterWidth] * 0.5))).clip(10, 20000);
		wet = wet.collect { |x|
			Select.ar(p[\filterOn], [x, BLowPass.ar(BHiPass.ar(x, hp, 1), lp, 1)])
		};
		dryAmp = Select.kr(p[\mode], [DC.kr(1), 1 - on, DC.kr(0)]);
		// for the display: going or not, which repeat, where the interval is,
		// that repeat backwards or not, and the slice it took (gridVary moves it)
		ctx[\extras] = [active, A2K.kr(count), place, A2K.kr(back), slice];
		^(sig * dryAmp) + wet
	}
}
