AbstractControlValue {
	var value, <spec, specConnection, specConnection, <>updateOnConnect=true, <>holdUpdates=false;
	var inputTransform;
	var prIsChanging=false;

	*defaultSpec { this.subclassResponsibility(thisMethod) }

	*new {
		|initialValue, spec|
		^super.new.init(initialValue, spec);
	}

	init {
		|initialValue, inSpec|
		spec	= inSpec !? { inSpec.asSpec } ?? { this.class.defaultSpec.deepCopy };
		this.constrain(true);
	}

	name {
		^name ?? { spec.units };
	}

	value {
		^(value ?? { spec.default })
	}

	value_{
		|inVal|
		value = spec.constrain(inVal);
		this.changed(\value, value, spec.unmap(value));
	}

	input_{
		|inVal|
		this.value = spec.map(inVal);
	}

	input {
		^spec.unmap(this.value);
	}

	addDependant {
		|dependant|
		var value;

		super.addDependant(dependant);

		if (updateOnConnect) {
			value = this.value;
			dependant.update(this, \value, value, spec.unmap(value));
		}
	}

	onSignalDependantAdded {
		|signal, dependant|
		var value;

		if (updateOnConnect && ((signal == \value) || (signal == \input))) {
			value = this.value;
			dependant.update(this, \value, value, spec.unmap(value));
		}
	}

	signal {
		|keyOrFunc|
		if (keyOrFunc == \input) {
			^inputTransform ?? {
				inputTransform = super.signal(\value).transform({
					|obj, what, value, unmappedValue|
					[obj, what, unmappedValue]
				})
			}
		} {
			^super.signal(keyOrFunc)
		}
	}

	spec_{
		|inSpec|
		spec.setFrom(inSpec);

		this.constrain();
	}

	constrain {
		|notify=true|

		if (value.notNil) {
			value = spec.constrain(value);
		};

		if (notify) {
			this.changed(\value, this.value, spec.unmap(this.value));
		}
	}

	changed {
		arg what ... moreArgs;
		if (holdUpdates.not && prIsChanging.not) {
			prIsChanging = true;
			protect {
				super.changed(what, *moreArgs);
			} {
				prIsChanging = false;
			}
		}
	}

	// Do not override this method in subclasses - instead, override prSetFrom
	setFrom {
		|other|
		if (this.class != other.class) {
			Error("Trying to set a ControlValue of type '%' from one of type '%'.".format(this.class, other.class)).throw
		} {
			this.holdUpdates = true;
			protect { this.prSetFrom(other) } {
				this.holdUpdates = false;
			};
			this.changed(\value, value, spec.unmap(value));
		}
	}

	prSetFrom {
		|other|
		this.updateOnConnect = other.updateOnConnect;
		this.spec = other.spec;
		this.value = other.value;
	}

	asControlInput {
		^this.value
	}

	asStream {
		^Routine { loop { this.asControlInput.yield } }
	}
}

NumericControlValue : AbstractControlValue {
	*defaultSpec { ^\unipolar.asSpec }
}

IndexedControlValue : AbstractControlValue {
	*defaultSpec { ^ItemsSpec([]) }
}

BusControlValue : NumericControlValue {
	var bus, <>server;

	init {
		|initialValue, inSpec|
		super.init(initialValue, inSpec);

		server = Server.default;

		ServerTree.add(this);
		ServerQuit.add(this);
		ServerBoot.add(this);

		if (Server.default.serverRunning) {
			this.doOnServerBoot();
			this.doOnServerTree();
		}
	}

	bus {
		if (bus.isNil && server.serverRunning) {
			this.sendBus();
		};
		^bus;
	}

	doOnServerTree {}

	doOnServerBoot {
		this.sendBus();
	}

	doOnServerQuit {
		this.free();
	}

	value_{
		|inValue|
		super.value_(inValue);
		this.prSendValue()
	}

	constrain {
		super.constrain();
		this.prSendValue()
	}

	sendBus {
		if (bus.isNil) {
			bus = Bus.control(server, 1);
			bus.set(this.value);
		}
	}

	prSendValue {
		bus !? { bus.set(this.value) };
	}

	free {
		if (bus.notNil) {
			bus.free; bus = nil;
		}
	}

	asMap { ^this.bus.asMap }
}

OnOffControlValue : AbstractControlValue {
	var value, onSig, offSig;

	*defaultSpec { ^ItemSpec([\off, \on]) }

	init {
		|initialValue|
		^super.init(initialValue);
	}

	on {
		this.value = \on;
	}

	off {
		this.value = \off;
	}

	toggle {
		this.value = (value == \on).if(\off, \on);
	}

	value_{
		|inVal|
		if ((inVal == \on) || (inVal == \off)) {
			if (inVal != value) {
				value = inVal;
				this.changed(\value, value);
				this.changed(inVal);
			}
		} {
			Error("Value must be \off or \on").throw
		}
	}

	input_{
		|inputVal|
		this.value = (inputVal > 0.5).if(\on, \off);
	}

	input {
		^switch (value,
			{ \off }, { 0 },
			{ \on }, { 1 }
		)
	}

	constrain {}
}

MIDIControlValue : NumericControlValue {
	var <>inputSpec, <isOwned=false;
	var func, <midiFunc;

	*defaultInputSpec { ^ControlSpec(0, 127); }

	cc_{
		| ccNumOrFunc, chan, srcID, argTemplate, dispatcher |
		inputSpec = inputSpec ?? { this.class.defaultInputSpec };

		func = func ? {
			|val|
			this.input = inputSpec.unmap(val);
		};

		this.clearMIDIFunc();

		if (ccNumOrFunc.notNil) {
			if (ccNumOrFunc.isKindOf(MIDIFunc)) {
				isOwned = false;
				midiFunc = ccNumOrFunc;
				midiFunc.add(func);
			} {
				isOwned = true;
				midiFunc = MIDIFunc.cc(func, ccNumOrFunc, chan, srcID, argTemplate, dispatcher)
			}
		}
	}

	prSetFrom {
		|other|
		super.prSetFrom(other);
		this.inputSpec = other.inputSpec;
		if (other.midiFunc.notNil) {
			this.cc_(
				other.midiFunc.msgNum,
				other.midiFunc.chan,
				other.midiFunc.srcID,
				other.midiFunc.argTemplate,
				other.midiFunc.dispatcher
			)
		}
	}

	free {
		this.clearMIDIFunc();
	}

	clearMIDIFunc {
		if (midiFunc.notNil) {
			midiFunc.remove(func);
			if (isOwned) {
				midiFunc.free;
			};
			midiFunc = nil;
		}
	}
}

ControlValueEnvir : EnvironmentRedirect {
	var <default, redirect;
	var envir;
	var <allowCreate=true;

	*new {
		|default=(NumericControlValue)|
		var envir = Environment();
		^super.new().default_(default).know_(true)
	}

	default_{
		|inDefault|
		if (inDefault.isKindOf(Class)) {
			default = { inDefault.new() }
		} {
			default = inDefault
		}
	}

	at {
		|key|
		var control = super.at(key);

		if(control.isNil && allowCreate) {
			control = default.value(key);
			super.put(key, control);
		};
		^control
	}

	put {
		|key, value|
		var control = super.at(key);

		if (control.isNil || value.isNil) {
			super.put(key, value);
		} {
			control.setFrom(value);
		}
	}
}

