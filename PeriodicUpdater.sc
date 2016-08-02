PeriodicUpdater {
	var <object, <method, <clock, <>delegate;
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
		process = SkipJack(this.pull(_), freq, name:"PeriodicUpdater_" ++ this.identityHash.asString, clock:clock);
	}

	start {
		process.start();
	}

	stop {
		process.stop();
	}

	pull {
		var val = object.perform(method);
		if (val != lastVal) {
			lastVal = val;
			delegate.changed(\value, val)
		};
	}
}

BusUpdater : PeriodicUpdater {
	*new {
		|bus, freq=0.1, delegate|
		^super.new(bus, \getSynchronous, freq, delegate);
	}
}