MethodSlot {
	var <updateFunc, <reciever, <methodName;

	*new {
		|obj, method|
		if (obj.isKindOf(View)) {
			^MethodSlotUI.prNew().init(obj, method);
		} {
			^MethodSlot.prNew().init(obj, method);
		};
	}

	*prNew { ^super.new }

	init {
		|object, methodString|
		reciever = object;
		methodName = methodString.asString.split($()[0].asSymbol;
		updateFunc = MethodSlot.makeUpdateFunc(reciever, methodString);
		object.tryPerform(\autoReleaseDependants);
	}

	*makeUpdateFunc {
		|reciever, methodString|
		var methodName;

		methodString = methodString.asString;
		methodName = methodString.split($()[0].stripWhiteSpace.asSymbol; // guess the method name - used later for validation

		if (methodString.find("(").isNil) {
			methodString = methodString ++ "(object, changed, *args)";
		};

		if (reciever.respondsTo(methodName).not && reciever.tryPerform(\know).asBoolean.not) {
			Exception("Object of type % doesn't respond to %.".format(reciever.class, methodName)).throw;
		};

		^"{ |reciever, object, changed, args| var value = args[0]; var input = args[1]; reciever.% }".format(methodString).interpret;
	}

	update {
		|object, changed ...args|
		updateFunc.value(reciever, object, changed, args);
	}

	connectionTraceString {
		|what|
		^"%(%(%).%)".format(this.class, reciever.class, reciever.identityHash, methodName)
	}
}

MethodSlotUI : MethodSlot {
	classvar deferList, deferFunc;

	*initClass {
		deferList = List();
	}

	*doDeferred {
		var tmpList = deferList;
		deferList = List(tmpList.size);
		deferFunc = nil;

		tmpList.do {
			|argsList|
			argsList[0].value(*argsList[1]);
		}
	}

	*deferUpdate {
		|updateFunc, args|
		deferList.add([updateFunc, args]);
		deferFunc ?? {
			deferFunc = { MethodSlotUI.doDeferred }.defer
		}
	}

	*prNew { ^super.prNew }

	update {
		|object, changed ...args|
		if (this.canCallOS) {
			updateFunc.value(reciever, object, changed, args);
		} {
			this.class.deferUpdate(updateFunc, [reciever, object, changed, args])
		}
	}
}

ValueSlot : MethodSlot {
	*new {
		|obj, setter=\value_|
		^super.new(obj, "%(value)".format(setter))
	}
}

SynthArgSlot {
	var <synth, <>argName, synthConn;

	*new {
		|synth, argName|
		^super.newCopyArgs(synth, argName).connectSynth
	}

	connectSynth {
		var endSig;
		if (synth.respondsTo(\register)) {
			synth.register;
		};

		if(synth.isKindOf(Node)) {
			endSig = \n_end
		} {
			if (synth.isKindOf(NodeProxy)) {
				endSig = \end
			}
		};

		synthConn = synth.signal(\end).connectTo(this.methodSlot(\disconnectSynth))
	}

	disconnectSynth {
		synthConn.free();
		synth = argName = synthConn = nil;
	}

	update {
		|obj, what, value|
		if (synth.notNil) {
			synth.server.makeBundle(nil, { synth.set(argName, value) });
		}
	}

	spec {
		var spec, def;

		def = synth.respondsTo(\def).if({ synth.def });

		if (def.notNil and: { def.metadata.notNil } and: { def.metadata[\spec].notNil }) {
			spec = def.metadata[\spec][argName];
		};

		if (spec.isNil) {
			spec = argName.asSpec;
		};

		^spec;
	}
}

+NodeProxy {
	asSynth {
		^this.group
	}
}

