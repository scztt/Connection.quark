+Node {
	argSlot {
		|argName|
		^SynthArgSlot(this, argName)
	}

	argSlots {
		|...argNames|
		^argNames.collect(SynthArgSlot(this, _));
	}

	mapToArgs {
		|...associations|
		^ConnectionList.make {
			associations.do {
				|assoc|
				assoc.key.signal(\value).connectTo(this.argSlot(assoc.value));
			}
		}
	}
}

+NodeProxy {
	argSlot {
		|argName|
		^SynthArgSlot(this, argName)
	}

	argSlots {
		|...argNames|
		^argNames.collect(this.argSlot(_))
	}

	mapToArgs {
		|...associations|
		^this.group.mapToArgs(*associations)
	}
}