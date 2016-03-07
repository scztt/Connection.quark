AbstractControlValue {
	var <value, <spec, specConnection, specConnection, <>updateOnConnect=true, <>holdUpdates=false;

	*defaultSpec { this.subclassResponsibility(thisMethod) }

	*new {
		|initialValue, spec|
		^super.new.init(initialValue, spec);
	}

	init {
		|initialValue, inSpec|
		spec = inSpec 			?? { this.class.defaultSpec };
		value = initialValue 	?? { spec.default };
	}

	value_{
		|inVal|
		value = spec.constrain(inVal);
		this.changed(\value, value);
	}

	input_{
		|inVal|
		value = spec.map(inVal);
		this.changed(\value, value);
	}

	input {
		^spec.unmap(value);
	}

	addDependant {
		|dependant|
		super.addDependant(dependant);
		if (updateOnConnect) {
			dependant.update(this, \value, value);
		}
	}

	spec_{
		|inSpec|
		spec.setFrom(inSpec);
		this.constrain();
	}

	constrain {
		var newValue = spec.constrain(value);
		if (value != newValue) { this.value = newValue }
	}

	changed {
		arg what ... moreArgs;
		if (holdUpdates.not) {
			^super.changed(what, *moreArgs);
		}
	}

	update {
		|object, changed, value|
		if ((changed == \value) && (value.isNumber)) {
			this.value = value;
		}
	}

	signal {
		|key|
		if (key == \value) {
			// We only use the \value signal - so just return the object itself as an optimization.
			// This also allows us to detect when dependants are added, and report the current value.
			^this
		} {
			^super.signal(key);
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
			this.changed(\value, value);
		}
	}

	prSetFrom {
		|other|
		this.updateOnConnect = other.updateOnConnect;
		this.spec = other.spec;
		this.value = other.value;
	}
}

NumericControlValue : AbstractControlValue {
	*defaultSpec { ^\unipolar.asSpec }
}

MIDIControlValue : NumericControlValue {
	var <>inputSpec, <isOwned=false;
	var func, <midiFunc;

	*defaultInputSpec { ^ControlSpec(0, 127); }

	cc_{
		| ccNumOrFunc, chan, srcID, argTemplate, dispatcher |
		inputSpec = inputSpec ?? { this.defaultInputSpec };

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

	*new {
		|default=(NumericControlValue)|
		var envir = Environment();
		^super.new().default_(default)
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

		if(control.isNil) {
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

