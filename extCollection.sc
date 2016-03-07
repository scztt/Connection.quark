+Collection {
	connectAll {
		|dependant|
		^ConnectionList(this.collect(_.connectTo(dependant)))
	}

	connectMap {
		|dependant|
		var pairs = this.asPairs.clump(2).collect(_.reverse).flatten;
		^ConnectionMap(*pairs).connectTo(dependant);
	}
}