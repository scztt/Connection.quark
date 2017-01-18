PeriodicUpdater {
	var <>object, <method, <clock, <>delegate;
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
		inBus !? { server = inBus.server };
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