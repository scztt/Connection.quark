+View {
	updateOnAction {
		|should=true|
		if (should) {
			ViewActionUpdater.enable(this);
		} {
			ViewActionUpdater.disable(this);
		}
	}
}
