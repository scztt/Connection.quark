/*ItemSpec : ControlSpec {
	var <items;

	*new { arg items, default;
		^super.new(0, 1, \linear, 1.0 / items.size, default ?? {items[0]}).items_(items).init
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
			^(index * (1.0 / items.size));
		}
	}

	copy {
		^this.class.newFrom(this)
	}
}*/


ItemSpec {
	var <items, <warp, <>default, <spec;

	*new { |items, warp = 0, default|
		^super.newCopyArgs( items, warp, default ).init;
	}

	init {
		spec = [ -0.5, items.size - 0.5, warp, 1].asSpec;
		// default is in mapped range, not unmapped 0
		// if no default given, take first key
		if (items.includes(default).not) { default = items[0] };
	}

	setFrom {
		|other|
		items = other.items;
		warp = other.warp;
		default = other.default;
		spec = other.spec;
	}

	// from number to item - create equal parts of the range
	map { |inval|
		^items.clipAt( spec.map( inval ).asInteger );
	}

	minval { ^0 }
	maxval { ^(items.size - 1) }
	step { ^1 }
	units { ^"" }

	// from string to number
	unmap { |inval|
		var index = items.indexOfEqual( inval );
		if ( index.notNil ){
			^spec.unmap( index ).round(1/(items.size - 1));
		};
		^nil;
	}

	asSpec {
		^this;
	}

	constrain {
		|value|
		if (items.includes(value)) {
			^value
		} {
			^default ?? { this.map(0) }
		}
	}

	items_{ |newItems| items = newItems;  }
	warp_ { |argWarp| spec.warp_(argWarp) }

}
