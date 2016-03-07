+Object {
	valueSlot {
		|setter=\value_|
		^ValueSlot(this, setter)
	}

	methodSlot {
		|method ...argOrder|
		^MethodSlot(this, method, *argOrder)
	}

	connectTo {
		|...dependants|
		var autoConnect = if (dependants.last.isKindOf(Boolean)) { dependants.pop() } { true };
		if (dependants.size == 1) {
			^Connection(this, dependants[0], autoConnect);
		} {
			^ConnectionList(dependants.collect {
				|dependant|
				Connection(this, dependant, autoConnect)
			})
		}
	}

	mapToSlots {
		|...associations|
		^ConnectionList.makeWith {
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

	connectionTraceString {
		^"%(%)".format(this.class, this.identityHash)
	}

	connectionFreed {}
}