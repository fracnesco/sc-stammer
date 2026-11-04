// =====================================================================
// StammerView - Stammer's display: one interval, and what happens in it
//
// One interval along, in sixteenths. Where it goes, the slices it will play,
// as tall as `keep` leaves them and moving with `fall`; a line runs along with the
// server's own count, and they light up while it is really going.
//
//   click    go, now
//   drag     left and right: the offset
//   the wheel  the hold
// =====================================================================

StammerView : FxParamView {
	var dragging = false, pressX;

	prDraw {
		var live = unit.extras ? [];
		var active = (live[0] ? 0) > 0.5, count = live[1] ? 0, place = live[2] ? 0;
		var total = unit.intervalTime, sixteenth = unit.sixteenth;
		var slice = unit.sliceTime, hold = unit.hold * sixteenth;
		var start = unit.offset * sixteenth / total;
		var pxPer = bw / total;
		var repeats = (hold / slice).ceil.asInteger.clip(1, 256);
		Pen.fillColor = FxPalette.bg;
		Pen.fillRect(Rect(0, 0, bw, bh));
		// the sixteenths, heavier on the beat
		((total / sixteenth).asInteger + 1).do { |i|
			var x = i * sixteenth * pxPer;
			Pen.strokeColor = if ((i % 4) == 0) { FxPalette.waveDim } { FxPalette.waveDim.copy.alpha_(0.3) };
			Pen.line(x @ 0, x @ bh);
			Pen.stroke;
		};
		repeats.do { |r|
			var keep = unit.decay ** r;
			var x = (start * bw) + (r * slice * pxPer);
			var w = (slice * pxPer - 1).max(1);
			var h = keep * (bh - 30);
			var pitch = (r * unit.pitchFall).clip(-48, 48);
			var y = (bh * 0.5) - (h * 0.5) + (pitch * 1.2);
			var lit = active and: { r == count };
			if (x < bw) {
				Pen.fillColor = (if (lit) { FxPalette.accent } { FxPalette.wave }).copy.alpha_(if (active) { 0.9 } { 0.55 });
				Pen.fillRect(Rect(x, y.clip(16, bh - 4 - h.min(bh - 20)), w.min(bw - x), h.min(bh - 20)));
			};
		};
		Pen.fillColor = FxPalette.text;
		Pen.fillRect(Rect((place + start).wrap(0, 1) * bw, 0, 1, bh));
		this.prCaption("every %, % at %, % sixteenths of % slices%".format(
			unit.interval, (unit.chance * 100).round(1).asString ++ "%", unit.offset.asInteger, unit.hold.asInteger, unit.grid,
			if (active) { "   going" } { "" }
		));
	}

	prMouseDown { |x, y, mod, count|
		dragging = true;
		pressX = x;
		this.prMouseMove(x, y, mod);
	}

	prMouseMove { |x, y, mod|
		if (dragging) {
			unit.offset = (x / bw * (unit.intervalTime / unit.sixteenth)).floor;
		};
	}

	prMouseUp { dragging = false }

	prWheel { |x, y, delta| unit.hold = unit.hold + delta.sign }

	hint { ^"one interval, in sixteenths: drag for where it goes, the wheel for how long - the bars are the slices, falling and fading as the settings say" }
}
