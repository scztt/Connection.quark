+ControlSpec {
	warp_{
		|w|
		warp = w.copy;
		w.spec = this;
		this.changed(\warp);
	}
}
