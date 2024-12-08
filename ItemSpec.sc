ItemSpec : ControlSpec {
	var <items;

	*new { arg items, default=0;
		^super.new(0, 1, \linear, 1.0 / items.size, default).items_(items).init
	}

	*newFrom { arg similar;
		^this.new(similar.items)
	}

	items_{
		|inItems|
		items = inItems;
		this.step = 1.0 / items.size;

	}

	map { arg value;
		// maps a value from [0..1] to spec range
		^items.clipAt(
			floor(warp.map(value.clip(0.0, 1.0)) * items.size)
		);
	}

	unmap { arg value;
		var index = items.indexOf(value);
		if (index.isNil) { ^nil } {
			^index * (1.0 / items.size);
		}
	}

	copy {
		^this.class.newFrom(this)
	}

	constrain { arg value;
		if (items.includes(value).not) {
			Error("Value must be one of the items %".format(items.cs)).throw
		}
		^value

	}
}

