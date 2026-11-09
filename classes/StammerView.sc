// =====================================================================
// StammerView - Stammer's display: one interval as a strip of steps, the
// slice it catches, and the repeats it plays
//
// Along: one interval in sixteenths, the beats numbered (more intervals
// when the hold runs past the end of one). Top to bottom:
//
//   steps    a cell a sixteenth; the run it holds lit in Stammer's yellow
//            from the step where it goes, fainter as the chance falls
//   in       the input as it passed, along the interval, once sound comes
//            in; the box is the slice it catches, just before it goes, the
//            dashed bar under it how far gridVary lets it stray, and the
//            hatching what insert (or gate) mutes while it goes
//   repeats  a block a repeat: as big as it keeps, as low as it falls, the
//            slice inside it forwards or backwards (about as many backwards
//            as `backwards` says - which ones is chance); the band, if the
//            filter is on
//
// A line runs with the server's own count, and the repeat it is playing
// lights up while it really goes.
//
//   click a step            where it goes (offset)
//   drag along the steps    how long (hold)
//   the wheel on them       the slice
//   double click them       back to the start, four steps
//   drag the repeats        up and down: how far each falls (shift: fine)
//   the wheel on them       how much each keeps
//   double click them       back to 0 st and 90 %
// =====================================================================

StammerView : FxParamView {
	// what the meter replies bring: the input along one interval, how many
	// times the interval came round while it goes, where it caught
	var env, lastIdx, lastClock, wraps = 0, wasActive = false, catchAt;
	// the strip as last laid out, in pixels and sixteenths
	var gx0 = 36, gx1 = 100, gw = 64, gSteps = 16, gOff = 0, gHold = 4, gSpan = 16;
	var gStepTop = 38, gStepH = 20, gInTop = 66, gInBot = 110, gRepTop = 120, gRepBot = 200;
	// a drag going on
	var dragMode, dragAnchor, dragMoved = false, dragY, dragFrom, dragShift = false, dragSpan;

	*minHeight { ^230 }

	// the same number for the same block every time: about `backwards` of the
	// blocks are drawn backwards, and more of them turn as it goes up
	*roll { |i| ^((i + 1) * 0.6180339887).frac }

	// ------------------------------------------------- the model, in sixteenths

	prSteps {
		var cls = unit.class;
		^(cls.intervalBeats[cls.intervalNames.indexOf(unit.get(\interval)) ? 1] * 4).round.asInteger.max(1)
	}

	prSixteenth { ^60 / unit.get(\bpm) * 0.25 }

	// a slice in sixteenths: the grid's - or, given seconds, what the server took
	prSliceSteps { |secs|
		var cls = unit.class;
		secs = secs ?? { cls.gridBeats[cls.gridNames.indexOf(unit.get(\grid)) ? 3] * 60 / unit.get(\bpm) };
		^(secs.clip(0.002, cls.maxTime * 0.5) / this.prSixteenth).max(0.01)
	}

	prLayout {
		var inH;
		this.prMeasure;
		gx0 = 36;
		gx1 = (bw - 10).max(gx0 + 40);
		gw = gx1 - gx0;
		gSteps = this.prSteps;
		gOff = unit.get(\offset).round.asInteger % gSteps;
		gHold = unit.get(\hold).round.asInteger.clip(1, 32);
		// whole intervals, as many as the hold needs; a drag keeps the scale it began with
		gSpan = dragSpan ?? { (((gOff + gHold) / gSteps).ceil.max(1) * gSteps).asInteger };
		gStepTop = 38;
		gStepH = 20;
		inH = ((bh - 76) * 0.24).round.clip(30, 64);
		gInTop = 66;
		gInBot = gInTop + inH;
		gRepTop = gInBot + 10;
		gRepBot = (bh - 8).max(gRepTop + 40);
	}

	prX { |step| ^gx0 + (step / gSpan * gw) }

	prStepAt { |x| ^((x - gx0) / gw * gSpan).floor.asInteger.clip(0, gSpan - 1) }

	prRegion { |y|
		^case
		{ y >= (gRepTop - 5) } { \repeats }
		{ y >= (gInTop - 3) } { \input }
		{ true } { \steps }
	}

	// the repeats, each [from, to, index, caught at] in sixteenths along the
	// strip. A decision that falls inside the hold catches again there, and the
	// count starts over (the server does the same)
	prPlan { |anchor, slice|
		var list = [], end = anchor + gHold, edges;
		edges = (0..9).collect { |k| gOff + (k * gSteps) }
			.select { |d| (d > (anchor + 0.001)) and: { d < (end - 0.001) } };
		edges = [anchor] ++ edges ++ [end];
		edges.doAdjacentPairs { |a, b|
			var t = a, r = 0;
			while { (t < (b - 0.001)) and: { list.size < 512 } } {
				list = list.add([t, (t + slice).min(b), r, a]);
				t = t + slice;
				r = r + 1;
			};
		};
		^list
	}

	// ------------------------------------------------------------- live

	// every meter reply: where the count is, and the input's level written
	// along the interval wherever the count has passed since the last one
	levelsArrived {
		var live, active, clock, n, idx, level, i;
		if (unit.isNil) { ^this };
		live = unit.extras ? [];
		if (live.size < 3) { ^this };
		active = (live[0] ? 0) > 0.5;
		gSteps = this.prSteps;
		gOff = unit.get(\offset).round.asInteger % gSteps;
		clock = ((live[2] ? 0) + (gOff / gSteps)).wrap(0, 1);
		if (lastClock.notNil and: { clock < (lastClock - 0.5) }) { wraps = wraps + 1 };
		if (active.not) { wraps = 0 };
		if (active and: { wasActive.not }) { catchAt = clock * gSteps; wraps = 0 };
		n = gSteps * 4;
		if (env.isNil or: { env.size != n }) { env = Array.fill(n, 0); lastIdx = nil };
		idx = (clock * n).floor.asInteger.clip(0, n - 1);
		level = unit.inLevel ? 0;
		if (lastIdx.notNil and: { ((idx - lastIdx) % n) < (n div: 4).max(2) }) {
			i = (lastIdx + 1) % n;
			while { i != idx } { env[i] = level; i = (i + 1) % n };
		};
		env[idx] = level;
		lastIdx = idx;
		lastClock = clock;
		wasActive = active;
	}

	// ------------------------------------------------------------- draw

	prDraw {
		var live = unit.extras ? [], alive, active, clock, pos, anchor, slice, plan, lit, st;
		var c0, c1, wrapped = false;
		this.prLayout;
		alive = unit.isAlive and: { live.size >= 3 };
		active = alive and: { (live[0] ? 0) > 0.5 };
		clock = if (alive) { ((live[2] ? 0) + (gOff / gSteps)).wrap(0, 1) };
		pos = clock !? { |c| if (active) { (wraps * gSteps) + (c * gSteps) } { c * gSteps } };
		// where it caught: the step it was set to go on, or where go was pressed
		anchor = if (active and: { catchAt.notNil } and: { (catchAt - gOff).abs >= 0.75 }) { catchAt } { gOff };
		slice = this.prSliceSteps(if (active) { live[4] });
		plan = this.prPlan(anchor, slice);
		lit = if (active and: { pos.notNil }) { plan.detect { |b| (pos >= b[0]) and: { pos < b[1] } } };
		// the slice comes from just before it goes: from the end of the interval, at the start of one
		c1 = anchor;
		c0 = anchor - slice;
		if (c0 < -0.001) { c0 = c0 + gSteps; c1 = c1 + gSteps; wrapped = true };
		st = (live: live, alive: alive, active: active, pos: pos, anchor: anchor, slice: slice,
			plan: plan, lit: lit, id: unit.class.identityColour ? FxPalette.accent,
			chance: unit.get(\chance), c0: c0, c1: c1, wrapped: wrapped);
		this.prDrawCaption(st);
		this.prDrawRuler(st);
		this.prDrawSteps(st);
		this.prDrawInput(st);
		this.prDrawRepeats(st);
		pos !? { |p| this.prDrawPlayhead(p) };
	}

	prDrawCaption { |st|
		var n, right, colour, lit = st[\lit];
		this.prCaption("every %  ·  % bpm".format(unit.get(\interval), FxPalette.fixed(unit.get(\bpm), 1)), 8, 4, FxPalette.text);
		if (lit.notNil) {
			n = st[\plan].count { |b| b[3] == lit[3] };
			right = "going  ·  repeat % of %".format(lit[2] + 1, n);
			colour = FxPalette.accent;
		} {
			n = st[\plan].count { |b| b[3] == st[\anchor] };
			right = "% % of %".format(n, if (n == 1) { "repeat" } { "repeats" }, unit.get(\grid));
			if (st[\chance] < 1) { right = right ++ "  ·  " ++ FxPalette.format(st[\chance], "", \pct) ++ " of the time" };
			colour = st[\id];
		};
		Pen.stringRightJustIn(right, Rect(bw * 0.3, 3, (bw * 0.7) - 10, 12), FxPalette.smallFont, colour);
	}

	prDrawRuler { |st|
		var small = FxPalette.smallFont, y0 = gStepTop, id = st[\id];
		var a0 = this.prX(st[\anchor]), a1 = this.prX((st[\anchor] + gHold).min(gSpan)), label;
		(gSpan + 1).do { |i|
			var x = this.prX(i), beat = (i % 4) == 0, bar = (i % 16) == 0, edge = (i % gSteps) == 0;
			var len = case { edge } { 8 } { beat } { 5 } { true } { 2 };
			Pen.fillColor = case
				{ edge } { FxPalette.dim }
				{ beat } { FxPalette.fade(FxPalette.dim, 0.7) }
				{ true } { FxPalette.waveDim };
			Pen.fillRect(Rect(x - 0.5, y0 - len - 1, 1, len));
			if (beat and: { i < gSpan }) {
				label = case
					{ gSpan <= 16 } { ((i div: 4) + 1).asString }
					{ gSpan <= 32 } { "%.%".format((i div: 16) + 1, ((i % 16) div: 4) + 1) }
					{ bar } { ((i div: 16) + 1).asString }
					{ true } { nil };
				label !? {
					Pen.stringAtPoint(label, (x + 3) @ (y0 - 20), small,
						if (i < gSteps) { FxPalette.dim } { FxPalette.fade(FxPalette.dim, 0.6) });
				};
			};
		};
		// the later decisions: where it would catch again, if it is still going
		(1..9).do { |k|
			var d = gOff + (k * gSteps), x = this.prX(d);
			if (d < gSpan) {
				Pen.fillColor = FxPalette.fade(id, if (d < (st[\anchor] + gHold)) { 0.7 } { 0.35 });
				Pen.moveTo(x @ (y0 - 9));
				Pen.lineTo((x + 5) @ (y0 - 6.5));
				Pen.lineTo(x @ (y0 - 4));
				Pen.lineTo(x @ (y0 - 9));
				Pen.fill;
			};
		};
		// the hold as a brace over the steps, and the flag where it goes
		Pen.fillColor = id;
		Pen.fillRect(Rect(a0, y0 - 3, (a1 - a0).max(2), 2));
		Pen.moveTo(a0 @ (y0 - 11));
		Pen.lineTo((a0 + 8) @ (y0 - 7.5));
		Pen.lineTo(a0 @ (y0 - 4));
		Pen.lineTo(a0 @ (y0 - 11));
		Pen.fill;
		Pen.fillRect(Rect(a0 - 0.5, y0 - 11, 1.5, 10));
	}

	prDrawSteps { |st|
		var id = st[\id], chance = st[\chance], w = gw / gSpan;
		var from = st[\anchor], to = st[\anchor] + gHold, start = st[\anchor].floor.asInteger;
		var playStep = st[\pos] !? { |p| p.floor.asInteger };
		gSpan.do { |i|
			var r = Rect(this.prX(i) + 1, gStepTop, (w - 2).max(1), gStepH);
			var a = if (i >= gSteps) { 0.5 } { 1 }, run = ((i + 1) > from) and: { i < to }, col;
			col = case
				{ st[\active] and: { run } and: { playStep == i } } { FxPalette.accent }
				{ i == start } { FxPalette.fade(id, 0.55 + (0.4 * chance)) }
				{ run } { FxPalette.fade(id, 0.2 + (0.32 * chance)) }
				{ (i % 4) == 0 } { FxPalette.controlHi }
				{ true } { FxPalette.control };
			Pen.fillColor = FxPalette.fade(col, a);
			Pen.addRoundedRect(r, 2, 2);
			Pen.fill;
			if (playStep == i) {
				Pen.strokeColor = FxPalette.fade(FxPalette.text, 0.8 * a);
				Pen.width = 1;
				Pen.addRoundedRect(r.insetBy(0.5, 0.5), 2, 2);
				Pen.stroke;
			};
			if ((i == start) and: { chance < 1 } and: { r.width >= 30 }) {
				Pen.stringCenteredIn(FxPalette.format(chance, "", \pct).replace(" ", ""), r, FxPalette.smallFont, FxPalette.bg);
			};
		};
	}

	prDrawBeatLines { |top, bottom|
		var w = gw / gSpan;
		Pen.width = 1;
		(gSpan + 1).do { |i|
			var x = this.prX(i).round + 0.5, beat = (i % 4) == 0;
			if (beat or: { w >= 24 }) {
				Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, case
					{ (i % gSteps) == 0 } { 0.75 }
					{ beat } { 0.4 }
					{ true } { 0.15 });
				Pen.line(x @ (top + 1), x @ (bottom - 1));
				Pen.stroke;
			};
		};
	}

	prDrawInput { |st|
		var id = st[\id], h = gInBot - gInTop, mode = unit.get(\mode), has, hx0, hx1, box, label, lw;
		Pen.fillColor = FxPalette.bg;
		Pen.addRoundedRect(Rect(gx0, gInTop, gw, h), 3, 3);
		Pen.fill;
		this.prDrawBeatLines(gInTop, gInBot);
		Pen.stringRightJustIn("in", Rect(0, gInTop + 2, gx0 - 6, 12), FxPalette.smallFont, FxPalette.dim);
		// the input, one interval of it, again in every interval shown
		has = env.notNil and: { env.size == (gSteps * 4) } and: { env.maxItem > 0.0005 };
		if (has) {
			(gSpan div: gSteps).do { |copy|
				var base = copy * gSteps;
				Pen.fillColor = FxPalette.fade(FxPalette.wave, if (copy == 0) { 0.5 } { 0.25 });
				Pen.moveTo(this.prX(base) @ gInBot);
				env.do { |lv, k|
					Pen.lineTo(this.prX(base + ((k + 0.5) / 4)) @ (gInBot - 1 - lv.max(1e-5).ampdb.linlin(-48, 0, 0, h - 6)));
				};
				Pen.lineTo(this.prX(base + gSteps) @ gInBot);
				Pen.fill;
			};
		};
		// what the mode mutes: insert while it goes, gate all the time
		hx0 = switch (mode, \gate, { gx0 }, \insert, { this.prX(st[\anchor]) }, { nil });
		hx1 = switch (mode, \gate, { gx1 }, \insert, { this.prX((st[\anchor] + gHold).min(gSpan)) }, { nil });
		if (hx0.notNil and: { hx1 > hx0 }) {
			Pen.push;
			Pen.addRect(Rect(hx0, gInTop, hx1 - hx0, h));
			Pen.clip;
			Pen.fillColor = FxPalette.fade(FxPalette.waveBg, 0.5);
			Pen.fillRect(Rect(hx0, gInTop, hx1 - hx0, h));
			Pen.strokeColor = FxPalette.fade(FxPalette.dim, if (st[\active]) { 0.6 } { 0.3 });
			Pen.width = 1;
			(((hx1 - hx0) + h) / 6).ceil.asInteger.do { |k|
				var x = hx0 + (k * 6);
				Pen.line(x @ gInTop, (x - h) @ gInBot);
			};
			Pen.stroke;
			Pen.pop;
		};
		// the slice it catches
		box = Rect(this.prX(st[\c0]), gInTop + 1, (this.prX(st[\c1]) - this.prX(st[\c0])).max(2), h - 2);
		// no input yet: say so in the widest stretch nothing else is drawn on
		if (has.not) {
			this.prSayInGap(["the input shows here once sound comes in", "no input yet"],
				[[box.left, box.right]] ++ if (mode == \insert) { [[hx0, hx1]] } { [] }, gInTop, h);
		};
		// vary: how short and how long a slice it may take, under the box
		this.prDrawVary(st, gInBot - 7);
		Pen.fillColor = FxPalette.fade(id, 0.18);
		Pen.fillRect(box);
		Pen.strokeColor = id;
		Pen.width = 1.5;
		if (st[\wrapped]) { Pen.lineDash = FloatArray[3, 2] };
		Pen.addRect(box.insetBy(0.75, 0.75));
		Pen.stroke;
		Pen.lineDash = FloatArray[1, 0];
		label = unit.get(\grid).asString;
		lw = label.bounds(FxPalette.smallFont).width.ceil + 4;
		if ((box.left - lw - 2) >= gx0) {
			Pen.stringRightJustIn(label, Rect(box.left - lw - 2, gInTop + 2, lw, 12), FxPalette.smallFont, id);
		} {
			Pen.stringLeftJustIn(label, Rect(box.right + 4, gInTop + 2, lw, 12), FxPalette.smallFont, id);
		};
	}

	// the first of the texts that fits the widest gap of the lane between the spans [x0, x1] taken
	prSayInGap { |texts, taken, top, h|
		var font = FxPalette.smallFont, edges, best, text;
		edges = [[gx0, gx0]] ++ taken.reject { |t| t[0].isNil }.sort { |a, b| a[0] < b[0] } ++ [[gx1, gx1]];
		edges.doAdjacentPairs { |a, b|
			var x0 = a[1].max(gx0), x1 = b[0].min(gx1);
			if (best.isNil or: { (x1 - x0) > (best[1] - best[0]) }) { best = [x0, x1] };
		};
		text = texts.detect { |t| (t.bounds(font).width.ceil + 16) <= (best[1] - best[0]) };
		text !? {
			Pen.stringCenteredIn(text, Rect(best[0], top, best[1] - best[0], h), font, FxPalette.fade(FxPalette.dim, 0.8));
		};
	}

	// gridVary: a bar from the shortest slice it may take to the longest, ending where it catches
	prDrawVary { |st, y|
		var vary = unit.get(\gridVary), id = st[\id], base = this.prSliceSteps, lo, hi, x0, x1, xe;
		if (vary <= 0.001) { ^this };
		lo = st[\c1] - (base * (2 ** vary.neg));
		hi = (st[\c1] - this.prSliceSteps((base * this.prSixteenth) * (2 ** vary))).max(st[\c1] - gSteps);
		x0 = this.prX(hi.max(0));
		x1 = this.prX(lo);
		xe = this.prX(st[\c1]);
		Pen.strokeColor = FxPalette.fade(id, 0.8);
		Pen.width = 1;
		Pen.lineDash = FloatArray[2, 2];
		Pen.line(x0 @ y, xe @ y);
		Pen.stroke;
		Pen.lineDash = FloatArray[1, 0];
		Pen.width = 2;
		Pen.line(x0 @ (y - 4), x0 @ (y + 4));
		Pen.line(x1 @ (y - 4), x1 @ (y + 4));
		Pen.stroke;
	}

	prDrawRepeats { |st|
		var id = st[\id], chance = st[\chance], hgt = gRepBot - gRepTop, cy = gRepTop + (hgt * 0.5);
		var maxT = (hgt * 0.26).clip(8, 60), pxSt = ((hgt * 0.5) - (maxT * 0.5) - 4).max(8) / 24;
		var fall = unit.get(\pitchFall), keep = unit.get(\decay), level = unit.get(\level), backP = unit.get(\backwards);
		var keepDb = if (keep > 0) { 20 * keep.log10 } { -120 };
		var yOf = { |down| cy + (down.sign * 24 * (down.abs / 24).tanh * pxSt) };
		var thOf = { |n| 3 + ((((level + (n * keepDb)) + 42) / 54).clip(0, 1) * (maxT - 3)) };
		var a = 0.2 + (0.4 * chance), firsts, first, last;
		Pen.fillColor = FxPalette.bg;
		Pen.addRoundedRect(Rect(gx0, gRepTop, gw, hgt), 3, 3);
		Pen.fill;
		this.prDrawBeatLines(gRepTop, gRepBot);
		// the pitch a repeat falls to, in semitones from the slice's own
		[-24, -12, 0, 12, 24].do { |down|
			var y = yOf.(down);
			Pen.strokeColor = FxPalette.fade(FxPalette.waveDim, if (down == 0) { 0.9 } { 0.4 });
			Pen.width = 1;
			if (down == 0) { Pen.lineDash = FloatArray[3, 3] };
			Pen.line(gx0 @ y, gx1 @ y);
			Pen.stroke;
			Pen.lineDash = FloatArray[1, 0];
			Pen.stringRightJustIn(case { down == 0 } { "0" } { down > 0 } { "-" ++ down } { "+" ++ down.neg },
				Rect(0, y - 6, gx0 - 6, 12), FxPalette.smallFont, FxPalette.dim);
		};
		Pen.stringRightJustIn("st", Rect(0, gRepTop + 2, gx0 - 6, 12), FxPalette.smallFont, FxPalette.fade(FxPalette.dim, 0.8));
		// the band filter on the repeats, when it is on
		if (unit.get(\filterOn) == true) {
			Pen.stringLeftJustIn("through a band at %, % wide".format(
				FxPalette.format(unit.get(\filterFreq), "Hz", nil, 0), FxPalette.format(unit.get(\filterWidth), "oct", nil, 1)),
				Rect(gx0 + 6, gRepTop + 3, gw * 0.6, 12), FxPalette.smallFont, FxPalette.fade(id, 0.85));
		};
		// the hold, washed in yellow
		Pen.fillColor = FxPalette.fade(id, 0.05);
		Pen.fillRect(Rect(this.prX(st[\anchor]), gRepTop + 1,
			this.prX((st[\anchor] + gHold).min(gSpan)) - this.prX(st[\anchor]), hgt - 2));
		st[\plan].do { |b, i|
			var from = b[0], to = b[1].min(gSpan), n = b[2], th = thOf.(n), y = yOf.(n * fall);
			var x0 = this.prX(from) + 1, x1 = this.prX(to) - 1, rect;
			var now = st[\lit] === b;
			var past = (st[\lit].notNil) and: { b[3] == st[\lit][3] } and: { n < st[\lit][2] };
			var back = if (now and: { st[\live][3].notNil }) { st[\live][3] > 0.5 } { this.class.roll(i) < backP };
			if (from < gSpan) {
				rect = Rect(x0, y - (th * 0.5), (x1 - x0).max(1), th);
				Pen.fillColor = case
					{ now } { FxPalette.accent }
					{ past } { FxPalette.fade(id, a * 0.5) }
					{ true } { FxPalette.fade(id, a) };
				Pen.addRoundedRect(rect, 2, 2);
				Pen.fill;
				Pen.strokeColor = if (now) { FxPalette.accent } { FxPalette.fade(id, 0.45 + (0.5 * chance)) };
				Pen.width = 1;
				Pen.addRoundedRect(rect.insetBy(0.5, 0.5), 2, 2);
				Pen.stroke;
				if ((rect.width >= 9) and: { th >= 7 }) {
					this.prDrawSliceShape(rect.insetBy(2, 2), back,
						if (now) { FxPalette.fade(FxPalette.bg, 0.75) } { FxPalette.fade(id, (a + 0.4).min(1)) });
				};
			};
		};
		// from the box it caught down to the first repeat
		firsts = st[\plan].select { |b| b[3] == st[\anchor] };
		first = firsts.first;
		last = firsts.last;
		if (first.notNil and: { st[\wrapped].not }) {
			this.prDrawArrow((this.prX(st[\c0]) + this.prX(st[\c1])) * 0.5, gInBot + 2,
				(this.prX(first[0]) + this.prX(first[1].min(gSpan))) * 0.5, yOf.(0) - (thOf.(0) * 0.5) - 3,
				FxPalette.fade(id, 0.7));
		};
		// what the last repeat comes to
		if (last.notNil and: { (fall != 0) or: { keep < 1 } } and: { last[2] > 0 }) {
			this.prDrawEnd(last, yOf.(last[2] * fall), thOf.(last[2]), last[2] * fall, last[2] * keepDb, id);
		};
	}

	// a hit as it sounds: there at once, dying away - the other way round backwards
	prDrawSliceShape { |r, back, colour|
		var n = 10;
		Pen.fillColor = colour;
		Pen.moveTo((if (back) { r.right } { r.left }) @ r.bottom);
		(n + 1).do { |j|
			var f = j / n;
			Pen.lineTo((if (back) { r.right - (f * r.width) } { r.left + (f * r.width) }) @ (r.bottom - (r.height * ((1 - f) ** 2.5))));
		};
		Pen.lineTo((if (back) { r.right } { r.left }) @ r.bottom);
		Pen.fill;
	}

	prDrawArrow { |xa, ya, xb, yb, colour|
		var ang, tip = 4;
		if ((yb - ya) < 6) { ^this };
		ang = atan2(yb - ya, xb - xa);
		Pen.strokeColor = colour;
		Pen.width = 1;
		Pen.line(xa @ ya, xb @ yb);
		Pen.stroke;
		Pen.fillColor = colour;
		Pen.moveTo(xb @ yb);
		Pen.lineTo((xb - (tip * cos(ang - 0.5))) @ (yb - (tip * sin(ang - 0.5))));
		Pen.lineTo((xb - (tip * cos(ang + 0.5))) @ (yb - (tip * sin(ang + 0.5))));
		Pen.lineTo(xb @ yb);
		Pen.fill;
	}

	// the last repeat's pitch and level against the first, beside it
	prDrawEnd { |b, y, th, down, db, colour|
		var parts = [], text, w, x;
		if (down != 0) { parts = parts.add(FxPalette.format(down.neg, "st", nil, 0, true)) };
		if (db < -0.05) { parts = parts.add(if (db < -90) { "-inf" } { FxPalette.format(db, "dB", nil, 0, true) }) };
		if (parts.isEmpty) { ^this };
		text = parts.join("  ");
		w = text.bounds(FxPalette.smallFont).width.ceil + 4;
		x = this.prX(b[1].min(gSpan)) + 4;
		if ((x + w) > gx1) {
			// no room after it: over it, to its right edge
			Pen.stringRightJustIn(text, Rect(x - 8 - w, (y - (th * 0.5) - 14).max(gRepTop + 2), w, 12), FxPalette.smallFont, colour);
		} {
			Pen.stringLeftJustIn(text, Rect(x, y - 6, w, 12), FxPalette.smallFont, colour);
		};
	}

	prDrawPlayhead { |pos|
		var x;
		if (pos > gSpan) { ^this };
		x = this.prX(pos);
		Pen.fillColor = FxPalette.fade(FxPalette.text, 0.85);
		Pen.fillRect(Rect(x - 0.75, gStepTop - 2, 1.5, gRepBot - gStepTop + 2));
	}

	// ------------------------------------------------------------ mouse

	prMouseDown { |x, y, mod, count|
		this.prLayout;
		dragShift = mod.isShift;
		dragMode = nil;
		if (this.prRegion(y) == \repeats) {
			if (count == 2) { this.prDefault(\pitchFall); this.prDefault(\decay); ^this };
			dragMode = \fall;
			dragY = y;
			dragFrom = unit.get(\pitchFall);
			^this
		};
		if (count == 2) { this.prDefault(\offset); this.prDefault(\hold); ^this };
		dragSpan = gSpan;
		dragAnchor = this.prStepAt(x);
		dragMoved = false;
		dragMode = \steps;
		unit.set(\offset, (dragAnchor % gSteps).clip(0, 15));
		this.prSayHint(x, y);
	}

	prMouseMove { |x, y, mod|
		var cur;
		case
		{ dragMode == \steps } {
			this.prLayout;
			cur = this.prStepAt(x);
			if (cur != dragAnchor) { dragMoved = true };
			if (dragMoved) { unit.set(\hold, (cur - dragAnchor + 1).clip(1, 32)) };
			this.prSayHint(x, y);
		}
		{ dragMode == \fall } {
			if (mod.isShift != dragShift) { dragShift = mod.isShift; dragFrom = unit.get(\pitchFall); dragY = y };
			unit.set(\pitchFall, dragFrom + ((y - dragY) / 200 * 24 * if (dragShift) { 0.1 } { 1 }));
			this.prSayHint(x, y);
		};
	}

	prMouseUp {
		dragMode = nil;
		dragSpan = nil;
		this.refresh;
	}

	prWheel { |x, y, delta|
		var names, i;
		if (delta == 0) { ^this };
		this.prLayout;
		if (this.prRegion(y) == \repeats) {
			unit.set(\decay, (unit.get(\decay) + (delta.sign * 0.01)).clip(0, 1));
		} {
			names = unit.class.gridNames;
			i = names.indexOf(unit.get(\grid)) ? 3;
			unit.set(\grid, names.clipAt(i + delta.sign));
		};
		this.prSayHint(x, y);
	}

	prDefault { |key| unit.set(key, unit.class.param(key)[\default]) }

	// the hint bar follows a drag: the panel's hint for this view, asked again
	prSayHint { |x, y| view.mouseOverAction.value(view, x, y) }

	// ------------------------------------------------------------ hint

	hint { |x, y|
		if (unit.isNil) { ^"" };
		this.prLayout;
		^switch (this.prRegion(y ? 0),
			\repeats, {
				"the repeats, a block each: as big as it keeps, as low as it falls, about % of them backwards - drag up and down for the fall (%), the wheel for keep (%), double click: 0 st and 90 %"
					.format(FxPalette.format(unit.get(\backwards), "", \pct),
						FxPalette.format(unit.get(\pitchFall), "st", nil, 1, true), FxPalette.format(unit.get(\decay), "", \pct))
			},
			\input, {
				"the input as it passed - the box is the slice it catches (%)%, %: click for where it goes, drag along for how long"
					.format(unit.get(\grid),
						if (unit.get(\gridVary) > 0.001) { ", the bar under it how far vary lets it stray" } { "" },
						switch (unit.get(\mode),
						\insert, { "hatched where insert mutes the input" },
						\gate, { "all hatched: gate lets only the repeats out" },
						{ "and mix lets the input carry on under the repeats" }))
			},
			{
				"one interval in sixteenths: click a step for where it goes (offset %), drag along for how long (hold %), the wheel for the slice (%) - double click: back to 0 and 4"
					.format(unit.get(\offset).round.asInteger, unit.get(\hold).round.asInteger, unit.get(\grid))
			}
		)
	}
}
