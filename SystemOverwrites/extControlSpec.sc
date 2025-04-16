+ControlSpec {
	setFrom {
		|otherSpec|

		// Note that ownership with grid and warp are weird - they point back to
		// the owning spec, so these need to be copied and re-setup in this case.
		this.minval 	= otherSpec.minval;
		this.maxval 	= otherSpec.maxval;
		this.warp 		= otherSpec.warp.asWarp(this);
		this.step 		= otherSpec.step;
		this.default 	= otherSpec.default;
		this.units 		= otherSpec.units;

		if (otherSpec.instVarAt(\grid).notNil) {
			"found a grid: %".format(otherSpec).postln;
			this.grid 		= otherSpec.instVarAt(\grid).copy.spec_(this)
		}
	}

	warp_{
		|w|
		warp = w.copy;
		w.spec = this;
		this.changed(\warp);
	}
}