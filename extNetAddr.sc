OscSlot {
	var target, path;

	*new {
		|target, path|
		^super.newCopyArgs(target, path).init;
	}

	init {
		if (target.respondsTo(\sendMsg).not) {
			Error("Target % does not respond to :sendMsg".format(target)).throw;
		};
	}

	update {
		|object, changed, value|
		target.sendMsg(path, value)
	}

	connectionTraceString {
		|what|
		^"%(%: %)".format(this.class, target, path)
	}

}

+NetAddr {
	oscSlot {
		|path|
		^OscSlot(this, path);
	}

	oscSlots {
		|paths|
		^paths.collect(this.oscSlot(_))
	}
}

+Server {
	oscSlot {
		|path|
		^OscSlot(this, path);
	}

	oscSlots {
		|paths|
		^paths.collect(this.oscSlot(_))
	}
}