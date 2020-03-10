PeriodicUpdater {
	var <>object, <>method, <clock, <>delegate;
	var <freq, <>name;
	var <process, <lastVal;

	*new {
		|object, method=\value, freq=0.1, delegate, clock|
		var new = super.newCopyArgs(object, method, clock ?? AppClock).freq_(freq).name_(method);
		new.delegate = delegate ?? new;
		^new
	}

	freq_{
		|inFreq|
		freq = inFreq;
		process.stop();
		process = SkipJack({ this.pull() },
			dt: freq,
			name: "PeriodicUpdater_" ++ this.identityHash.asString,
			clock: clock
		);
	}

	start {
		process.start();
	}

	stop {
		process.stop();
	}

	pull {
		|update=true|
		var val = object.perform(method);

		if (update && (val != lastVal)) {
			lastVal = val;
			delegate.changed(\value, val)
		};
	}
}

BusUpdater : PeriodicUpdater {
	var server;

	*new {
		|bus, freq=0.1, delegate|
		^super.new(bus, \getSynchronous, freq, delegate);
	}

	bus_{
		|inBus|
		object = inBus;
		inBus !? {
			ServerQuit.add(this);
			server = inBus.server
		};
	}

	doOnServerQuit {
		object = nil;
		this.releaseDependants();
	}

	pull {
		|update=true|
		if (server.notNil and: { server.hasShmInterface }) {
			^super.pull(update);
		} {
			object !? {
				object.get({
					|val|
					if (update && (val != lastVal)) {
						lastVal = val;
						delegate.changed(\value, val)
					};
				});
			}
		}
	}
}

OSCReplyUpdater {
	var <target, <rate, <>addAction, <name;
	var <node, <valueFunc, <triggerFunc, <synthDef, responder;
	var <value, <replyCount = 0;
	var valueStructure;

	*basicNew {
		|target, rate, addAction|
		^super.newCopyArgs(target ? Server.default, rate ? 1, addAction ? \addAfter).init;
	}

	*new {
		|synthDefFunc, rate, target, addAction|
		^super.newCopyArgs(
			target ? Server.default,
			rate ? 1,
			addAction ? \addAfter
		).init.valueFunc_(synthDefFunc)
	}

	init {
		name = this.class.name;
		target = target.asTarget;

		responder = OSCFunc(this.onReply(_), this.address).permanent_(true);

		triggerFunc = this.impulseTrigger;

		ServerTree.add(this);
	}

	onReply {
		|msg|
		replyCount = msg[2];
		value = msg[3..];

		valueStructure !? { value = value.reshapeLike(valueStructure) };

		this.changed(\value, *value);
	}

	free {
		this.releaseDependants();
		responder.free; responder = nil;
		node.free; node = nil;
		ServerTree.remove(this);
	}

	defName {
		^"%_%".format(name, this.identityHash).asSymbol
	}

	address {
		^"/%/%".format(name, this.identityHash).asSymbol
	}

	rateArg { ^\prOscReplyRate }

	impulseTrigger {
		^{
			|values|
			if (values.rate == \audio) {
				Impulse.ar(this.rateArg.ar(rate))
			} {
				Impulse.kr(this.rateArg.kr(rate))
			}
		}
	}

	defaultTriggerFunc {
		^this.impulseTrigger
	}

	defaultValueFunc {
		^{ DC.kr(0) }
	}

	makeReply {
		|values, trigger|

		if (values.asArray.detect(_.isArray).notNil) {
			valueStructure = values;
		} {
			valueStructure = nil; // Don't bother, interpret at array
		};
		values = values.asArray.flatten;

		^if (trigger.rate == \audio) {
			SendReply.ar(trigger, this.address, values, PulseCount.ar(trigger));
		} {
			SendReply.kr(trigger, this.address, values, PulseCount.kr(trigger));
		}
	}

	rate_{
		|val|
		rate = val;
		if (node.notNil) {
			node.set(this.rateArg, rate);
		}
	}

	triggerFunc_{
		|func|
		if (func != triggerFunc) {
			triggerFunc = func;
			this.updateSynthDef();
		}
	}

	valueFunc_{
		|func|
		if (func != valueFunc) {
			valueFunc = func;
			this.updateSynthDef();
		}
	}

	buildSynthDef {
		^SynthDef(this.defName, {
			var rateSymbol, trigger, values;

			values 		= SynthDef.wrap(valueFunc ? this.defaultValueFunc);
			trigger 	= SynthDef.wrap(triggerFunc ? this.defaultTriggerFunc, prependArgs:[values]);

			this.makeReply(values, trigger);
		})
	}

	updateSynthDef {
		synthDef = this.buildSynthDef.add;

		fork {
			target.server.sync;
			this.updateSynth();
		}
	}

	updateSynth {
		if (target.server.serverRunning) {
			if (node.notNil) {
				node = Synth.replace(node, synthDef.name, [this.rateArg, rate], true);
				switch (addAction,
					\addToHead, { node.moveToHead(target) },
					\addToTail, { node.moveToTail(target) },
					\addBefore, { node.moveBefore(target) },
					\addAfter, { node.moveAfter(target) },
				);
			} {
				node = Synth(synthDef.name, [this.rateArg, rate], target, addAction);
			}
		}
	}

	doOnServerTree {
		node = nil;
		this.updateSynth();
	}
}

SignalStatsUpdater : OSCReplyUpdater {
	var inputFunc;

	*new {
		|inputFunc, statsFunc, rate, target, addAction|
		^(this.basicNew(target, rate, addAction)
			.inputFunc_(inputFunc)
			.valueFunc_(statsFunc))
	}

	*minMaxAvgFunc {
		^{
			|values, reset|
			var rateSym;
			var min, max, avg;

			rateSym = if(values.rate == \audio, \ar, \kr);

			min = RunningMin.perform(rateSym, values, reset);
			max = RunningMax.perform(rateSym, values, reset);
			avg = AverageOutput.perform(rateSym, values, reset);

			[avg, min, max];
		}
	}

	*combinedMinMaxAvgFunc {
		^{
			|values, reset|
			var rateSym;
			var min, max, avg;

			rateSym = if (values.rate == \audio, \ar, \kr);

			values = values.asArray;
			min = RunningMin.perform(rateSym, ArrayMax.perform(rateSym, values), reset);
			max = RunningMax.perform(rateSym, ArrayMin.perform(rateSym, values), reset);
			avg = AverageOutput.perform(rateSym, values.sum / values.size, reset);

			[avg, min[0], max[0]];
		}
	}

	defaultValueFunc {
		^this.class.minMaxAvgFunc
	}

	defaultInputFunc {
		^{ DC.kr(0) }
	}

	inputFunc_{
		|func|
		if (func != inputFunc) {
			inputFunc = func;
			this.updateSynthDef();
		}
	}

	buildSynthDef {
		|bus|
		^SynthDef(this.defName, {
			var rateSymbol, trigger, input, values;

			input 	= SynthDef.wrap(inputFunc ? this.defaultInputFunc);
			trigger = SynthDef.wrap(triggerFunc ? this.defaultTriggerFunc, prependArgs:[input]);
			values	= SynthDef.wrap(valueFunc ? this.defaultValueFunc, prependArgs:[input, trigger]);

			this.makeReply(values, trigger);
		})
	}
}


BusStatsUpdater : SignalStatsUpdater {
	var <bus, <inputBus=false;

	*new {
		|bus, statsFunc, target, rate, addAction|
		^(super.basicNew(target ?? bus.server, rate, addAction)
			.bus_(bus)
			.valueFunc_(statsFunc));
	}

	makeInputFunc {
		^{
			var class = inputBus.if(SoundIn, In);
			var rate = inputBus.if(\audio, bus.asBus.rate);

			if (rate == \audio) {
				class.ar(bus.asBus.index, bus.numChannels);
 			} {
				class.kr(bus.asBus.index, bus.numChannels);
			}
		}
	}

	inputBus_{
		|b|
		if (inputBus != b) {
			inputBus = b;
			inputFunc = this.makeInputFunc();
			this.updateSynthDef();
		}
	}

	bus_{
		|inBus|
		if (inBus != bus) {
			bus = inBus;
			inputFunc = this.makeInputFunc();
			this.updateSynthDef();
		}
	}
}