+Node {
	argSlot {
		|argName|
		^SynthArgSlot(this, argName)
	}

	mapToArgs {
		|...associations|
		^ConnectionList.makeWith {
			associations.do {
				|assoc|
				assoc.key.signal(\value).connectTo(this.argSlot(assoc.value));
			}
		}
	}
}
