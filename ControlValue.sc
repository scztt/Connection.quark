AbstractControlValue {
	var value, <spec, specConnection, <>updateOnConnect=true, <>holdUpdates=false;
	var >name;
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
		value	= initialValue;
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
		inVal = spec.constrain(inVal);
		if (inVal != value) {
			value = inVal;
			this.changed(\value, value, spec.unmap(value));
		}
	}

	input_{
		|inVal|
		this.value = spec.map(inVal);
	}

	input {
		^spec.unmap(this.value);
	}

	defaultIncrement {
		^(spec.step > 0).if(spec.step, 1/200);
	}

	increment {
		|amount|
		amount = amount ?? { this.defaultIncrement };
		this.input = this.input + amount;
	}

	decrement {
		|amount|
		amount = amount ?? { this.defaultIncrement };
		^this.increment(amount.neg);
	}

	addDependant {
		|dependant|
		super.addDependant(dependant);

		this.onSignalDependantAdded(\value, dependant)
	}

	onSignalDependantAdded {
		|signal, dependant|
		var value;

		if (updateOnConnect) {
			if (signal == \value) {
				value = this.value;
				dependant.update(this, \value, value, spec.unmap(value));
			};
			if (signal == \input) {
				dependant.update(this, \input, this.input);
			};
		}
	}

	signal {
		|keyOrFunc|
		if (keyOrFunc == \input) {
			^if (inputTransform.isNil or: { inputTransform.dependants.isNil }) {
				inputTransform = super.signal(\value).transform({
					|obj, what, value, unmappedValue|
					[obj, what, unmappedValue]
				})
			} {
				inputTransform
			};
		} {
			^super.signal(keyOrFunc)
		}
	}

	spec_{
		|inSpec|
		spec.setFrom(inSpec.asSpec);

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
		this.updateOnConnect	= other.updateOnConnect;
		this.spec				= other.spec;
		this.value				= other.value;
	}

	asControlInput {
		^this.value
	}

	asStream {
		^Routine { loop { this.asControlInput.yield } }
	}

	free {
		this.releaseDependants();
	}

	embedInStream {
		|inval|
		^this.value.yield;
	}

}

NumericControlValue : AbstractControlValue {
	*defaultSpec { ^\unipolar.asSpec }
}

IndexedControlValue : AbstractControlValue {
	*defaultSpec { ^ItemSpec([]) }

	next {
		var index;
		if (spec.items.size > 0) {
			index = spec.items.indexOf(this.value);
			this.value = spec.items.wrapAt(index + 1);
		}
	}

	prev {
		var index;
		if (spec.items.size > 0) {
			index = spec.items.indexOf(this.value);
			this.value = spec.items.wrapAt(index - 1);
		}
	}
}

BusControlValue : NumericControlValue {
	var bus, <>server, <channels;

	init {
		|initialValue, inSpec|
		super.init(initialValue, inSpec);

		server = Server.default;

		ServerTree.add(this);
		ServerQuit.add(this);
		ServerBoot.add(this);

		channels = initialValue !? { initialValue.asArray.size } ?? 1;

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
			this.prSendValue();
		}
	}

	prSendValue {
		bus !? { bus.set(*this.value) };
	}

	free {
		super.free();
		bus.free;
		bus = nil;
	}

	asMap { ^this.bus.asMap }
	asBus { ^this.bus }
}

OnOffControlValue : IndexedControlValue {
	var value, onSig, offSig;

	*defaultSpec { ^ItemSpec([\off, \on]) }

	on {
		this.input = 1;
	}

	off {
		this.input = 0;
	}

	toggle {
		this.input = 1 - this.input;
	}

	input {
		^spec.items.indexOf(value)
	}
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

OSCControlValue : NumericControlValue {
	var <inputSpec, <isOwned=false;
	var func, <oscFunc, <index=1;

	*defaultInputSpec { ^nil }

	index_{
		|i|
		index = i;
	}

	src_{
		| pathOrFunc, srcID, recvPort, argTemplate, dispatcher |
		inputSpec = inputSpec ?? { this.class.defaultInputSpec };

		func = func ? {
			|val|
			index !? { val = val[index] };
			if (inputSpec.notNil) {
				this.input = inputSpec.unmap(val);
			} {
				this.value = val;
			}
		};

		this.clearOSCFunc();

		if (pathOrFunc.notNil) {
			if (pathOrFunc.isKindOf(OSCFunc)) {
				isOwned = false;
				oscFunc = pathOrFunc;
				oscFunc.add(func);
			} {
				isOwned = true;
				oscFunc = OSCFunc(func, pathOrFunc, srcID, recvPort, argTemplate, dispatcher);
			}
		}
	}

	prSetFrom {
		|other|
		super.prSetFrom(other);
		this.inputSpec = other.inputSpec;
		if (other.oscFunc.notNil) {
			this.src_(
				other.oscFunc.path,
				other.oscFunc.srcID,
				other.oscFunc.recvPort,
				other.oscFunc.argTemplate,
				other.oscFunc.dispatcher
			)
		}
	}

	free {
		this.clearOSCFunc();
	}

	clearOSCFunc {
		if (oscFunc.notNil) {
			oscFunc.remove(func);
			if (isOwned) {
				oscFunc.free;
			};
			oscFunc = nil;
		}
	}

}

ControlValueEnvir : EnvironmentRedirect {
	var <default, redirect;
	var <allowCreate=true;

	*new {
		|type=(NumericControlValue)|
		^super.new(Environment()).default_(type).know_(true)
	}

	*newFromSpecs {
		|specs, type|
		^this.new(type).setSpecs(specs);
	}

	resetToDefault {
		envir.keysValueDo {
			|key, val|
			val.value = val.spec.default;
		}
	}

	asSynthArgs {
		|...keys|
		if (keys.size == 0) {
			^envir.asPairs
		} {
			^[keys, envir.atAll(keys)].flop.flatten
		}
	}

	asSynthMapArgs {
		|...keys|
		var vals = this.asSynthArgs(*keys);
		var newVals = Array(vals.size);

		vals = vals.pairsDo({
			|key, val|
			newVals.add(key);
			newVals.add(val.asMap);
		});

		^newVals
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

	setSpecs {
		|specs|
		specs.keysValuesDo {
			|name, spec|
			this.at(name).spec = spec
		};
	}

	setValues {
		|envir|
		var control;

		envir.keysValuesDo {
			|key, value|
			control = super.at(key);
			control !? {
				control.value = value;
			}
		}
	}

	setInputs {
		|envir|
		var control;

		envir.keysValuesDo {
			|key, input|
			control = super.at(key);
			control !? {
				control.input = input;
			}
		}
	}

	storeValues {
		|name|
		var string, header, doc, longestKey = 10;

		name = name ? "_";

		header = "// ControlValueEnvir preset: %\n".format(name);
		string = header ++ "(\n";

		envir.keys.do {
			|name|
			longestKey = max(longestKey, name.asString.size + 1);
		};

		envir.keysValuesDo {
			|key, val|
			string = string ++ "\t";
			string = string ++ "%:".format(key.asString).padRight(longestKey + 4);
			string = string ++ "%,\n".format(val.value.asCompileString);
		};

		string = string ++ ")\n";
		doc = Document(name, string);
		{
			doc.promptToSave = false;
			doc.front;
			doc.selectRange(header.size, string.size - header.size);
		}.defer(0.2);

		^string;
	}

	mapToSynthArgs {
		|node ...keys|

		if (keys.size == 0) {
			keys = envir.keys;
		};

		// Fix for nodeproxy - we DON'T want to map to fadeTime!
		if (node.isKindOf(NodeProxy)) {
			keys.remove(\fadeTime);
			keys.remove(\gate);
		};

		node.map(*this.asSynthMapArgs(*keys.asArray))
	}

	connectToSynthArgs {
		|node ...keys|
		var connections = Array(envir.size);

		if (keys.size == 0) {
			keys = envir.keys;
		};

		// Fix for nodeproxy - we DON'T want to map to fadeTime!
		if (node.isKindOf(NodeProxy)) {
			keys.remove(\fadeTime);
			keys.remove(\gate);
		};

		keys.do {
			|name|
			connections.add(
				envir.at(name).signal(\value).connectTo(node.argSlot(name))
			)
		};

		connections = ConnectionList.newFrom(connections);
		connections.freeAfter(node);
		^connections
	}

	asPbind {
		^Pbind(
			*(envir.keys.asArray.collect {
				|name|
				[name, super.at(name).asStream]
			}).flatten
		)
	}

	keys { ^envir.keys }
	values { ^envir.values }
}