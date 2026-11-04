// =====================================================================
// Stammer - it catches a slice of what is playing and repeats it
//
// A beat repeat. Every `interval` it decides, by `chance`, whether to go; if it
// goes, it takes the last `grid` of sound and plays it over and over for `hold`
// sixteenths. Every repeat can fall in pitch (`pitchFall`), in level (`decay`),
// or run backwards (`reverse`), and `gridVary` changes the slice's length each
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
	*panelExtras { |panel|
		^[
			FxPalette.button("go now", 56, { panel.unit !? (_.repeatNow) }),
			FxPalette.button("resync", 56, { panel.unit !? (_.resync) })
		]
	}
	*windowSize { ^Point(860, 500) }

	*intervals { ^#['1/2', 2, '1 bar', 4, '2 bars', 8, '4 bars', 16, '1/4', 1] }
	*intervalNames { ^this.intervals.clump(2).collect(_.first) }
	*intervalBeats { ^this.intervals.clump(2).collect(_.last) }

	*grids { ^#['1/4', 1, '1/8', 0.5, '1/8t', 0.33333, '1/16', 0.25, '1/16t', 0.16667, '1/32', 0.125, '1/32t', 0.08333, '1/64', 0.0625] }
	*gridNames { ^this.grids.clump(2).collect(_.first) }
	*gridBeats { ^this.grids.clump(2).collect(_.last) }

	*params {
		^[
			this.knob(\bpm, 20, 300, \lin, 120, "bpm", "bpm", "the tempo it counts in", lag: 0, section: \when, dec: 1),
			this.menu(\interval, this.intervalNames, '1 bar', "every", "how often it decides whether to go", \when),
			this.knob(\offset, 0, 15, \lin, 0, "16ths", "offset", "how far into that it goes, in sixteenths", lag: 0, section: \when, dec: 0, step: 1),
			this.knob(\chance, 0, 1, \lin, 1, "", "chance", "how likely it is to go when it comes round", box: \pct, lag: 0, section: \when),
			this.knob(\hold, 1, 32, \lin, 4, "16ths", "hold", "how long it goes on, in sixteenths", lag: 0, section: \when, dec: 0, step: 1),
			this.menu(\mode, #[\mix, \insert, \gate], \insert, "mode",
				"mix: the input carries on under the repeats; insert: they replace it; gate: only ever the repeats", \when),

			this.menu(\grid, this.gridNames, '1/16', "slice", "how long a slice it takes", \slice),
			this.knob(\gridVary, 0, 3, \lin, 0, "oct", "vary", "how far a slice's length may stray each time it goes", lag: 0, section: \slice),
			this.knob(\decay, 0, 1, \lin, 0.9, "", "keep", "how much of the level each repeat keeps", box: \pct, lag: 0, section: \slice),
			this.knob(\pitchFall, -12, 12, \lin, 0, "st", "fall", "how far each repeat falls in pitch - under 0, how far it rises", lag: 0, section: \slice, dec: 1),
			this.knob(\reverse, 0, 1, \lin, 0, "", "reverse", "how many slices play backwards", box: \pct, lag: 0, section: \slice),
			this.toggle(\filterOn, false, "filter", "the band filter on the repeats", \slice),
			this.knob(\filterFreq, 40, 16000, \exp, 1600, "Hz", "freq", "the middle of the band", section: \slice, dec: 0),
			this.knob(\filterWidth, 0.2, 9, \lin, 3, "oct", "width", "how wide the band is, in octaves", section: \slice),
			this.knob(\level, -24, 12, \lin, 0, "dB", "level", "the repeats, in dB", section: \slice, dec: 1),
			this.knob(\output, -36, 24, \lin, 0, "dB", "output", "the whole thing, in dB", section: \slice, dec: 1)
		]
	}

	*presetList {
		^[
			\default, (),
			'end of bar', (offset: 12, hold: 4, grid: '1/16'),
			'machine gun', (grid: '1/32', hold: 4, offset: 12, decay: 0.95),
			'falling down', (grid: '1/16', hold: 8, offset: 8, pitchFall: 2, decay: 0.92),
			'tape stop', (grid: '1/32', hold: 8, offset: 8, pitchFall: 4, decay: 0.85, filterOn: true, filterFreq: 800, filterWidth: 4),
			'dice', (interval: '1/4', chance: 0.25, hold: 3, grid: '1/32', gridVary: 2, reverse: 0.4),
			'backwards', (grid: '1/8', hold: 8, offset: 8, reverse: 1),
			'radio', (grid: '1/16t', hold: 6, chance: 0.6, interval: '1/2', filterOn: true, filterFreq: 1800, filterWidth: 1.5, mode: \mix),
			'only stutters', (mode: \gate, interval: '1/4', chance: 0.7, hold: 2, grid: '1/32')
		]
	}

	// go, now, whatever the interval says
	repeatNow { this.prTrigger("Go") }

	// the interval starts here
	resync { this.prTrigger("Sync") }

	// seconds, as the graph counts them
	sixteenth { ^60 / values[\bpm] * 0.25 }
	intervalTime { ^this.class.intervalBeats[this.class.intervalNames.indexOf(values[\interval])] * 60 / values[\bpm] }
	sliceTime { ^this.class.gridBeats[this.class.gridNames.indexOf(values[\grid])] * 60 / values[\bpm] }

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
		var back = Latch.ar(K2A.ar(TRand.kr(0, 1, Impulse.kr(50))) < p[\reverse], again);
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
		ctx[\extras] = [active, A2K.kr(count), place];
		^(sig * dryAmp) + wet
	}
}
