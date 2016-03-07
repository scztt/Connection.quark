AbstractControlValue {
	classvar <>defaultValue, <>defaultSpec;
	var <value, <spec;

	*new {
		|initialValue, spec|
		^super.new.init(initialValue, spec);
	}

	init {
		|initialValue, inSpec|
		value = initialValue ?? defaultValue.copy;
		spec = inSpec ?? defaultSpec.copy;
	}

	value_{
		|inVal|

		inVal = spec.constrain(inVal);

		if (value != inVal) {
			value = inVal;
			this.changed(\value, value);
		}
	}

	input_{
		|inVal|
		this.value_(spec.map(inVal));
	}

	input {
		^spec.unmap(value);
	}

	spec_{
		|inSpec|

		if (inSpec != spec) {
			spec = inSpec;
			this.changed(\spec, spec);
			this.value = value;
		}
	}
}

NumericControlValue : AbstractControlValue {
	*initClass {
		Class.initClassTree(Spec);

		defaultValue = 0;
		defaultSpec = \unipolar.asSpec;
	}
}

MIDIControlValue : NumericControlValue {
	var midiFunc, func, isOwned=false;

	cc {
		| ccNumOrFunc, chan, srcID, argTemplate, dispatcher |
		func = func ? {
			|val|
			this.input = val / 127.0;
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