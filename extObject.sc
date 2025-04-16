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

	delegateSlot {
		^MethodSlot(this, "changed(changed, *args)")
	}


	forwardSlot {
		^MethodSlot(this, "forwardUpdate(object, changed, *args)")
	}

	forwardUpdate {
		|...args|
		dependantsDictionary.at(this).copy.do({ arg item;
			item.update(*args);
		});
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

	uniqueConnectionAt {
		|name|
		^UniqueConnections.at(this, name)
	}

	uniqueConnectionPut {
		|name, value|
		UniqueConnections.put(this, name, value)
	}

	connectToUnique {
		|name ...dependants|
		var connection;

		if (name.isKindOf(String)) {
			name = name.asSymbol
		};

		if (name.isKindOf(Symbol).not) {
			dependants = [name] ++ dependants;
			name = \defaultUniqueConnection;
		};

		dependants = dependants.reject(_.isNil);

		this.uniqueConnectionAt(name).free;

		if (dependants.notEmpty) {
			connection = this.connectTo(*dependants);
			this.uniqueConnectionPut(name, connection);
		};

		^connection
	}

	mapToSlots {
		|...associations|
		^ConnectionList.newFrom(
			associations.collect {
				|assoc|
				assoc.key.connectTo(this.methodSlot(assoc.value));
			}
		)
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