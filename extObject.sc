+Object {
	onSignalDependantAdded {
		// override to be notified of new signal dependant connection
	}

	onSignalDependantRemoved {
		// override to be notified of new signal dependant disconnection
	}

	valueSlot {
		|setter=\value|
		^ValueSlot(this, setter.asSymbol.asSetter)
	}

	inputSlot {
		^this.valueSlot(\input)
	}

	methodSlot {
		|method|
		^MethodSlot(this, method)
	}

	methodSlots {
		|...methods|
		^methods.collect(this.methodSlot(_))
	}

	forwardSlot {
		^MethodSlot(this, "changed(changed, *args)")
	}

	connectTo {
		|...dependants|
		var autoConnect = if (dependants.last.isKindOf(Boolean)) { dependants.pop() } { true };
		if (dependants.size == 1) {
			^Connection(this, dependants[0], autoConnect);
		} {
			^ConnectionList.newFrom(dependants.collect {
				|dependant|
				Connection(this, dependant, autoConnect)
			})
		}
	}

	mapToSlots {
		|...associations|
		^ConnectionList.make {
			associations.do {
				|assoc|
				assoc.key.connectTo(this.methodSlot(assoc.value));
			}
		}
	}

	signal {
		|keyOrFunc|
		if (keyOrFunc.isNil) {
			^this
		} {
			if (keyOrFunc.isKindOf(Symbol)) {
				^UpdateDispatcher(this).at(keyOrFunc);
			} {
				^this.connectTo(UpdateFilter(keyOrFunc));
			}
		}
	}

	signals {
		|...keyOrFuncs|
		^keyOrFuncs.collect(this.signal(_));
	}

	inputToValue { |obj|		^this.signal(\input).connectTo(obj.valueSlot()) }
	valueToValue { |obj|		^this.signal(\value).connectTo(obj.valueSlot()) }
	inputToInput { |obj|		^this.signal(\input).connectTo(obj.inputSlot()) }
	valueToInput { |obj|		^this.signal(\value).connectTo(obj.inputSlot()) }
	inputToArg { |obj, argName|	^this.signal(\input).connectTo(obj.argSlot(argName)) }
	valueToArg { |obj, argName|	^this.signal(\value).connectTo(obj.argSlot(argName)) }

	connectionTraceString {
		^"%(%)".format(this.class, this.identityHash)
	}

	connectionFreed {}
}